package dev.yourname.filebox.data.ops

import dev.yourname.filebox.data.fs.FileSource
import dev.yourname.filebox.data.media.MediaStoreIndex
import dev.yourname.filebox.data.store.FavoritesRepository
import dev.yourname.filebox.data.store.HiddenItem
import dev.yourname.filebox.data.store.HiddenStore
import dev.yourname.filebox.data.store.TrashItem
import dev.yourname.filebox.data.store.TrashStore
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.Names
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class NameException(val problem: Names.Problem) : Exception(problem.name)

/** Quick, synchronous-feeling actions: create, rename, hide (S10, S14). Long ones go through [Ops]. */
class FileActions(
    private val fs: FileSource,
    private val index: MediaStoreIndex,
    private val favorites: FavoritesRepository,
    private val hidden: HiddenStore,
) {
    fun validate(parent: FileRef, name: String, current: String? = null): Names.Problem? {
        Names.check(name)?.let { return it }
        if (name != current && !name.equals(current, ignoreCase = true) && fs.exists(fs.child(parent, name))) return Names.Problem.Exists
        return null
    }

    suspend fun rename(ref: FileRef, newName: String): Result<FileRef> {
        val parent = fs.parent(ref) ?: return Result.failure(IllegalStateException("No parent"))
        validate(parent, newName, ref.name)?.let { return Result.failure(NameException(it)) }
        return fs.rename(ref, newName).onSuccess {
            favorites.relocate(ref.location, it.location)
            index.notify(listOf(ref.location, it.location))
        }
    }

    suspend fun createFolder(parent: FileRef, name: String): Result<FileRef> {
        validate(parent, name)?.let { return Result.failure(NameException(it)) }
        return fs.createFolder(parent, name)
    }

    suspend fun createTextFile(parent: FileRef, name: String): Result<FileRef> {
        validate(parent, name)?.let { return Result.failure(NameException(it)) }
        return fs.createFile(parent, name).onSuccess { index.notify(listOf(it.location)) }
    }

    /** Hiding is a dot-prefix rename, recorded so S14 can list and undo it. */
    suspend fun hide(entries: List<FileEntry>): Int {
        var n = 0
        for (e in entries) {
            if (e.isHidden) continue
            val parent = fs.parent(e.ref) ?: continue
            val wanted = ".${e.name}"
            val hiddenName = if (fs.exists(fs.child(parent, wanted))) Names.nextFree(wanted, e.isDir) { fs.exists(fs.child(parent, it)) } else wanted
            fs.rename(e.ref, hiddenName).onSuccess {
                hidden.add(e.location, it.location, e.isDir)
                favorites.relocate(e.location, it.location)
                index.notify(listOf(e.location, it.location))
                n++
            }
        }
        return n
    }

    suspend fun unhide(item: HiddenItem): Result<FileRef> {
        val src = FileRef.Path(item.hiddenPath)
        if (!fs.exists(src)) {
            hidden.remove(item.id)
            return Result.failure(IllegalStateException("It was moved or deleted outside the app"))
        }
        val parent = fs.parent(src)!!
        val wanted = File(item.originalPath).name
        val name = if (fs.exists(fs.child(parent, wanted))) Names.nextFree(wanted, item.isDir) { fs.exists(fs.child(parent, it)) } else wanted
        return fs.rename(src, name).onSuccess {
            hidden.remove(item.id)
            favorites.relocate(src.location, it.location)
            index.notify(listOf(src.location, it.location))
        }
    }

    /** S14 rows; entries whose file vanished outside the app are flagged. */
    fun hiddenItems(): Flow<List<Pair<HiddenItem, Boolean>>> =
        hidden.rows.map { rows -> rows.sortedByDescending { it.hiddenAt }.map { it to !File(it.hiddenPath).exists() } }
}

/** Recycle bin (S15). Moving in and out runs through [Ops] so it gets progress and conflicts. */
class TrashRepository(private val store: TrashStore, private val ops: Ops, private val fs: FileSource) {
    val items: Flow<List<TrashItem>> = store.rows

    fun restore(ids: List<Long>) = ops.enqueue(OpType.Restore, emptyList(), trashIds = ids)

    suspend fun purge(ids: Set<Long>) {
        store.rows.value.filter { it.id in ids }.forEach { fs.delete(FileRef.Path(it.trashPath)) }
        store.remove(ids)
    }

    suspend fun empty() = purge(store.rows.value.map { it.id }.toSet())

    /** At app start: drop items past retention and rows whose file vanished. (Architecture: a daily worker.) */
    suspend fun purgeExpired(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        val rows = store.rows.value
        val expired = rows.filter { it.deletedAt < cutoff }.map { it.id }.toSet()
        purge(expired)
        val missing = rows.filter { it.id !in expired && !File(it.trashPath).exists() }.map { it.id }.toSet()
        if (missing.isNotEmpty()) store.remove(missing)
    }
}
