package dev.yourname.filebox.data.ops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipTools {
    suspend fun extract(zipFile: File, destination: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(zipFile.isFile && zipFile.extension.equals("zip", true)) { "Selecione um arquivo ZIP válido" }
            if (!destination.exists() && !destination.mkdirs()) error("Não foi possível criar a pasta de destino")
            val root = destination.canonicalFile
            var count = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { input ->
                while (true) {
                    val item = input.nextEntry ?: break
                    val output = File(root, item.name).canonicalFile
                    require(output.path.startsWith(root.path + File.separator) || output == root) {
                        "O ZIP contém um caminho inválido"
                    }
                    if (output.exists() && !item.isDirectory) error("O arquivo ${item.name} já existe no destino")
                    if (item.isDirectory) {
                        if (!output.exists() && !output.mkdirs()) error("Falha ao criar ${item.name}")
                    } else {
                        output.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Falha ao criar pasta") }
                        FileOutputStream(output).use { stream -> input.copyTo(stream) }
                        count++
                    }
                    input.closeEntry()
                }
            }
            count
        }
    }

    suspend fun compress(folder: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(folder.isDirectory) { "Selecione uma pasta" }
            val parent = folder.parentFile ?: error("Pasta sem diretório superior")
            val output = File(parent, "${folder.name.ifBlank { "Arquivos" }}.zip")
            if (output.exists()) error("Já existe ${output.name}")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                folder.walkTopDown().filter { it != folder }.forEach { file ->
                    val relative = folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                    val name = if (file.isDirectory) "$relative/" else relative
                    zip.putNextEntry(ZipEntry(name))
                    if (file.isFile) FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            output
        }
    }
}
