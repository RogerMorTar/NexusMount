package com.nexusmount.app.interconnect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val path: String
)

object InterconnectClient {

    data class Result(
        val success: Boolean,
        val message: String,
        val entries: List<RemoteEntry> = emptyList()
    )

    suspend fun probe(host: String, port: Int = InterconnectServer.PORT): Result =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://${host.trim()}:$port/api/info")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                if (code != 200) return@withContext Result(false, "HTTP $code")
                val json = JSONObject(body)
                if (json.optString("mode") != "interconnect-readonly") {
                    return@withContext Result(false, "No es un visor NexusMount en ese puerto")
                }
                Result(true, "Visor NexusMount · ${json.optString("root")}")
            } catch (e: Exception) {
                Result(
                    false,
                    "No hay NexusMount (app/web) exponiendo disco en $host:${InterconnectServer.PORT}.\n" +
                        "En el otro dispositivo: Interconexión → Exponer mi disco.\n(${e.message})"
                )
            }
        }

    suspend fun list(host: String, path: String = "", port: Int = InterconnectServer.PORT): Result =
        withContext(Dispatchers.IO) {
            try {
                val q = URLEncoder.encode(path, "UTF-8")
                val url = URL("http://${host.trim()}:$port/api/list?path=$q")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 20000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                val entries = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RemoteEntry(
                        o.getString("name"),
                        o.getBoolean("dir"),
                        o.optLong("size"),
                        o.getString("path")
                    )
                }
                Result(true, "OK (${entries.size})", entries)
            } catch (e: Exception) {
                Result(false, "Error listando: ${e.message}")
            }
        }

    suspend fun download(
        host: String,
        remotePath: String,
        dest: File,
        port: Int = InterconnectServer.PORT
    ): Result = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(remotePath, "UTF-8")
            val url = URL("http://${host.trim()}:$port/api/download?path=$q")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 180000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) {
                return@withContext Result(false, "Descarga HTTP ${conn.responseCode}")
            }
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            Result(true, "Copiado: ${dest.name}")
        } catch (e: Exception) {
            Result(false, "Error descarga: ${e.message}")
        }
    }
}
