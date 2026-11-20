package dev.yourname.filebox.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yourname.filebox.domain.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(entry: FileEntry, onBack: () -> Unit) {
    var content by remember(entry.location) { mutableStateOf("") }
    var loading by remember(entry.location) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entry.location) {
        content = withContext(Dispatchers.IO) { File(entry.location).readText(Charsets.UTF_8) }
        loading = false
    }
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(enabled = !loading && !saving, onClick = {
                        saving = true
                        scope.launch {
                            message = runCatching {
                                withContext(Dispatchers.IO) {
                                    File(entry.location).writeText(content, Charsets.UTF_8)
                                }
                                "Arquivo salvo"
                            }.getOrElse { "Erro ao salvar: ${it.message}" }
                            saving = false
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (saving) "Salvando" else "Salvar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("Digite seu texto...") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
