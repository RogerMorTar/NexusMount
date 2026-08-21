package com.nexusmount.app.interconnect

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * HTTP solo lectura: expone el almacenamiento de ESTE dispositivo
 * a otros NexusMount (app/web) en Wi‑Fi o Tailscale.
 * No permite borrar ni modificar.
 */
class InterconnectServer(private val context: Context) {

    companion object {
        const val PORT = 3847
        @Volatile var running: Boolean = false
            private set
        @Volatile var rootPath: String = ""
            private set
        private var instance: InterconnectServer? = null

        fun get(context: Context): InterconnectServer {
            if (instance == null) instance = InterconnectServer(context.applicationContext)
            return instance!!
        }
    }

    private var serverSocket: ServerSocket? = null
    private val active = AtomicBoolean(false)

    fun start(root: File = defaultRoot()): Boolean {
        if (active.get()) return true
        if (!root.exists()) root.mkdirs()
        rootPath = root.absolutePath
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(PORT))
            serverSocket = ss
            active.set(true)
            running = true
            thread(isDaemon = true, name = "nexus-interconnect") {
                while (active.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        thread(isDaemon = true) { handle(client, root) }
                    } catch (_: Exception) {
                        if (!active.get()) break
                    }
                }
            }
            true
        } catch (_: Exception) {
            running = false
            active.set(false)
            false
        }
    }

    fun stop() {
        active.set(false)
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun defaultRoot(): File {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists() && ext.canRead()) return ext
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    private fun handle(socket: Socket, root: File) {
        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(socket.getOutputStream(), 400, "text/plain", "Bad request")
                return
            }
            val method = parts[0]
            val pathQuery = parts[1]
            val pathOnly = pathQuery.substringBefore("?")
            val query = pathQuery.substringAfter("?", "")

            if (method != "GET") {
                writeResponse(socket.getOutputStream(), 405, "text/plain", "Only GET (read-only)")
                return
            }

            when {
                pathOnly == "/" || pathOnly == "/api/info" -> {
                    val info = JSONObject()
                        .put("app", "NexusMount")
                        .put("mode", "interconnect-readonly")
                        .put("root", root.absolutePath)
                        .put("writable", false)
                    writeResponse(socket.getOutputStream(), 200, "application/json", info.toString())
                }
                pathOnly == "/api/list" -> {
                    val rel = queryParam(query, "path") ?: ""
                    val dir = safeResolve(root, rel)
                    if (dir == null || !dir.exists() || !dir.isDirectory) {
                        writeResponse(
                            socket.getOutputStream(), 404, "application/json",
                            JSONObject().put("error", "not found").toString()
                        )
                        return
                    }
                    val arr = JSONArray()
                    dir.listFiles()
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?.forEach { f ->
                            arr.put(
                                JSONObject()
                                    .put("name", f.name)
                                    .put("dir", f.isDirectory)
                                    .put("size", if (f.isFile) f.length() else 0)
                                    .put("path", relativePath(root, f))
                            )
                        }
                    writeResponse(socket.getOutputStream(), 200, "application/json", arr.toString())
                }
                pathOnly == "/api/download" -> {
                    val rel = queryParam(query, "path") ?: ""
                    val file = safeResolve(root, rel)
                    if (file == null || !file.exists() || !file.isFile) {
                        writeResponse(socket.getOutputStream(), 404, "text/plain", "Not found")
                        return
                    }
                    writeFile(socket.getOutputStream(), file)
                }
                else -> writeResponse(socket.getOutputStream(), 404, "text/plain", "Not found")
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun queryParam(query: String, key: String): String? {
        if (query.isEmpty()) return null
        for (part in query.split("&")) {
            val kv = part.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == key) {
                return URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return null
    }

    private fun safeResolve(root: File, rel: String): File? {
        val cleaned = rel.trim().trimStart('/').replace("..", "")
        val target = if (cleaned.isEmpty()) root else File(root, cleaned)
        val rootCanon = root.canonicalFile
        val targetCanon = try { target.canonicalFile } catch (_: Exception) { return null }
        if (!targetCanon.path.startsWith(rootCanon.path)) return null
        return targetCanon
    }

    private fun relativePath(root: File, file: File): String {
        val r = root.canonicalPath
        val f = file.canonicalPath
        return if (f.startsWith(r)) f.removePrefix(r).trimStart('/', '\\') else file.name
    }

    private fun writeResponse(out: OutputStream, code: Int, mime: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header =
            "HTTP/1.1 $code OK\r\nContent-Type: $mime; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun writeFile(out: OutputStream, file: File) {
        val header =
            "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\nContent-Disposition: attachment; filename=\"${file.name}\"\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray())
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        }
        out.flush()
    }
}
