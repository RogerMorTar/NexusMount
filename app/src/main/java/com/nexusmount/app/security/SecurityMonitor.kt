package com.nexusmount.app.security

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SecurityEvent(
    val time: Long,
    val type: String,
    val detail: String,
    val severity: String // info | warning | critical
)

/**
 * Registro de eventos de seguridad (fuerza bruta, rutas sensibles, etc.)
 * Persistido en SharedPreferences.
 */
class SecurityMonitor(context: Context) {

    private val prefs = context.getSharedPreferences("nexus_security", Context.MODE_PRIVATE)
    private val failedLogins = mutableMapOf<String, Int>()

    fun log(type: String, detail: String, severity: String = "info") {
        val line = "${System.currentTimeMillis()}|$severity|$severity|$type|$detail"
        val existing = prefs.getString("events", "") ?: ""
        val lines = (line + "\n" + existing).lines().take(200)
        prefs.edit().putString("events", lines.joinToString("\n")).apply()
    }

    fun recordFailedAuth(source: String) {
        val count = (failedLogins[source] ?: 0) + 1
        failedLogins[source] = count
        val sev = if (count >= 5) "critical" else "warning"
        log("brute_force", "Intentos fallidos desde $source: $count", sev)
    }

    fun recordSensitivePath(path: String) {
        log("sensitive_path", "Acceso a ruta sensible: $path", "warning")
    }

    fun getEvents(): List<SecurityEvent> {
        val raw = prefs.getString("events", "") ?: return emptyList()
        return raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) return@mapNotNull null
            SecurityEvent(
                time = p[0].toLongOrNull() ?: 0L,
                severity = p[1],
                type = p[2],
                detail = p.drop(3).joinToString("|")
            )
        }
    }

    fun formatTime(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }
}
