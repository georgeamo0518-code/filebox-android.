package dev.yourname.filebox.data.fs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.StatFs
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

    fun current(): List<StorageVolume> =
        // One app-specific dir per mounted volume; its root is the part before /Android/.
        context.getExternalFilesDirs(null).filterNotNull().mapNotNull { appDir ->
            val root = File(appDir.absolutePath.substringBefore("/Android/"))
            val v = sm.getStorageVolume(root) ?: return@mapNotNull null
            val stat = runCatching { StatFs(root.path) }.getOrNull() ?: return@mapNotNull null
            val desc = v.getDescription(context)
            val usb = v.isRemovable && desc.contains("usb", ignoreCase = true)
            StorageVolume(
                id = if (v.isPrimary) PRIMARY else (v.uuid ?: root.name),
                label = when {
                    v.isPrimary -> "Internal storage"
                    usb -> "USB drive"
                    else -> "SD card"
                },
                root = FileRef.Path(root.absolutePath),
                totalBytes = stat.totalBytes,
                freeBytes = stat.availableBytes,
                isRemovable = v.isRemovable,
                isUsb = usb,
                isWritable = root.canWrite(),
            )
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
