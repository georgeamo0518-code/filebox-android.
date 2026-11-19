package dev.yourname.filebox.data.fs

import android.os.StatFs
import android.webkit.MimeTypeMap
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/** java.io backend for primary storage, and for SD/USB volumes when the OS exposes them as paths. */
class PathFileSource : FileSource {

    private fun File.ref() = FileRef.Path(absolutePath)
    private val FileRef.file get() = File((this as FileRef.Path).path)

    fun toEntry(f: File, countChildren: Boolean = true): FileEntry {
        val dir = f.isDirectory
        return FileEntry(
            ref = f.ref(),
            name = f.name,
            isDir = dir,
            size = if (dir) 0 else f.length(),
            modifiedAt = f.lastModified(),
            mimeType = if (dir) null else mimeOf(f.name),
            childCount = if (dir && countChildren) f.list()?.size else null,
        )
    }

    override suspend fun list(dir: FileRef, showHidden: Boolean): List<FileEntry> = withContext(Dispatchers.IO) {
        val f = dir.file
        if (!f.exists()) throw ListError.Missing(f.path)
        val children = f.listFiles() ?: throw ListError.Inaccessible(f.path)
        children.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { toEntry(it) }
            .toList()
    }

    override suspend fun stat(ref: FileRef): FileEntry? = withContext(Dispatchers.IO) {
        ref.file.takeIf { it.exists() }?.let { toEntry(it) }
    }

    override fun exists(ref: FileRef) = ref.file.exists()
    override fun child(dir: FileRef, name: String): FileRef = File(dir.file, name).ref()
    override fun parent(ref: FileRef): FileRef? = ref.file.parentFile?.ref()

    override fun folderSize(ref: FileRef): Flow<SizeProgress> = flow {
        var bytes = 0L
        var files = 0
        var folders = 0
        var lastEmit = 0L
        val stack = ArrayDeque<File>().apply { add(ref.file) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (c in dir.listFiles().orEmpty()) {
                // Never follow symlinks: they can loop.
                if (Files.isSymbolicLink(c.toPath())) continue
                if (c.isDirectory) { folders++; stack.add(c) } else { files++; bytes += c.length() }
            }
            val now = System.currentTimeMillis()
            if (now - lastEmit > 150) { emit(SizeProgress(bytes, files, folders, false)); lastEmit = now }
        }
        emit(SizeProgress(bytes, files, folders, true))
    }.flowOn(Dispatchers.IO)

    override fun freeBytes(dir: FileRef): Long = runCatching { StatFs(dir.file.path).availableBytes }.getOrDefault(0)

    override fun isWritable(dir: FileRef) = dir.file.canWrite()

    override suspend fun createFolder(parent: FileRef, name: String) = withContext(Dispatchers.IO) {
        val f = File(parent.file, name)
        when {
            f.exists() -> Result.failure(FileAlreadyExistsException(f))
            f.mkdir() -> Result.success(f.ref())
            else -> Result.failure(IOException("Couldn't create the folder here"))
        }
    }

    override suspend fun createFile(parent: FileRef, name: String) = withContext(Dispatchers.IO) {
        val f = File(parent.file, name)
        runCatching {
            if (!f.createNewFile()) throw FileAlreadyExistsException(f)
            f.ref()
        }
    }

    override suspend fun rename(ref: FileRef, newName: String): Result<FileRef> = withContext(Dispatchers.IO) {
        val src = ref.file
        val dst = File(src.parentFile, newName)
        val caseOnly = src.name != newName && src.name.equals(newName, ignoreCase = true)
        when {
            !caseOnly && dst.exists() -> Result.failure(FileAlreadyExistsException(dst))
            caseOnly -> {
                // FAT/exFAT are case-insensitive: go through a temporary name.
                val tmp = File(src.parentFile, ".rename-${System.nanoTime()}")
                if (src.renameTo(tmp) && tmp.renameTo(dst)) Result.success(dst.ref())
                else Result.failure(IOException("Couldn't rename"))
            }
            src.renameTo(dst) -> Result.success(dst.ref())
            else -> Result.failure(IOException("Couldn't rename"))
        }
    }

    override fun moveFast(from: FileRef, to: FileRef): Boolean {
        val src = from.file
        val dst = to.file
        if (volumeRootOf(src) != volumeRootOf(dst)) return false
        return src.renameTo(dst)
    }

    override fun delete(ref: FileRef): Boolean {
        val f = ref.file
        if (!f.exists()) return true
        if (f.isDirectory && !Files.isSymbolicLink(f.toPath())) {
            f.listFiles()?.forEach { delete(it.ref()) }
        }
        return f.delete()
    }

    override fun mkdirs(ref: FileRef) = ref.file.let { it.isDirectory || it.mkdirs() }
    override fun openInput(ref: FileRef): InputStream = FileInputStream(ref.file)
    override fun openOutput(ref: FileRef): OutputStream = FileOutputStream(ref.file)

    override fun walk(ref: FileRef): Sequence<FileEntry> =
        ref.file.walkTopDown()
            .onEnter { it == ref.file || !Files.isSymbolicLink(it.toPath()) }
            .map { toEntry(it, countChildren = false) }

    /** "/storage/emulated/0/x" -> "/storage/emulated/0", "/storage/1A2B-3C4D/x" -> "/storage/1A2B-3C4D". */
    fun volumeRootOf(f: File): String {
        val p = f.absolutePath
        Regex("""^(/storage/emulated/\d+|/storage/[^/]+|/mnt/media_rw/[^/]+)""").find(p)?.let { return it.value }
        return "/"
    }

    companion object {
        fun mimeOf(name: String): String? {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return null
            if (ext == "apk") return "application/vnd.android.package-archive"
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }
}
