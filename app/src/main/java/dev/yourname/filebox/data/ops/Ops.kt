package dev.yourname.filebox.data.ops

import dev.yourname.filebox.data.fs.FileSource
import dev.yourname.filebox.data.fs.VolumeRepository
import dev.yourname.filebox.data.media.MediaStoreIndex
import dev.yourname.filebox.data.store.FavoritesRepository
import dev.yourname.filebox.data.store.TrashItem
import dev.yourname.filebox.data.store.TrashStore
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.Format
import dev.yourname.filebox.domain.Names
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

enum class OpType { Copy, Move, Delete, Trash, Restore }
enum class OpStatus { Queued, Running, Conflict, Done, Failed, Cancelled }
enum class ConflictChoice { Skip, Replace, KeepBoth }

data class ConflictInfo(val existing: FileEntry, val incoming: FileEntry, val remaining: Int)

data class FileOperation(
    val id: Long,
    val type: OpType,
    val destination: FileRef?,
    val status: OpStatus = OpStatus.Queued,
    val counting: Boolean = true,
    val itemsTotal: Int = 0,
    val itemsDone: Int = 0,
    val bytesTotal: Long = 0,
    val bytesDone: Long = 0,
    val bytesPerSec: Long = 0,
    val currentName: String? = null,
    val conflict: ConflictInfo? = null,
    val failed: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    /** Trash rows this operation created, so the snackbar can offer Undo. */
    val trashedIds: List<Long> = emptyList(),
) {
    val isFinished get() = status == OpStatus.Done || status == OpStatus.Failed || status == OpStatus.Cancelled
    val fraction: Float? get() = when {
        counting -> null
        bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
        itemsTotal > 0 -> itemsDone.toFloat() / itemsTotal
        else -> null
    }
}

/** Raised for an item that cannot be processed; the operation carries on with the next one. */
class ItemFailure(message: String) : IOException(message)

/**
 * The file operations queue (S09). One operation runs at a time, so two pastes never interleave.
 *
 * Interim: runs in an app-scoped coroutine. The architecture's WorkManager worker with a foreground
 * notification (survives leaving the app) and the persisted file_operations table are /replica-backend work;
 * the function signatures here are the ones that worker will keep.
 */
class Ops(
    private val fs: FileSource,
    private val volumes: VolumeRepository,
    private val index: MediaStoreIndex,
    private val trash: TrashStore,
    private val favorites: FavoritesRepository,
    private val scope: CoroutineScope,
) {
    private class Request(
        val id: Long,
        val type: OpType,
        val sources: List<FileRef>,
        val destination: FileRef?,
        val trashIds: List<Long>,
        var policy: ConflictChoice?,
    )

    private var nextId = 1L
    private val queue = Channel<Request>(Channel.UNLIMITED)
    private val _ops = MutableStateFlow<Map<Long, FileOperation>>(emptyMap())
    private val _finished = MutableSharedFlow<FileOperation>(extraBufferCapacity = 8)
    private val conflictAnswers = mutableMapOf<Long, CompletableDeferred<Pair<ConflictChoice, Boolean>>>()
    private val jobs = mutableMapOf<Long, Job>()

    /** Every finished operation, once, for snackbars and list refreshes. */
    val finished: SharedFlow<FileOperation> = _finished

    val all: StateFlow<Map<Long, FileOperation>> = _ops

    init {
        scope.launch {
            for (r in queue) {
                if (_ops.value[r.id]?.status == OpStatus.Cancelled) continue
                val job = scope.launch(Dispatchers.IO) { run(r) }
                jobs[r.id] = job
                job.join()
                jobs.remove(r.id)
            }
        }
    }

    fun enqueue(
        type: OpType,
        sources: List<FileRef>,
        destination: FileRef? = null,
        policy: ConflictChoice? = null,
        trashIds: List<Long> = emptyList(),
    ): Long {
        val id = nextId++
        _ops.update { it + (id to FileOperation(id, type, destination)) }
        queue.trySend(Request(id, type, sources, destination, trashIds, policy))
        return id
    }

    fun observe(id: Long): Flow<FileOperation?> = _ops.map { it[id] }

    /** The operation the progress dialog should show: running or waiting on a conflict. */
    fun active(): Flow<FileOperation?> = _ops.map { m -> m.values.firstOrNull { !it.isFinished && it.status != OpStatus.Queued } }

    fun resolveConflict(id: Long, choice: ConflictChoice, applyAll: Boolean) {
        conflictAnswers.remove(id)?.complete(choice to applyAll)
    }

    fun cancel(id: Long) {
        set(id) { if (it.isFinished) it else it.copy(status = OpStatus.Cancelled) }
        conflictAnswers.remove(id)?.cancel()
        jobs[id]?.cancel()
    }

    /** Can [dest] receive [sources]? Blocks pasting into the source's own folder, or into itself. */
    fun canPasteInto(dest: FileRef, sources: List<FileRef>): String? {
        val d = dest.location
        for (s in sources) {
            val loc = s.location
            if (d == loc || d.startsWith("$loc/")) return "Can't paste a folder into itself"
            if (fs.parent(s)?.location == d) return "Can't paste into the same folder"
        }
        return null
    }

    private fun set(id: Long, f: (FileOperation) -> FileOperation) = _ops.update { m ->
        m[id]?.let { m + (id to f(it)) } ?: m
    }

    // ---- the worker ----

    private var lastTick = 0L
    private var bytesAtTick = 0L

    private suspend fun run(r: Request) {
        val touched = mutableSetOf<String>()
        try {
            set(r.id) { it.copy(status = OpStatus.Running, counting = true) }
            val trashRows = if (r.type == OpType.Restore) trash.rows.value.filter { it.id in r.trashIds } else emptyList()
            val sources = if (r.type == OpType.Restore) trashRows.map { FileRef.Path(it.trashPath) } else r.sources

            // Count first so the dialog can show a real fraction.
            var bytes = 0L
            for (s in sources) {
                coroutineContext.ensureActive()
                bytes += fs.walk(s).sumOf { if (it.isDir) 0L else it.size }
            }
            set(r.id) { it.copy(counting = false, itemsTotal = sources.size, bytesTotal = bytes) }
            lastTick = System.currentTimeMillis()
            bytesAtTick = 0

            preflight(r, sources, bytes)

            sources.forEachIndexed { i, src ->
                coroutineContext.ensureActive()
                set(r.id) { it.copy(currentName = src.name) }
                try {
                    when (r.type) {
                        OpType.Copy, OpType.Move -> transfer(r, src, r.destination!!, sources.size - i - 1, touched)
                        OpType.Delete -> {
                            if (!fs.delete(src)) throw ItemFailure("Couldn't delete ${src.name}")
                            touched += src.location
                            favorites.remove(src.location)
                        }
                        OpType.Trash -> moveToTrash(r, src, touched)
                        OpType.Restore -> restore(r, trashRows[i], sources.size - i - 1, touched)
                    }
                } catch (e: ItemFailure) {
                    set(r.id) { it.copy(failed = it.failed + 1, error = it.error ?: e.message) }
                } catch (e: IOException) {
                    set(r.id) { it.copy(failed = it.failed + 1, error = it.error ?: (e.message ?: "Couldn't process ${src.name}")) }
                }
                set(r.id) { it.copy(itemsDone = it.itemsDone + 1) }
            }
            set(r.id) { op ->
                val allFailed = op.failed > 0 && op.failed == op.itemsTotal
                op.copy(status = if (allFailed) OpStatus.Failed else OpStatus.Done, currentName = null, conflict = null)
            }
        } catch (e: CancellationException) {
            set(r.id) { it.copy(status = OpStatus.Cancelled, conflict = null) }
        } catch (e: IOException) {
            set(r.id) { it.copy(status = OpStatus.Failed, error = e.message, conflict = null) }
        } finally {
            withContext(NonCancellable) {
                index.notify(touched)
                volumes.invalidate()
                _ops.value[r.id]?.let { _finished.emit(it) }
            }
        }
    }

    private fun preflight(r: Request, sources: List<FileRef>, bytes: Long) {
        if (r.type != OpType.Copy && r.type != OpType.Move) return
        val dest = r.destination ?: throw IOException("No destination")
        canPasteInto(dest, sources)?.let { throw IOException(it) }
        if (!fs.isWritable(dest)) throw IOException("This folder is read-only")
        val sameVolume = sources.all { volumes.volumeOf(it.location)?.id == volumes.volumeOf(dest.location)?.id }
        if (r.type == OpType.Copy || !sameVolume) {
            val free = fs.freeBytes(dest)
            if (free in 1 until bytes) throw IOException("Not enough space: needs ${Format.bytes(bytes)}, ${Format.bytes(free)} free")
        }
    }

    /** Resolves a name clash at [target]. Returns the final target, or null to skip this item. */
    private suspend fun resolve(r: Request, src: FileRef, target: FileRef, remaining: Int): FileRef? {
        if (!fs.exists(target)) return target
        val choice = r.policy ?: run {
            val existing = fs.stat(target)!!
            val incoming = fs.stat(src) ?: throw ItemFailure("${src.name} no longer exists")
            val answer = CompletableDeferred<Pair<ConflictChoice, Boolean>>()
            conflictAnswers[r.id] = answer
            set(r.id) { it.copy(status = OpStatus.Conflict, conflict = ConflictInfo(existing, incoming, remaining)) }
            val (c, all) = answer.await()
            set(r.id) { it.copy(status = OpStatus.Running, conflict = null) }
            if (all) r.policy = c
            c
        }
        val parent = fs.parent(target)!!
        return when (choice) {
            ConflictChoice.Skip -> {
                set(r.id) { it.copy(skipped = it.skipped + 1) }
                null
            }
            ConflictChoice.KeepBoth -> fs.child(parent, Names.nextFree(target.name, fs.stat(src)?.isDir == true) { fs.exists(fs.child(parent, it)) })
            ConflictChoice.Replace -> {
                if (target.location == src.location) return null
                if (!fs.delete(target)) throw ItemFailure("Couldn't replace ${target.name}")
                target
            }
        }
    }

    private suspend fun transfer(r: Request, src: FileRef, dest: FileRef, remaining: Int, touched: MutableSet<String>) {
        if (!fs.exists(src)) throw ItemFailure("${src.name} no longer exists")
        val target = resolve(r, src, fs.child(dest, src.name), remaining) ?: return
        if (r.type == OpType.Move && fs.moveFast(src, target)) {
            val size = fs.walk(target).sumOf { if (it.isDir) 0L else it.size }
            progress(r.id, size)
            favorites.relocate(src.location, target.location)
        } else {
            copyTree(r.id, src, target, touched)
            if (r.type == OpType.Move) {
                // Cross-volume move: the copy is verified complete before the source goes.
                if (!fs.delete(src)) throw ItemFailure("Copied ${src.name}, but couldn't remove the original")
                favorites.relocate(src.location, target.location)
            }
        }
        touched += src.location
        touched += target.location
    }

    private suspend fun copyTree(id: Long, src: FileRef, target: FileRef, touched: MutableSet<String>) {
        val entry = fs.stat(src) ?: throw ItemFailure("${src.name} no longer exists")
        if (entry.isDir) {
            if (!fs.mkdirs(target)) throw ItemFailure("Couldn't create ${target.name}")
            for (child in fs.list(src, showHidden = true)) {
                coroutineContext.ensureActive()
                copyTree(id, child.ref, fs.child(target, child.name), touched)
            }
            return
        }
        // Write to a dot-prefixed .partial file and rename at the end, so a cancelled copy never
        // leaves a half-written file under the real name.
        val parent = fs.parent(target)!!
        val partial = fs.child(parent, ".${target.name}.partial")
        try {
            fs.openInput(src).use { input ->
                fs.openOutput(partial).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        progress(id, n.toLong())
                    }
                    (output as? java.io.FileOutputStream)?.fd?.sync()
                }
            }
            if (!fs.moveFast(partial, target)) throw ItemFailure("Couldn't finish ${target.name}")
            File(target.location).setLastModified(entry.modifiedAt)
            touched += target.location
        } catch (e: Throwable) {
            withContext(NonCancellable) { fs.delete(partial) }
            if (e is IOException && e.message?.contains("ENOSPC") == true) throw IOException("Not enough space")
            throw e
        }
    }

    private fun progress(id: Long, delta: Long) {
        val now = System.currentTimeMillis()
        set(id) { op ->
            val done = op.bytesDone + delta
            if (now - lastTick >= 500) {
                val speed = (done - bytesAtTick) * 1000 / (now - lastTick)
                lastTick = now
                bytesAtTick = done
                op.copy(bytesDone = done, bytesPerSec = speed)
            } else op.copy(bytesDone = done)
        }
    }

    private suspend fun moveToTrash(r: Request, src: FileRef, touched: MutableSet<String>) {
        val entry = fs.stat(src) ?: throw ItemFailure("${src.name} no longer exists")
        val volume = volumes.volumeOf(src.location) ?: throw ItemFailure("Unknown volume for ${src.name}")
        val bin = fs.child(volume.root, TRASH_DIR)
        if (!fs.mkdirs(bin)) throw ItemFailure("Couldn't create the recycle bin")
        File(bin.location, ".nomedia").createNewFile()
        val id = trash.newId()
        val target = fs.child(bin, "${id}_${src.name}")
        val size = fs.walk(src).sumOf { if (it.isDir) 0L else it.size }
        // The bin lives on the same volume, so this is a rename: instant even for big folders.
        if (!fs.moveFast(src, target)) throw ItemFailure("Couldn't move ${src.name} to the recycle bin")
        trash.add(TrashItem(id, src.location, target.location, volume.id, src.name, entry.isDir, size, System.currentTimeMillis()))
        favorites.remove(src.location)
        progress(r.id, size)
        touched += src.location
        set(r.id) { it.copy(trashedIds = it.trashedIds + id) }
    }

    private suspend fun restore(r: Request, item: TrashItem, remaining: Int, touched: MutableSet<String>) {
        val src = FileRef.Path(item.trashPath)
        if (!fs.exists(src)) {
            trash.remove(setOf(item.id))
            throw ItemFailure("${item.name} is no longer in the recycle bin")
        }
        val original = FileRef.Path(item.originalPath)
        // The original folder may be gone: recreate it.
        fs.parent(original)?.let { if (!fs.mkdirs(it)) throw ItemFailure("Couldn't recreate ${it.location}") }
        val target = resolve(r, src, original, remaining) ?: return
        if (!fs.moveFast(src, target)) throw ItemFailure("Couldn't restore ${item.name}")
        trash.remove(setOf(item.id))
        progress(r.id, item.sizeBytes)
        touched += target.location
    }

    companion object {
        const val TRASH_DIR = ".filebox_trash"
    }
}
