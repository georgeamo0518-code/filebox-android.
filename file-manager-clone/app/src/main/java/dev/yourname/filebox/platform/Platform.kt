package dev.yourname.filebox.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.yourname.filebox.MainActivity
import dev.yourname.filebox.R
import dev.yourname.filebox.data.fs.PathFileSource
import dev.yourname.filebox.domain.FileEntry
import java.io.File

/** F01: all-files access on Android 11+, legacy read/write on 8-10. */
object PermissionManager {
    enum class State { Granted, Denied }

    val usesAllFilesAccess get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val legacyPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun state(context: Context): State {
        val ok = if (usesAllFilesAccess) Environment.isExternalStorageManager()
        else legacyPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        return if (ok) State.Granted else State.Denied
    }

    /** Settings screen with this app's "Allow access to manage all files" switch. */
    fun allFilesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun allFilesFallbackIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

/** Everything that hands a file to another app (F02, F09, F14). */
object Intents {
    private fun uri(context: Context, path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

    private fun mime(e: FileEntry) = e.mimeType ?: PathFileSource.mimeOf(e.name) ?: "*/*"

    /** Returns false when no installed app can open this type. */
    fun open(context: Context, e: FileEntry): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, e.location), mime(e))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun share(context: Context, files: List<FileEntry>) {
        val uris = files.map { uri(context, it.location) }
        val types = files.map { mime(it) }.distinct()
        val type = when {
            types.size == 1 -> types[0]
            types.map { it.substringBefore('/') }.distinct().size == 1 -> types[0].substringBefore('/') + "/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        // ClipData carries the read grant to the receiving app for every URI.
        send.clipData = ClipData.newUri(context.contentResolver, files[0].name, uris[0]).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun canInstallPackages(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, path: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, path), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Could-have: pin a folder to the launcher. Opens S03 at that path via filebox://open?path=. */
    fun pinFolderShortcut(context: Context, e: FileEntry): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.Builder().scheme("filebox").authority("open").appendQueryParameter("path", e.location).build())
        val info = ShortcutInfoCompat.Builder(context, "folder:${e.location}")
            .setShortLabel(e.name.ifEmpty { "Folder" })
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}

/** S20: what an APK file is, and how it compares to what's installed. */
data class ApkInfo(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val icon: Drawable?,
    val installedVersionCode: Long?,
) {
    enum class Status { NotInstalled, Installed, Newer, Older }

    val status get() = when {
        installedVersionCode == null -> Status.NotInstalled
        versionCode > installedVersionCode -> Status.Newer
        versionCode < installedVersionCode -> Status.Older
        else -> Status.Installed
    }

    companion object {
        fun read(context: Context, path: String): ApkInfo? {
            val pm = context.packageManager
            val info: PackageInfo = pm.getPackageArchiveInfo(path, 0) ?: return null
            val app = info.applicationInfo ?: return null
            // Without these, label and icon resolve against nothing.
            app.sourceDir = path
            app.publicSourceDir = path
            val installed = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
            return ApkInfo(
                label = app.loadLabel(pm).toString(),
                packageName = info.packageName,
                versionName = info.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                installedVersionCode = installed?.let { PackageInfoCompat.getLongVersionCode(it) },
            )
        }
    }
}
