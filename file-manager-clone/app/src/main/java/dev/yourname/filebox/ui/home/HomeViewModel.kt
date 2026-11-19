package dev.yourname.filebox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yourname.filebox.AppContainer
import dev.yourname.filebox.data.prefs.Settings
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.StorageVolume
import dev.yourname.filebox.ui.theme.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), Settings()
    )

    val volumes: StateFlow<List<StorageVolume>> = container.volumes.volumes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.volumes.current())

    val counts: StateFlow<Map<FileCategory, Int>> = container.index.categoryCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recent: StateFlow<List<FileEntry>> = container.index.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }

    fun openVolume(volume: StorageVolume) = volume.root
}

class BrowserViewModel(
    private val container: AppContainer,
    private val initial: FileRef,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries

    private val _path = MutableStateFlow(initial)
    val path: StateFlow<FileRef> = _path

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { refresh(initial) }

    val settings = container.settings.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), Settings()
    )

    fun refresh(ref: FileRef) {
        _path.value = ref
        _error.value = null
        viewModelScope.launch {
            _entries.value = runCatching { container.fs.list(ref, settings.value.showHidden) }
                .onFailure { _error.value = it.message ?: "Couldn't list this folder" }
                .getOrDefault(emptyList())
        }
    }

    fun navigateTo(child: FileEntry) {
        if (child.isDir) refresh(child.ref)
    }

    fun goUp() {
        container.fs.parent(_path.value)?.let { refresh(it) }
    }

    fun crumbs(): List<String> {
        val p = (_path.value as? dev.yourname.filebox.domain.FileRef.Path)?.path ?: return emptyList()
        val parts = p.trimEnd('/').split('/').filter { it.isNotEmpty() }
        return parts
    }
}