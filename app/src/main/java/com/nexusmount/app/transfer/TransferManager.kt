package com.nexusmount.app.transfer

import com.nexusmount.app.data.DriveRepository
import com.nexusmount.app.data.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class TransferManager(private val repo: DriveRepository) {

    suspend fun copyFile(source: File, dest: File, onProgress: (Int) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = UUID.randomUUID().toString()
                val item = TransferItem(
                    id = id,
                    name = source.name,
                    from = source.absolutePath,
                    to = dest.absolutePath,
                    progress = 0,
                    status = "running"
                )
                val list = repo.getTransfers().toMutableList()
                list.add(0, item)
                repo.saveTransfers(list)

                dest.parentFile?.mkdirs()
                val total = source.length().coerceAtLeast(1L)
                var copied = 0L
                FileInputStream(source).use { input ->
                    FileOutputStream(dest).use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            copied += n
                            val p = ((copied * 100) / total).toInt()
                            onProgress(p)
                            updateProgress(id, p, "running")
                        }
                    }
                }
                updateProgress(id, 100, "completed")
                dest
            }
        }

    suspend fun moveFile(source: File, dest: File, onProgress: (Int) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            copyFile(source, dest, onProgress).mapCatching {
                if (!source.delete()) throw Exception("No se pudo borrar origen")
                it
            }
        }

    private fun updateProgress(id: String, progress: Int, status: String) {
        val list = repo.getTransfers().map {
            if (it.id == id) it.copy(progress = progress, status = status) else it
        }
        repo.saveTransfers(list)
    }
}
