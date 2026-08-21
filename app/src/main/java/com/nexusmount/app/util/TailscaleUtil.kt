package com.nexusmount.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Detección real de Tailscale (app + VPN + IPs 100.x).
 * Requiere <queries> en el manifest (Android 11+).
 */
object TailscaleUtil {

    private val PACKAGES = listOf(
        "com.tailscale.ipn",
        "com.tailscale.ipn.dev"
    )

    fun isInstalled(context: Context): Boolean {
        for (pkg in PACKAGES) {
            if (packageInstalled(context, pkg)) return true
        }
        // Fallback: lanzador
        for (pkg in PACKAGES) {
            if (context.packageManager.getLaunchIntentForPackage(pkg) != null) return true
        }
        return false
    }

    private fun packageInstalled(context: Context, pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun installedPackage(context: Context): String? {
        for (pkg in PACKAGES) {
            if (packageInstalled(context, pkg) ||
                context.packageManager.getLaunchIntentForPackage(pkg) != null
            ) return pkg
        }
        return null
    }

    fun openTailscale(context: Context) {
        val pkg = installedPackage(context) ?: PACKAGES.first()
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=${PACKAGES.first()}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://play.google.com/store/apps/details?id=${PACKAGES.first()}"
                    )
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** IPs 100.64.0.0/10 (CGNAT Tailscale). */
    fun getTailscaleIpv4(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                try {
                    if (!iface.isUp) continue
                } catch (_: Exception) {
                }
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address || (addr.hostAddress?.contains(":") == false)) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("100.")) {
                            // 100.64.0.0/10 → 100.64–100.127
                            val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                            if (second in 64..127 || host.startsWith("100.")) {
                                result.add("$host (${iface.name})")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result.distinct()
    }

    fun hasVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nets = cm.allNetworks
                for (n in nets) {
                    val caps = cm.getNetworkCapabilities(n) ?: continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
                }
            }
            // Si hay IP 100.x, casi seguro Tailscale/VPN mesh
            getTailscaleIpv4().isNotEmpty()
        } catch (_: Exception) {
            getTailscaleIpv4().isNotEmpty()
        }
    }

    fun statusSummary(context: Context): String {
        val installed = isInstalled(context)
        val pkg = installedPackage(context)
        val vpn = hasVpnActive(context)
        val ips = getTailscaleIpv4()
        return buildString {
            appendLine("Instalado: ${if (installed) "Sí (${pkg ?: "ok"})" else "No detectado"}")
            appendLine("VPN activa: ${if (vpn) "Sí" else "No"}")
            if (ips.isEmpty()) {
                appendLine("IPs 100.x: (ninguna — abre Tailscale y conecta)")
            } else {
                appendLine("IPs Tailscale:")
                ips.forEach { appendLine("  • $it") }
            }
            if (!installed) {
                appendLine()
                appendLine("Si Tailscale está instalado y sigue saliendo No:")
                appendLine("reinicia la app tras actualizar NexusMount.")
            }
        }
    }
}
