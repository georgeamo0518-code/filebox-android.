package dev.yourname.filebox

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import dev.yourname.filebox.data.ops.ZipTools
import dev.yourname.filebox.ui.home.TextEditorScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yourname.filebox.domain.FileRef
import dev.yourname.filebox.domain.FileCategory
import dev.yourname.filebox.domain.FileEntry
import dev.yourname.filebox.platform.Intents
import dev.yourname.filebox.platform.PermissionManager
import dev.yourname.filebox.ui.home.BrowserScreen
import dev.yourname.filebox.ui.home.HomeScreen
import dev.yourname.filebox.ui.theme.FileboxTheme

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer
    private var hasStorageAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as FileboxApp).container
        hasStorageAccess = PermissionManager.state(this) == PermissionManager.State.Granted

        setContent {
            FileboxTheme {
                var current by remember { mutableStateOf<FileRef?>(null) }
                var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }
                var searchQuery by remember { mutableStateOf<String?>(null) }
                var editing by remember { mutableStateOf<FileEntry?>(null) }
                var extracting by remember { mutableStateOf<FileEntry?>(null) }
                var extractPath by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val openFile: (FileEntry) -> Unit = { entry ->
                    when {
                        entry.extension in setOf("txt", "md", "log", "csv", "json", "xml", "html", "rtf", "ini", "properties", "yaml", "yml", "sql", "sh", "bat", "gradle", "kt", "java", "js", "css") -> editing = entry
                        entry.extension == "zip" -> {
                            extracting = entry
                            extractPath = File(entry.location).parent ?: Environment.getExternalStorageDirectory().absolutePath
                        }
                        !Intents.open(this@MainActivity, entry) -> {
                            Toast.makeText(this@MainActivity, "Nenhum aplicativo encontrado para abrir este arquivo", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (editing != null) {
                        TextEditorScreen(entry = editing!!, onBack = { editing = null })
                    } else if (!hasStorageAccess) {
                        StoragePermissionScreen(onGrant = ::openStorageSettings)
                    } else if (current != null) {
                        BrowserScreen(
                            container = container,
                            initial = current!!,
                            onBack = { current = null; selectedCategory = null; searchQuery = null },
                            onOpenFile = openFile,
                            category = selectedCategory,
                            searchQuery = searchQuery,
                            onCompressFolder = { path ->
                                scope.launch {
                                    val result = ZipTools.compress(File(path))
                                    Toast.makeText(this@MainActivity,
                                        result.fold({ "ZIP criado: ${it.name}" }, { it.message ?: "Erro ao compactar" }),
                                        Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    } else {
                        HomeScreen(
                            container = container,
                            onOpenFolder = { current = it },
                            onOpenCategory = {
                                selectedCategory = it
                                searchQuery = null
                                current = container.volumes.primary()?.root
                                    ?: FileRef.Path(Environment.getExternalStorageDirectory().absolutePath)
                            },
                            onSearch = {
                                searchQuery = it
                                selectedCategory = null
                                current = container.volumes.primary()?.root
                                    ?: FileRef.Path(Environment.getExternalStorageDirectory().absolutePath)
                            },
                            onOpenFile = openFile,
                        )
                    }
                }
                extracting?.let { selected ->
                    AlertDialog(
                        onDismissRequest = { extracting = null },
                        title = { Text("Extrair ZIP") },
                        text = {
                            OutlinedTextField(
                                value = extractPath,
                                onValueChange = { extractPath = it },
                                label = { Text("Pasta de destino") },
                                singleLine = true,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val zip = File(selected.location)
                                val target = File(extractPath)
                                extracting = null
                                scope.launch {
                                    val result = ZipTools.extract(zip, target)
                                    Toast.makeText(this@MainActivity,
                                        result.fold({ "Extraídos ${it} arquivos" }, { it.message ?: "Erro ao extrair" }),
                                        Toast.LENGTH_LONG).show()
                                }
                            }) { Text("Extrair") }
                        },
                        dismissButton = {
                            TextButton(onClick = { extracting = null }) { Text("Cancelar") }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) {
            hasStorageAccess = PermissionManager.state(this) == PermissionManager.State.Granted
        }
    }

    private fun openStorageSettings() {
        val intent = if (PermissionManager.usesAllFilesAccess) {
            runCatching { PermissionManager.allFilesIntent(this) }
                .getOrElse { PermissionManager.allFilesFallbackIntent() }
        } else {
            IntentCompat.legacyStorageSettingsIntent(this)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (PermissionManager.usesAllFilesAccess) {
                runCatching { startActivity(PermissionManager.allFilesFallbackIntent()) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StoragePermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Allow file access", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "To browse folders and manage files, Filebox needs permission to access your device storage. " +
                "On the next screen, enable ‘Allow access to manage all files’ and return to the app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Grant access") }
    }
}

private object IntentCompat {
    fun legacyStorageSettingsIntent(context: android.content.Context) =
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
}
