package dev.yourname.filebox.data.fs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.StatFs
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

/** Mounted volumes with live free/total space. Re-emits on mount, eject and after file operations. */
class VolumeRepository(private val context: Context) {

    private val sm = context.getSystemService(StorageManager::class.java)
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun invalidate() { refresh.tryEmit(Unit) }

    fun current(): List<StorageVolume> {
        // Android can return null from getStorageVolume(root) on some Samsung builds.
        // Always expose the primary shared-storage root directly, then add mounted volumes.
        val primaryRoot = Environment.getExternalStorageDirectory().canonicalFile
        val candidates = linkedMapOf<String, Pair<File, android.os.storage.StorageVolume?>>()
        candidates[primaryRoot.path] = primaryRoot to runCatching { sm.getStorageVolume(primaryRoot) }.getOrNull()

        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            val rootPath = appDir.absolutePath.substringBefore("/Android/")
            val root = runCatching { File(rootPath).canonicalFile }.getOrNull() ?: return@forEach
            val volume = runCatching { sm.getStorageVolume(appDir) ?: sm.getStorageVolume(root) }.getOrNull()
            candidates.putIfAbsent(root.path, root to volume)
        }

        return candidates.values.mapNotNull { (root, volume) ->
            val stat = runCatching { StatFs(root.path) }.getOrNull() ?: return@mapNotNull null
            val isPrimary = root.path == primaryRoot.path || volume?.isPrimary == true
            val removable = volume?.isRemovable ?: !isPrimary
            val description = runCatching { volume?.getDescription(context).orEmpty() }.getOrDefault("")
            val usb = removable && description.contains("usb", ignoreCase = true)
            StorageVolume(
                id = if (isPrimary) PRIMARY else (volume?.uuid ?: root.name),
                label = when {
                    isPrimary -> "Internal storage"
                    usb -> "USB drive"
                    else -> "SD card"
                },
                root = FileRef.Path(root.absolutePath),
                totalBytes = stat.totalBytes,
                freeBytes = stat.availableBytes,
                isRemovable = removable,
                isUsb = usb,
                isWritable = root.canWrite(),
            )
        }.sortedBy { if (it.id == PRIMARY) 0 else 1 }
    }

    fun primary(): StorageVolume? = current().firstOrNull { it.id == PRIMARY }

    fun volumeOf(location: String): StorageVolume? =
        current().filter { location == it.root.location || location.startsWith(it.root.location + "/") }
            .maxByOrNull { it.root.location.length }

    fun volumes(): Flow<List<StorageVolume>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(current()) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val job = launch { refresh.collect { trySend(current()) } }
        trySend(current())
        awaitClose {
            job.cancel()
            context.unregisterReceiver(receiver)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val PRIMARY = "primary"
    }
}
