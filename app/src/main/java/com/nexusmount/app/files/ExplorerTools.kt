package com.nexusmount.app.files

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileClipboard {
    enum class Mode { COPY, CUT }
    var mode: Mode = Mode.COPY
    var paths: MutableList<String> = mutableListOf()
    fun clear() { paths.clear() }
    fun hasItems() = paths.isNotEmpty()
}

object ExplorerFavorites {
    private const val PREFS = "nexus_explorer"
    fun list(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("favorites", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
    fun toggle(ctx: Context, path: String): Boolean {
        val set = list(ctx).toMutableList()
        return if (path in set) {
            set.remove(path); save(ctx, set); false
        } else {
            set.add(path); save(ctx, set); true
        }
    }
    private fun save(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("favorites", arr.toString()).apply()
    }
}

object ExplorerUtils {
    fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${"%.1f".format(b / (1024.0 * 1024 * 1024))} GB"
    }
    fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    fun mime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "txt", "log", "md", "json", "xml" -> "text/plain"
                else -> "application/octet-stream"
            }
    }
    fun properties(f: File): String = buildString {
        appendLine("Nombre: ${f.name}")
        appendLine("Ruta: ${f.absolutePath}")
        appendLine("Tipo: ${if (f.isDirectory) "Carpeta" else "Archivo"}")
        if (f.isFile) appendLine("Tamaño: ${formatSize(f.length())}")
        if (f.isDirectory) appendLine("Elementos: ${f.listFiles()?.size ?: 0}")
        appendLine("Modificado: ${formatDate(f.lastModified())}")
        appendLine("Legible: ${f.canRead()} · Escribible: ${f.canWrite()}")
        appendLine("Oculto: ${f.name.startsWith(".")}")
    }
    fun copyFile(src: File, destDir: File): Boolean {
        return try {
            if (src.isDirectory) {
                val target = File(destDir, src.name)
                target.mkdirs()
                src.listFiles()?.forEach { copyFile(it, target) }
                true
            } else {
                destDir.mkdirs()
                val target = File(destDir, src.name)
                FileInputStream(src).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                true
            }
        } catch (_: Exception) { false }
    }
    fun moveFile(src: File, destDir: File): Boolean {
        val ok = copyFile(src, destDir)
        if (ok) src.deleteRecursively()
        return ok
    }
    fun share(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Compartir ${file.name}"))
        } catch (_: Exception) {}
    }
    fun open(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Abrir ${file.name}"))
        } catch (_: Exception) {}
    }
    fun mediaCategory(root: File, kind: String): List<File> {
        val exts = when (kind) {
            "images" -> setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            "music" -> setOf("mp3", "wav", "flac", "m4a", "aac", "ogg")
            "video" -> setOf("mp4", "mkv", "avi", "webm", "3gp", "mov")
            "docs" -> setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md")
            "apk" -> setOf("apk")
            "zip" -> setOf("zip", "rar", "7z", "tar", "gz")
            else -> emptySet()
        }
        val found = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            if (depth > 4 || found.size > 200) return
            val kids = d.listFiles() ?: return
            for (k in kids) {
                if (k.isFile) {
                    if (k.extension.lowercase() in exts) found.add(k)
                } else if (k.isDirectory && !k.name.startsWith(".")) walk(k, depth + 1)
            }
        }
        walk(root, 0)
        return found.sortedByDescending { it.lastModified() }
    }
}
