package dev.yourname.filebox.domain

import kotlinx.serialization.Serializable

/**
 * Where a file lives. Screens only ever see a FileRef, never a java.io.File, so a second backend
 * (SAF tree URIs for removable media on Android 8-10) can be added without touching the UI.
 * Only the path backend exists today: with all-files access, Android 11+ exposes SD/USB volumes as paths.
 */
sealed interface FileRef {
    val location: String
    val name: String

    @Serializable
    data class Path(val path: String) : FileRef {
        override val location get() = path
        override val name get() = path.trimEnd('/').substringAfterLast('/')
    }

    companion object {
        fun of(location: String): FileRef = Path(location)
    }
}

data class FileEntry(
    val ref: FileRef,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String?,
    val childCount: Int? = null,
) {
    val location get() = ref.location
    val isHidden get() = name.startsWith(".")
    val kind: FileKind get() = if (isDir) FileKind.Folder else FileKind.of(name, mimeType)
    val extension get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
}

/** Visual type of a file: picks the glyph, tint and thumbnail strategy. */
enum class FileKind {
    Folder, Image, Video, Audio, Doc, Apk, Archive, Other;

    companion object {
        val docExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "md", "rtf", "csv", "epub", "json", "xml", "html", "log")
        val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
        private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg")
        private val videoExt = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v")
        private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr")

        fun of(name: String, mime: String?): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext == "apk" -> Apk
                ext in archiveExtensions -> Archive
                mime?.startsWith("image/") == true || ext in imageExt -> Image
                mime?.startsWith("video/") == true || ext in videoExt -> Video
                mime?.startsWith("audio/") == true || ext in audioExt -> Audio
                ext in docExtensions -> Doc
                else -> Other
            }
        }
    }
}

/** The Home screen's category tiles (S01 -> S04). */
@Serializable
enum class FileCategory { Images, Video, Audio, Docs, Apks, Downloads, Archives }

enum class SortField { Name, Date, Size, Type }
enum class SortDir { Asc, Desc }
enum class ViewMode { List, Grid }

data class SortSpec(val field: SortField = SortField.Name, val dir: SortDir = SortDir.Asc) {

    /** Folders first, then by the chosen field; name is always the tie-breaker. */
    fun apply(list: List<FileEntry>, foldersFirst: Boolean = true): List<FileEntry> {
        val byName = Comparator<FileEntry> { a, b -> NaturalOrder.compare(a.name, b.name) }
        val primary: Comparator<FileEntry> = when (field) {
            SortField.Name -> byName
            SortField.Date -> compareBy<FileEntry> { it.modifiedAt }.then(byName)
            SortField.Size -> compareBy<FileEntry> { if (it.isDir) (it.childCount ?: 0).toLong() else it.size }.then(byName)
            SortField.Type -> compareBy<FileEntry> { it.extension }.then(byName)
        }
        val directed = if (dir == SortDir.Asc) primary else primary.reversed()
        return if (foldersFirst) list.sortedWith(compareBy<FileEntry> { !it.isDir }.then(directed)) else list.sortedWith(directed)
    }
}

/** "file2" sorts before "file10", case-insensitive. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

data class StorageVolume(
    val id: String,
    val label: String,
    val root: FileRef,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean,
    val isUsb: Boolean,
    val isWritable: Boolean,
) {
    val usedBytes get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
    val isNearFull get() = usedFraction >= 0.9f
}
