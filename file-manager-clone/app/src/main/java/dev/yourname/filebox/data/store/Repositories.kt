package dev.yourname.filebox.data.store

import dev.yourname.filebox.data.media.SearchFilters
import dev.yourname.filebox.domain.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File

// ---- favorites (S13, F10) ----

@Serializable
data class Favorite(
    val id: Long,
    val location: String,
    val label: String,
    val isDir: Boolean,
    val createdAt: Long,
)

data class FavoriteRow(val favorite: Favorite, val missing: Boolean)

class FavoritesRepository(dir: File) {
    private val table = JsonTable(File(dir, "favorites.json"), Favorite.serializer())

    /** Rows whose target no longer exists are flagged, not dropped: the user decides. */
    fun list(): Flow<List<FavoriteRow>> = table.rows.map { rows ->
        rows.sortedBy { it.createdAt }.map { FavoriteRow(it, !File(it.location).exists()) }
    }.flowOn(Dispatchers.IO)

    fun isFavorite(location: String) = table.rows.value.any { it.location == location }

    /** Upsert on location: favouriting twice is a no-op. */
    suspend fun add(location: String, isDir: Boolean) = table.update { rows ->
        if (rows.any { it.location == location }) rows
        else rows + Favorite(nextId(rows.map { it.id }), location, File(location).name, isDir, System.currentTimeMillis())
    }

    suspend fun remove(location: String) = table.update { rows -> rows.filterNot { it.location == location } }

    /** Keep bookmarks pointing at a file the app renamed or moved. */
    suspend fun relocate(from: String, to: String) = table.update { rows ->
        rows.map { f ->
            when {
                f.location == from -> f.copy(location = to, label = File(to).name)
                f.location.startsWith("$from/") -> f.copy(location = to + f.location.removePrefix(from))
                else -> f
            }
        }
    }
}

// ---- hidden items (S14, F11) ----

@Serializable
data class HiddenItem(
    val id: Long,
    val originalPath: String,
    val hiddenPath: String,
    val isDir: Boolean,
    val hiddenAt: Long,
)

class HiddenStore(dir: File) {
    private val table = JsonTable(File(dir, "hidden_items.json"), HiddenItem.serializer())
    val rows get() = table.rows

    suspend fun add(original: String, hidden: String, isDir: Boolean) = table.update { rows ->
        rows.filterNot { it.hiddenPath == hidden } +
            HiddenItem(nextId(rows.map { it.id }), original, hidden, isDir, System.currentTimeMillis())
    }

    suspend fun remove(id: Long) = table.update { rows -> rows.filterNot { it.id == id } }
}

// ---- recycle bin (S15, F06) ----

@Serializable
data class TrashItem(
    val id: Long,
    val originalPath: String,
    val trashPath: String,
    val volumeId: String,
    val name: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val deletedAt: Long,
)

class TrashStore(dir: File) {
    private val table = JsonTable(File(dir, "trash_items.json"), TrashItem.serializer())
    val rows get() = table.rows

    fun newId() = nextId(table.rows.value.map { it.id })

    suspend fun add(item: TrashItem) = table.update { rows -> rows.filterNot { it.trashPath == item.trashPath } + item }
    suspend fun remove(ids: Set<Long>) = table.update { rows -> rows.filterNot { it.id in ids } }
}

// ---- recent searches (S06, could) ----

@Serializable
data class SearchQuery(
    val text: String,
    val kind: FileCategory? = null,
    val minSize: Long? = null,
    val searchedAt: Long,
)

class SearchRepository(dir: File) {
    private val table = JsonTable(File(dir, "search_queries.json"), SearchQuery.serializer())

    fun recent(): Flow<List<SearchQuery>> = table.rows.map { rows -> rows.sortedByDescending { it.searchedAt } }

    /** Upsert on (text, filters), keep the last 20. */
    suspend fun remember(text: String, filters: SearchFilters) {
        if (text.isBlank()) return
        table.update { rows ->
            val q = SearchQuery(text.trim(), filters.kind, filters.minSize, System.currentTimeMillis())
            (rows.filterNot { it.text == q.text && it.kind == q.kind && it.minSize == q.minSize } + q)
                .sortedByDescending { it.searchedAt }
                .take(20)
        }
    }

    suspend fun clear() = table.update { emptyList() }
}

private fun nextId(ids: List<Long>) = (ids.maxOrNull() ?: 0) + 1
