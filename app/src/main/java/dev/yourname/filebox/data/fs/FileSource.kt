package dev.yourname.filebox.data.fs

import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/** Folder listing failed for a reason the UI shows as its own state on S03. */
sealed class ListError(message: String) : Exception(message) {
    class Inaccessible(path: String) : ListError("Android doesn't let apps open $path")
    class Missing(path: String) : ListError("$path no longer exists")
}

data class SizeProgress(val bytes: Long, val files: Int, val folders: Int, val done: Boolean)

/** One storage backend. Everything above data/fs talks to this, never to java.io directly. */
interface FileSource {
    suspend fun list(dir: FileRef, showHidden: Boolean): List<FileEntry>
    suspend fun stat(ref: FileRef): FileEntry?
    fun exists(ref: FileRef): Boolean
    fun child(dir: FileRef, name: String): FileRef
    fun parent(ref: FileRef): FileRef?
    fun folderSize(ref: FileRef): Flow<SizeProgress>
    fun freeBytes(dir: FileRef): Long
    fun isWritable(dir: FileRef): Boolean

    suspend fun createFolder(parent: FileRef, name: String): Result<FileRef>
    suspend fun createFile(parent: FileRef, name: String): Result<FileRef>
    suspend fun rename(ref: FileRef, newName: String): Result<FileRef>

    /** Same-volume move: one atomic rename. Returns false when the volumes differ and a copy is needed. */
    fun moveFast(from: FileRef, to: FileRef): Boolean
    fun delete(ref: FileRef): Boolean
    fun mkdirs(ref: FileRef): Boolean
    fun openInput(ref: FileRef): InputStream
    fun openOutput(ref: FileRef): OutputStream
    fun walk(ref: FileRef): Sequence<FileEntry>
}
