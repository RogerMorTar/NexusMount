package com.nexusmount.app.adblock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.net.URI

/**
 * Bloqueo de anuncios sin root:
 * - Lista local de dominios (filtro in-app)
 * - Acceso a DNS privado del sistema (AdGuard / DNS público)
 * Nota: el bloqueo TOTAL del sistema requiere DNS privado o VPN de terceros.
 */
object AdBlockManager {

    private const val PREFS = "nexus_adblock"

    /** Subconjunto de dominios publicitarios / trackers frecuentes */
    val BLOCKLIST = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "pagead2.googlesyndication.com", "adservice.google.com",
        "facebook.com/tr", "connect.facebook.net",
        "ads.yahoo.com", "adsystem.amazon.com",
        "scorecardresearch.com", "outbrain.com", "taboola.com",
        "adnxs.com", "adsrvr.org", "advertising.com",
        "moatads.com", "pubmatic.com", "rubiconproject.com",
        "criteo.com", "casalemedia.com", "openx.net",
        "adsafeprotected.com", "hotjar.com", "mixpanel.com",
        "branch.io", "appsflyer.com", "adjust.com"
    )

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", on).apply()
    }

    fun shouldBlockHost(ctx: Context, host: String?): Boolean {
        if (!isEnabled(ctx) || host.isNullOrBlank()) return false
        val h = host.lowercase().removePrefix("www.")
        return BLOCKLIST.any { h == it || h.endsWith(".$it") || h.contains(it) }
    }

    fun shouldBlockUrl(ctx: Context, url: String): Boolean {
        return try {
            val host = URI(url).host
            shouldBlockHost(ctx, host)
        } catch (_: Exception) {
            false
        }
    }

    fun openPrivateDnsSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    fun stats(ctx: Context): String {
        val on = isEnabled(ctx)
        val blocked = ctx.getSharedPreferences(PREFS, 0).getInt("blocked_count", 0)
        return "Filtro in-app: ${if (on) "ACTIVO" else "inactivo"}\n" +
            "Dominios en lista: ${BLOCKLIST.size}\n" +
            "Bloqueos registrados: $blocked"
    }

    fun recordBlock(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        p.edit().putInt("blocked_count", p.getInt("blocked_count", 0) + 1).apply()
    }
}
