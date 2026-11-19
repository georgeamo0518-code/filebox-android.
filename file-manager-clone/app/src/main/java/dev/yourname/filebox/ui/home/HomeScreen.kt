package dev.yourname.filebox.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yourname.filebox.AppContainer
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.StorageVolume
import dev.yourname.filebox.ui.components.*
import dev.yourname.filebox.ui.theme.FileboxTheme

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenFolder: (FileRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = remember { HomeViewModel(container) }
    val volumes by vm.volumes.collectAsState()
    val counts by vm.counts.collectAsState()
    val recent by vm.recent.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StorageCard(
                volumes = volumes,
                onClick = { volumes.firstOrNull { it.id == "primary" }?.root?.let(onOpenFolder) },
            )
        }
        items(FileCategory.entries) { cat ->
            CategoryTile(
                cat = cat,
                count = counts[cat] ?: 0,
                onClick = { /* category browsing is a future screen */ },
            )
        }
        if (recent.isNotEmpty()) {
            item {
                Text("Recent", style = MaterialTheme.typography.titleMedium)
            }
            items(recent) { entry ->
                FileRow(entry = entry, onClick = { /* open file */ })
            }
        }
    }
}

@Composable
fun BrowserScreen(
    container: AppContainer,
    initial: FileRef,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = remember { BrowserViewModel(container, initial) }
    val entries by vm.entries.collectAsState()
    val error by vm.error.collectAsState()
    val crumbs = vm.crumbs()

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(crumbs = crumbs, onBack = onBack, onCrumbClick = { i -> vm.refresh(vm.path.value) })
        if (error != null) {
            Text(error!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        } else if (entries.isEmpty()) {
            EmptyState("Empty folder", modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    FileRow(entry = entry, onClick = { vm.navigateTo(entry) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(crumbs: List<String>, onBack: () -> Unit, onCrumbClick: (Int) -> Unit) {
    TopAppBar(
        title = { BreadcrumbBar(breadcrumbs = crumbs, onCrumbClick = onCrumbClick) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}