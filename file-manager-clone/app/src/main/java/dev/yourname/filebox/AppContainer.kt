package dev.yourname.filebox

import android.content.Context
import dev.yourname.filebox.data.fs.PathFileSource
import dev.yourname.filebox.data.fs.VolumeRepository
import dev.yourname.filebox.data.media.MediaStoreIndex
import dev.yourname.filebox.data.ops.FileActions
import dev.yourname.filebox.data.ops.Ops
import dev.yourname.filebox.data.ops.TrashRepository
import dev.yourname.filebox.data.prefs.SettingsRepository
import dev.yourname.filebox.data.store.FavoritesRepository
import dev.yourname.filebox.data.store.HiddenStore
import dev.yourname.filebox.data.store.SearchRepository
import dev.yourname.filebox.data.store.TrashStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies. Small enough that Hilt would add more code than it saves for now. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())
    val fs = PathFileSource()
    val volumes = VolumeRepository(context)
    val index = MediaStoreIndex(context) { volumes.primary()?.root?.location }
    val settings = SettingsRepository(context)
    val favorites = FavoritesRepository(context.filesDir)
    val hiddenStore = HiddenStore(context.filesDir)
    val trashStore = TrashStore(context.filesDir)
    val searches = SearchRepository(context.filesDir)
    val ops = Ops(fs, volumes, index, trashStore, favorites, appScope)
    val actions = FileActions(fs, index, favorites, hiddenStore)
    val trash = TrashRepository(trashStore, ops, fs)
}
