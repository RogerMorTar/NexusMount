package com.nexusmount.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

/**
 * Detecta si Tailscale está instalado y si hay interfaces/IPs típicas de Tailscale (100.x.x.x).
 * No controla Tailscale: eso lo hace la app oficial.
 */
object TailscaleUtil {

    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openTailscale(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
        if (launch != null) {
            context.startActivity(launch)
        } else {
            // Abrir Play Store
            val market = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$TAILSCALE_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(market)
            } catch (_: Exception) {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** IPs CGNAT de Tailscale suelen ser 100.64.0.0/10 */
    fun getTailscaleIpv4(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (iface in interfaces) {
                val name = iface.name?.lowercase() ?: ""
                // Interfaces típicas: tailscale0, tun0, etc.
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val host = addr.hostAddress ?: continue
                    if (host.contains(":")) continue // skip IPv6 here
                    if (host.startsWith("100.")) {
                        result.add("$host (${iface.name})")
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun hasVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.allNetworks
        for (n in networks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        }
        return false
    }

    fun statusSummary(context: Context): String {
        val installed = isInstalled(context)
        val ips = getTailscaleIpv4()
        val vpn = hasVpnActive(context)
        return buildString {
            append(if (installed) "Tailscale instalado" else "Tailscale NO instalado")
            append("\n")
            append(if (vpn) "VPN activa" else "VPN no detectada")
            append("\n")
            if (ips.isNotEmpty()) {
                append("IPs Tailscale:\n")
                ips.forEach { append("• $it\n") }
            } else {
                append("Sin IP 100.x detectada")
            }
        }
    }
}
