package dev.yourname.filebox.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.yourname.filebox.domain.SortDir
import dev.yourname.filebox.domain.SortField
import dev.yourname.filebox.domain.SortSpec
import dev.yourname.filebox.domain.ViewMode
import dev.yourname.filebox.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val showHidden: Boolean = false,
    val viewMode: ViewMode = ViewMode.List,
    val sort: SortSpec = SortSpec(),
    val confirmDelete: Boolean = true,
    val useTrash: Boolean = true,
    val showThumbnails: Boolean = true,
    val binRetentionDays: Int = 30,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val showHidden = booleanPreferencesKey("show_hidden")
        val viewMode = stringPreferencesKey("view_mode")
        val sortField = stringPreferencesKey("sort_field")
        val sortDir = stringPreferencesKey("sort_dir")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val useTrash = booleanPreferencesKey("use_trash")
        val showThumbnails = booleanPreferencesKey("show_thumbnails")
        val binRetentionDays = intPreferencesKey("bin_retention_days")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            theme = p.enum(Keys.theme, d.theme),
            showHidden = p[Keys.showHidden] ?: d.showHidden,
            viewMode = p.enum(Keys.viewMode, d.viewMode),
            sort = SortSpec(p.enum(Keys.sortField, d.sort.field), p.enum(Keys.sortDir, d.sort.dir)),
            confirmDelete = p[Keys.confirmDelete] ?: d.confirmDelete,
            useTrash = p[Keys.useTrash] ?: d.useTrash,
            showThumbnails = p[Keys.showThumbnails] ?: d.showThumbnails,
            binRetentionDays = p[Keys.binRetentionDays] ?: d.binRetentionDays,
        )
    }

    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setShowHidden(v: Boolean) = context.dataStore.edit { it[Keys.showHidden] = v }
    suspend fun setViewMode(v: ViewMode) = context.dataStore.edit { it[Keys.viewMode] = v.name }
    suspend fun setSort(field: SortField, dir: SortDir) = context.dataStore.edit {
        it[Keys.sortField] = field.name
        it[Keys.sortDir] = dir.name
    }
    suspend fun setConfirmDelete(v: Boolean) = context.dataStore.edit { it[Keys.confirmDelete] = v }
    suspend fun setUseTrash(v: Boolean) = context.dataStore.edit { it[Keys.useTrash] = v }
    suspend fun setShowThumbnails(v: Boolean) = context.dataStore.edit { it[Keys.showThumbnails] = v }
    suspend fun setBinRetentionDays(v: Int) = context.dataStore.edit { it[Keys.binRetentionDays] = v }
}
