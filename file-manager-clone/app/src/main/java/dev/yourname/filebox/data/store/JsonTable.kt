package dev.yourname.filebox.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Interim stand-in for a Room table: rows kept in memory, written through to a JSON file in the
 * app's private storage. Same row shapes as replica/schema.sql, so the repositories above it keep
 * their signatures when /replica-backend swaps in Room.
 */
class JsonTable<T>(private val file: File, serializer: KSerializer<T>) {
    private val listSerializer = ListSerializer(serializer)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _rows = MutableStateFlow(load())
    val rows: StateFlow<List<T>> = _rows

    private fun load(): List<T> = runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrDefault(emptyList())

    suspend fun update(transform: (List<T>) -> List<T>) = mutex.withLock {
        val next = transform(_rows.value)
        _rows.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(listSerializer, next))
            tmp.renameTo(file)
        }
    }
}
