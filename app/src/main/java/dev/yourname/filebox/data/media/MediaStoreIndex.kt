package dev.yourname.filebox.data.media

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileKind
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.SortDir
import dev.yourname.filebox.domain.SortField
import dev.yourname.filebox.domain.SortSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

data class SearchFilters(
    val kind: FileCategory? = null,
    val minSize: Long? = null,
    val modifiedAfter: Long? = null,
    val inFolder: String? = null,
) {
    val isEmpty get() = kind == null && minSize == null && modifiedAfter == null
}

/**
 * Categories, recent files and name search, from the system's own index (MediaStore.Files).
 * No crawler of our own: queries are instant, at the cost of lagging files the system hasn't scanned.
 */
class MediaStoreIndex(private val context: Context, private val primaryRoot: () -> String?) {

    private val resolver: ContentResolver get() = context.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    private val projection = arrayOf(
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.MIME_TYPE,
    )

    private class Where(val sql: String, val args: List<String>)

    private fun likeAny(column: String, patterns: Collection<String>) = Where(
        patterns.joinToString(" OR ", "(", ")") { "$column LIKE ?" },
        patterns.toList(),
    )

    // Hidden files, dot-folders (our trash, .thumbnails) and directories are never listed.
    private val baseWhere = Where(
        "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ?",
        listOf("%/.%"),
    )

    private fun whereFor(cat: FileCategory): Where {
        val mt = MediaStore.Files.FileColumns.MEDIA_TYPE
        val name = MediaStore.Files.FileColumns.DISPLAY_NAME
        return when (cat) {
            FileCategory.Images -> Where("$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", emptyList())
            FileCategory.Video -> Where("$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}", emptyList())
            FileCategory.Audio -> Where("$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}", emptyList())
            FileCategory.Docs -> likeAny(name, FileKind.docExtensions.map { "%.$it" })
            FileCategory.Apks -> Where("$name LIKE ?", listOf("%.apk"))
            FileCategory.Archives -> likeAny(name, FileKind.archiveExtensions.map { "%.$it" })
            FileCategory.Downloads -> Where(
                "${MediaStore.Files.FileColumns.DATA} LIKE ?",
                listOf("${primaryRoot() ?: "/storage/emulated/0"}/Download/%"),
            )
        }
    }

    private fun and(vararg parts: Where?) = parts.filterNotNull().let { w ->
        Where(w.joinToString(" AND ") { "(${it.sql})" }, w.flatMap { it.args })
    }

    private fun sortColumn(spec: SortSpec): String {
        val col = when (spec.field) {
            SortField.Name -> MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE"
            SortField.Date -> MediaStore.Files.FileColumns.DATE_MODIFIED
            SortField.Size -> MediaStore.Files.FileColumns.SIZE
            SortField.Type -> MediaStore.Files.FileColumns.MIME_TYPE
        }
        return col + if (spec.dir == SortDir.Asc) " ASC" else " DESC"
    }

    private suspend fun query(where: Where, order: String, limit: Int): List<FileEntry> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, where.sql)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, where.args.toTypedArray())
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<FileEntry>()
        resolver.query(filesUri, projection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0) ?: continue
                val name = c.getString(1) ?: File(path).name
                out += FileEntry(
                    ref = FileRef.Path(path),
                    name = name,
                    isDir = false,
                    size = c.getLong(2),
                    modifiedAt = c.getLong(3) * 1000,
                    mimeType = c.getString(4),
                )
            }
        }
        out
    }

    private suspend fun count(where: Where): Int = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, where.sql)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, where.args.toTypedArray())
        }
        resolver.query(filesUri, arrayOf(MediaStore.Files.FileColumns._ID), args, null)?.use { it.count } ?: 0
    }

    /** Fires once now and again whenever the index changes. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(filesUri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.onStart { emit(Unit) }.conflate()

    fun categoryCounts(): Flow<Map<FileCategory, Int>> = changes().map {
        FileCategory.entries.associateWith { count(and(baseWhere, whereFor(it))) }
    }.flowOn(Dispatchers.IO)

    /** Up to [limit] files of one type. Paging is a TODO: 2,000 rows covers most phones' categories. */
    fun category(cat: FileCategory, sort: SortSpec, limit: Int = 2000): Flow<List<FileEntry>> = changes().map {
        query(and(baseWhere, whereFor(cat)), sortColumn(sort), limit)
    }

    fun recent(days: Int = 30, limit: Int = 500): Flow<List<FileEntry>> = changes().map {
        val since = System.currentTimeMillis() / 1000 - days * 86_400L
        query(
            and(baseWhere, Where("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?", listOf(since.toString()))),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            limit,
        )
    }

    suspend fun search(text: String, filters: SearchFilters, limit: Int = 500): List<FileEntry> {
        val escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val parts = mutableListOf(
            baseWhere,
            Where("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'", listOf("%$escaped%")),
        )
        filters.kind?.let { parts += whereFor(it) }
        filters.minSize?.let { parts += Where("${MediaStore.Files.FileColumns.SIZE} >= ?", listOf(it.toString())) }
        filters.modifiedAfter?.let { parts += Where("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?", listOf((it / 1000).toString())) }
        filters.inFolder?.let { parts += Where("${MediaStore.Files.FileColumns.DATA} LIKE ?", listOf("$it/%")) }
        return query(and(*parts.toTypedArray()), "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC", limit)
    }

    /** Tell the index about changed paths so galleries and other apps see them. Deleted paths drop out. */
    fun notify(paths: Collection<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context, paths.take(1000).toTypedArray(), null, null)
    }
}
