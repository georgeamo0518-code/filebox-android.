package dev.yourname.filebox.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.Format
import dev.yourname.filebox.domain.StorageVolume
import dev.yourname.filebox.ui.theme.FileboxTheme

@Composable
fun StorageCard(
    volumes: List<StorageVolume>,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val total = volumes.sumOf { it.totalBytes }
    val used = total - volumes.sumOf { it.freeBytes }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (total > 0) used.toFloat() / total else 0f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${Format.bytes(used)} used", style = MaterialTheme.typography.bodyMedium)
                Text("${Format.bytes(total)} total", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun CategoryTile(
    cat: FileCategory,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeCategory = when (cat) {
        FileCategory.Images -> dev.yourname.filebox.ui.theme.Category.Images
        FileCategory.Video -> dev.yourname.filebox.ui.theme.Category.Video
        FileCategory.Audio -> dev.yourname.filebox.ui.theme.Category.Audio
        FileCategory.Docs -> dev.yourname.filebox.ui.theme.Category.Docs
        FileCategory.Apks -> dev.yourname.filebox.ui.theme.Category.Apk
        FileCategory.Downloads -> dev.yourname.filebox.ui.theme.Category.Downloads
        FileCategory.Archives -> dev.yourname.filebox.ui.theme.Category.Archives
    }
    val colors = FileboxTheme.extended.category[themeCategory]
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = colors?.container ?: MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(cat.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(8.dp))
            Text("${Format.bytes(entry.size)} • ${Format.shortDate(entry.modifiedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<String>,
    onCrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        breadcrumbs.forEachIndexed { i, crumb ->
            TextButton(onClick = { onCrumbClick(i) }) {
                Text(crumb, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (i < breadcrumbs.size - 1) { Text("/", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}