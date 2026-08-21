package com.nexusmount.app.cleanup

import android.content.Context
import android.os.Environment
import java.io.File

data class CleanupItem(
    val id: String,
    val title: String,
    val detail: String,
    val bytes: Long,
    val safe: Boolean,
    val files: List<File>
)

object SmartCleanup {

    fun scan(context: Context): List<CleanupItem> {
        val items = mutableListOf<CleanupItem>()

        // 1) Caché de la app (siempre seguro)
        val cacheDirs = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir
        )
        val cacheFiles = mutableListOf<File>()
        var cacheBytes = 0L
        cacheDirs.forEach { dir ->
            walkFiles(dir).forEach {
                cacheFiles.add(it)
                cacheBytes += it.length()
            }
        }
        items.add(
            CleanupItem(
                "app_cache",
                "Caché de NexusMount",
                "smb_open, interconnect, temporales de la app",
                cacheBytes,
                safe = true,
                cacheFiles
            )
        )

        // 2) Archivos temporales conocidos
        val tmpNames = listOf(".tmp", ".temp", ".crdownload", ".partial", ".download")
        val tmpFiles = mutableListOf<File>()
        var tmpBytes = 0L
        val scanRoots = listOfNotNull(
            context.getExternalFilesDir(null),
            context.filesDir,
            safeExternal()
        )
        scanRoots.forEach { root ->
            walkFiles(root, maxDepth = 4).forEach { f ->
                val n = f.name.lowercase()
                if (tmpNames.any { n.endsWith(it) } || n.startsWith("._")) {
                    tmpFiles.add(f)
                    tmpBytes += f.length()
                }
            }
        }
        items.add(
            CleanupItem(
                "temp",
                "Archivos temporales",
                "${tmpFiles.size} archivos .tmp / .partial / etc.",
                tmpBytes,
                safe = true,
                tmpFiles
            )
        )

        // 3) Vacíos
        val emptyDirs = mutableListOf<File>()
        scanRoots.forEach { root ->
            walkDirs(root, maxDepth = 4).forEach { d ->
                if (d.isDirectory && (d.list()?.isEmpty() == true) && d != root) {
                    emptyDirs.add(d)
                }
            }
        }
        items.add(
            CleanupItem(
                "empty",
                "Carpetas vacías",
                "${emptyDirs.size} carpetas",
                0L,
                safe = true,
                emptyDirs
            )
        )

        // 4) Descargas antiguas de la app (>30 días)
        val dl = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val old = mutableListOf<File>()
        var oldBytes = 0L
        val limit = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        if (dl != null && dl.isDirectory) {
            walkFiles(dl, maxDepth = 3).forEach { f ->
                if (f.lastModified() < limit) {
                    old.add(f)
                    oldBytes += f.length()
                }
            }
        }
        items.add(
            CleanupItem(
                "old_downloads",
                "Descargas antiguas (app)",
                "${old.size} archivos > 30 días en carpeta de la app",
                oldBytes,
                safe = true,
                old
            )
        )

        // 5) Archivos grandes en almacenamiento accesible (>100 MB) — solo informe, no auto
        val big = mutableListOf<File>()
        var bigBytes = 0L
        val threshold = 100L * 1024 * 1024
        safeExternal()?.let { root ->
            walkFiles(root, maxDepth = 3).forEach { f ->
                if (f.length() >= threshold) {
                    big.add(f)
                    bigBytes += f.length()
                }
            }
        }
        items.add(
            CleanupItem(
                "large",
                "Archivos grandes (>100 MB)",
                "${big.size} archivos — revisión manual recomendada",
                bigBytes,
                safe = false,
                big.take(50)
            )
        )

        return items
    }

    fun clean(item: CleanupItem): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        item.files.forEach { f ->
            val len = if (f.isFile) f.length() else 0L
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (ok) {
                count++
                bytes += len
            }
        }
        return count to bytes
    }

    fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${"%.1f".format(b / (1024.0 * 1024 * 1024))} GB"
    }

    private fun safeExternal(): File? {
        val f = File("/storage/emulated/0")
        return if (f.exists() && f.canRead()) f else null
    }

    private fun walkFiles(dir: File, maxDepth: Int = 6): List<File> {
        val out = mutableListOf<File>()
        fun rec(d: File, depth: Int) {
            if (depth > maxDepth) return
            val kids = d.listFiles() ?: return
            for (k in kids) {
                if (k.isFile) out.add(k)
                else if (k.isDirectory) rec(k, depth + 1)
            }
        }
        if (dir.isDirectory) rec(dir, 0)
        return out
    }

    private fun walkDirs(dir: File, maxDepth: Int = 6): List<File> {
        val out = mutableListOf<File>()
        fun rec(d: File, depth: Int) {
            if (depth > maxDepth) return
            val kids = d.listFiles() ?: return
            for (k in kids) {
                if (k.isDirectory) {
                    out.add(k)
                    rec(k, depth + 1)
                }
            }
        }
        if (dir.isDirectory) rec(dir, 0)
        return out
    }
}
