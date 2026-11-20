package dev.yourname.filebox.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.domain.FileKind
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
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Filled.Storage, contentDescription = null,
                            modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column {
                        Text("Armazenamento interno", style = MaterialTheme.typography.titleSmall)
                        Text("Visão geral do espaço", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Abrir armazenamento",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.bytes(used), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text("de ${Format.bytes(total)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (fraction >= .9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(fraction * 100).toInt()}% utilizado", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${Format.bytes((total - used).coerceAtLeast(0))} livres",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val (icon, label, category) = when (cat) {
        FileCategory.Images -> Triple(Icons.Filled.Image, "Imagens", dev.yourname.filebox.ui.theme.Category.Images)
        FileCategory.Video -> Triple(Icons.Filled.Videocam, "Vídeos", dev.yourname.filebox.ui.theme.Category.Video)
        FileCategory.Audio -> Triple(Icons.Filled.MusicNote, "Áudios", dev.yourname.filebox.ui.theme.Category.Audio)
        FileCategory.Docs -> Triple(Icons.Filled.Description, "Documentos", dev.yourname.filebox.ui.theme.Category.Docs)
        FileCategory.Apks -> Triple(Icons.Filled.Android, "APKs", dev.yourname.filebox.ui.theme.Category.Apk)
        FileCategory.Downloads -> Triple(Icons.Filled.Download, "Downloads", dev.yourname.filebox.ui.theme.Category.Downloads)
        FileCategory.Archives -> Triple(Icons.Filled.FolderZip, "Arquivos", dev.yourname.filebox.ui.theme.Category.Archives)
    }
    val colors = FileboxTheme.extended.category[category]
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 104.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = colors?.container ?: MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = colors?.icon ?: MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(count.toString(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, bg) = when (entry.kind) {
        FileKind.Folder -> Triple(Icons.Filled.Folder, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        FileKind.Image -> Triple(Icons.Filled.Image, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        FileKind.Video -> Triple(Icons.Filled.PlayCircle, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        FileKind.Audio -> Triple(Icons.Filled.MusicNote, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
        FileKind.Doc -> Triple(Icons.Filled.Description, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        FileKind.Apk -> Triple(Icons.Filled.Android, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        FileKind.Archive -> Triple(Icons.Filled.FolderZip, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
        FileKind.Other -> Triple(Icons.Filled.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainer)
    }
    val context = LocalContext.current
    var thumbnailFailed by remember(entry.location) { mutableStateOf(false) }
    val thumbnailRequest = remember(entry.location, entry.kind) {
        ImageRequest.Builder(context)
            .data(Uri.fromFile(File(entry.location)))
            .apply {
                if (entry.kind == FileKind.Video) decoderFactory(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!entry.isDir && entry.kind in setOf(FileKind.Image, FileKind.Video) && !thumbnailFailed) {
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = entry.name,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { thumbnailFailed = true },
                )
            } else {
                Surface(shape = RoundedCornerShape(11.dp), color = bg) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp), tint = tint)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis)
                Text(if (entry.isDir) "Pasta" else "${Format.bytes(entry.size)} • ${Format.shortDate(entry.modifiedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Mais opções",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = { onCrumbClick(i) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(crumb, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (i < breadcrumbs.size - 1) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null,
                modifier = Modifier.padding(20.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
