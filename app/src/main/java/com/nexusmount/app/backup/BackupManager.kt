package com.nexusmount.app.backup

import android.content.Context
import com.nexusmount.app.zip.ZipUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnapshotInfo(
    val name: String,
    val path: String,
    val size: Long,
    val created: Long
)

class BackupManager(private val context: Context) {

    private val backupRoot: File
        get() = File(context.getExternalFilesDir(null), "backups").also { it.mkdirs() }

    fun runBackup(sources: List<File>, label: String = "auto"): Result<SnapshotInfo> {
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "backup_${label}_$stamp.zip"
            val dest = File(backupRoot, name)
            val existing = sources.filter { it.exists() }
            if (existing.isEmpty()) error("No hay archivos para respaldar")
            ZipUtils.zip(existing, dest).getOrThrow()
            SnapshotInfo(name, dest.absolutePath, dest.length(), System.currentTimeMillis())
        }
    }

    fun listSnapshots(): List<SnapshotInfo> {
        return backupRoot.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", true) }
            ?.map {
                SnapshotInfo(it.name, it.absolutePath, it.length(), it.lastModified())
            }
            ?.sortedByDescending { it.created }
            ?: emptyList()
    }

    fun restoreSnapshot(snapshot: File, destDir: File): Result<File> {
        return ZipUtils.unzip(snapshot, destDir)
    }
}
