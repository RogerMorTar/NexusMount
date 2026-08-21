package com.nexusmount.app.share

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.net.URLDecoder
import java.util.Locale

/**
 * HTTP solo lectura: expone un directorio en LAN/Tailscale.
 * Otros NexusMount pueden listar y copiar; no modificar ni borrar.
 */
class ReadOnlyShareServer(
    private val context: Context,
    port: Int = DEFAULT_PORT,
    root: File? = null
) : NanoHTTPD(port) {

    val rootDir: File = root ?: defaultRoot(context)

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.GET && session.method != Method.HEAD) {
                return newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED,
                    MIME_PLAINTEXT,
                    "Solo lectura: metodo no permitido"
                )
            }
            val uri = session.uri.substringBefore('?')
            when {
                uri == "/api/list" || uri.startsWith("/api/list") -> {
                    val pathParam = session.parameters["path"]?.firstOrNull() ?: ""
                    listJson(pathParam)
                }
                uri.startsWith("/api/info") -> infoJson()
                uri.startsWith("/file/") -> {
                    val rel = URLDecoder.decode(uri.removePrefix("/file/"), "UTF-8")
                    serveFile(rel)
                }
                else -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    htmlIndex()
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    private fun resolveSafe(rel: String): File? {
        val cleaned = rel.trim().trimStart('/').replace("..", "")
        val target = if (cleaned.isEmpty()) rootDir else File(rootDir, cleaned)
        val canonical = try { target.canonicalFile } catch (_: Exception) { return null }
        val rootCanonical = rootDir.canonicalFile
        if (!canonical.path.startsWith(rootCanonical.path)) return null
        return canonical
    }

    private fun listJson(rel: String): Response {
        val dir = resolveSafe(rel) ?: return newFixedLengthResponse(
            Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Ruta no permitida"
        )
        if (!dir.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No existe")
        }
        if (!dir.isDirectory) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No es carpeta")
        }
        val arr = JSONArray()
        dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?.forEach { f ->
                arr.put(
                    JSONObject()
                        .put("name", f.name)
                        .put("dir", f.isDirectory)
                        .put("size", if (f.isFile) f.length() else 0)
                        .put(
                            "path",
                            if (rel.isEmpty()) f.name else rel.trim('/') + "/" + f.name
                        )
                )
            }
        val body = JSONObject()
            .put("root", rootDir.absolutePath)
            .put("path", rel)
            .put("entries", arr)
            .toString()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
    }

    private fun infoJson(): Response {
        val body = JSONObject()
            .put("app", "NexusMount")
            .put("mode", "read-only")
            .put("root", rootDir.absolutePath)
            .put("port", listeningPort)
            .put("ips", JSONArray(localIpv4()))
            .toString()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
    }

    private fun serveFile(rel: String): Response {
        val file = resolveSafe(rel) ?: return newFixedLengthResponse(
            Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Ruta no permitida"
        )
        if (!file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No es archivo")
        }
        val fis = FileInputStream(file)
        return newFixedLengthResponse(Response.Status.OK, mime(file.name), fis, file.length())
    }

    private fun htmlIndex(): String {
        val ips = localIpv4().joinToString(", ")
        return """
            <html><body style="font-family:sans-serif;background:#0b1326;color:#dae2fd;padding:24px">
            <h2>NexusMount · solo lectura</h2>
            <p>Raiz: ${rootDir.absolutePath}</p>
            <p>IPs: $ips · puerto $DEFAULT_PORT</p>
            <p>API: /api/list?path= · /file/... · /api/info</p>
            </body></html>
        """.trimIndent()
    }

    private fun mime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "txt", "log", "md", "json" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val DEFAULT_PORT = 8765

        fun defaultRoot(context: Context): File {
            val emulated = File("/storage/emulated/0")
            if (emulated.exists() && emulated.canRead()) return emulated
            return context.getExternalFilesDir(null) ?: context.filesDir
        }

        fun localIpv4(): List<String> {
            val out = mutableListOf<String>()
            try {
                val en = NetworkInterface.getNetworkInterfaces() ?: return out
                while (en.hasMoreElements()) {
                    val iface = en.nextElement()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val a = addrs.nextElement()
                        val h = a.hostAddress ?: continue
                        if (h.contains(":")) continue
                        if (h.startsWith("127.")) continue
                        out.add(h)
                    }
                }
            } catch (_: Exception) {}
            return out.distinct()
        }
    }
}

object ShareServerHolder {
    @Volatile
    var server: ReadOnlyShareServer? = null

    fun isRunning(): Boolean = server?.isAlive == true

    fun start(context: Context, root: File? = null, port: Int = ReadOnlyShareServer.DEFAULT_PORT): String {
        stop()
        val s = ReadOnlyShareServer(context.applicationContext, port, root)
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = s
        val ips = ReadOnlyShareServer.localIpv4()
        return "Exponiendo ${s.rootDir.absolutePath}\nPuerto $port\nIPs: ${ips.joinToString()}"
    }

    fun stop() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
    }
}
