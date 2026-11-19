package dev.yourname.filebox.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yourname.filebox.AppContainer
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileRef
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("FILEBOX", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Meus arquivos", style = MaterialTheme.typography.headlineSmall)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    IconButton(onClick = { /* Search screen will be added without changing file access. */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Pesquisar arquivos")
                    }
                }
            }
        }
        item {
            StorageCard(
                volumes = volumes,
                onClick = { volumes.firstOrNull { it.id == "primary" }?.root?.let(onOpenFolder) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Categorias", style = MaterialTheme.typography.titleMedium)
                    Text("Acesso rápido", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val cats = FileCategory.entries
                cats.chunked(4).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        group.forEach { cat ->
                            CategoryTile(
                                cat = cat,
                                count = counts[cat] ?: 0,
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Acessados recentemente", style = MaterialTheme.typography.titleMedium)
                    Text("${recent.size} itens", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(recent) { entry ->
                FileRow(entry = entry, onClick = { /* Open action is handled in the browser screen. */ })
            }
        } else {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Seus arquivos recentes", style = MaterialTheme.typography.titleSmall)
                            Text("Os arquivos usados recentemente aparecerão aqui.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
            EmptyState("Esta pasta está vazia", modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
    )
}
