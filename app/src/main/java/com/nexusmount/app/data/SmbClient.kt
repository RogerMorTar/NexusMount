package com.nexusmount.app.data

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.mssmb2.SMB2Dialect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Cliente SMB robusto para Android (Tailscale / LAN).
 */
object SmbHelper {

    data class SmbResult(
        val success: Boolean,
        val message: String,
        val files: List<FileEntry> = emptyList(),
        val shares: List<String> = emptyList(),
        val detail: String = ""
    )

    private val COMMON_SHARES = listOf(
        "share", "Shared", "Share", "public", "Public", "data", "Data", "Datos",
        "media", "Media", "homes", "Users", "user", "documents", "Documents",
        "backup", "Backups", "nas", "NAS", "files", "Files", "smb", "storage",
        "videos", "Fotos", "fotos", "music", "Music",
        "C$", "D$", "E$", "F$"
    )

    private fun buildConfig(): SmbConfig {
        return SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .withTransactTimeout(30, TimeUnit.SECONDS)
            .withDialects(
                SMB2Dialect.SMB_3_1_1,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_2_0_2
            )
            .withMultiProtocolNegotiate(true)
            .withSigningRequired(false)
            .build()
    }

    /** Comprueba TCP 445 antes de SMB. */
    fun tcpProbe(host: String, port: Int = 445, timeoutMs: Int = 8000): Pair<Boolean, String> {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            }
            true to "Puerto $port abierto en $host"
        } catch (e: Exception) {
            false to "No hay conexión TCP a $host:$port — ${e.message}. " +
                "¿Tailscale conectado en ambos? ¿Firewall del PC bloquea SMB?"
        }
    }

    private fun authVariants(username: String, password: String, domain: String): List<AuthenticationContext> {
        val list = mutableListOf<AuthenticationContext>()
        val user = username.trim()
        val dom = domain.trim()

        if (user.isNotEmpty()) {
            // usuario + dominio explícito
            list.add(AuthenticationContext(user, password.toCharArray(), dom))
            // usuario sin dominio
            if (dom.isNotEmpty()) {
                list.add(AuthenticationContext(user, password.toCharArray(), ""))
            }
            // DOMAIN\user partido
            if (user.contains("\\")) {
                val parts = user.split("\\", limit = 2)
                list.add(AuthenticationContext(parts[1], password.toCharArray(), parts[0]))
            }
            // user@domain
            if (user.contains("@")) {
                val parts = user.split("@", limit = 2)
                list.add(AuthenticationContext(parts[0], password.toCharArray(), parts[1]))
            }
        }
        // Invitado / anónimo (muchos NAS lo permiten en lectura)
        try { list.add(AuthenticationContext.guest()) } catch (_: Throwable) {
            list.add(AuthenticationContext("Guest", CharArray(0), ""))
        }
        try { list.add(AuthenticationContext.anonymous()) } catch (_: Throwable) {
            list.add(AuthenticationContext("", CharArray(0), ""))
        }
        return list.distinctBy { "${it.username}|${it.domain}|${it.password.concatToString()}" }
    }

    private fun connectWithAuth(
        host: String,
        username: String,
        password: String,
        domain: String
    ): Triple<SMBClient, Connection, Session> {
        val client = SMBClient(buildConfig())
        val connection = client.connect(host.trim())
        var last: Exception? = null
        for (auth in authVariants(username, password, domain)) {
            try {
                val session = connection.authenticate(auth)
                return Triple(client, connection, session)
            } catch (e: Exception) {
                last = e
            }
        }
        try { connection.close() } catch (_: Exception) {}
        try { client.close() } catch (_: Exception) {}
        throw last ?: Exception("Autenticación fallida")
    }

    private fun friendlyError(e: Exception, host: String): String {
        val m = (e.message ?: e.javaClass.simpleName).lowercase()
        val raw = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("timeout") || m.contains("timed out") ->
                "Tiempo de espera agotado con $host.\n• Activa Tailscale en el móvil y en el PC\n• Usa la IP 100.x del PC (app Tailscale → Machines)\n• Comprueba que el PC no esté suspendido"
            m.contains("unreachable") || m.contains("enroute") || m.contains("network is unreachable") ->
                "Red inalcanzable ($host).\n• Tailscale debe estar Connected en ambos dispositivos\n• Misma cuenta Tailscale"
            m.contains("refused") || m.contains("econnrefused") ->
                "Conexión rechazada en $host:445.\n• Samba/Compartir archivos activo en el PC\n• Firewall de Windows: permitir SMB (puerto 445)"
            m.contains("logon") || m.contains("access denied") || m.contains("status_logon") ||
                m.contains("authentication") || m.contains("unauthorized") ->
                "Usuario o contraseña incorrectos (o el PC no acepta este tipo de acceso).\n• En Windows: usuario de la cuenta local o Microsoft\n• Prueba dominio vacío\n• En algunos NAS: usuario del NAS, no el de Windows"
            m.contains("negotiate") || m.contains("dialect") || m.contains("smb") ->
                "Problema de protocolo SMB con $host.\n• Activa SMB 2/3 en el PC (Windows: características de SMB)"
            m.contains("resolve") || m.contains("unknown host") ->
                "No se resuelve el host «$host». Usa la IP numérica (100.x.x.x o 192.168.x.x)."
            else -> "Error: $raw"
        }
    }

    suspend fun diagnose(host: String): SmbResult = withContext(Dispatchers.IO) {
        val h = host.trim()
        if (h.isEmpty()) return@withContext SmbResult(false, "IP vacía")
        val (ok445, msg445) = tcpProbe(h, 445)
        val (ok139, msg139) = tcpProbe(h, 139, 3000)
        val text = buildString {
            appendLine("Diagnóstico de $h")
            appendLine("• $msg445")
            appendLine("• Puerto 139: ${if (ok139) "abierto" else "cerrado/no responde"}")
            if (!ok445) {
                appendLine()
                appendLine("Sin puerto 445 no se puede usar Samba.")
                appendLine("Revisa Tailscale + firewall del PC.")
            } else {
                appendLine()
                appendLine("El puerto 445 responde. Si falla el login, revisa usuario/clave.")
            }
        }
        SmbResult(ok445, text, detail = msg445)
    }

    suspend fun testConnection(
        host: String,
        share: String,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        val h = host.trim()
        val (tcpOk, tcpMsg) = tcpProbe(h)
        if (!tcpOk) return@withContext SmbResult(false, tcpMsg)

        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val triple = connectWithAuth(h, username, password, domain)
            client = triple.first
            connection = triple.second
            session = triple.third
            val disk = session.connectShare(share.trim()) as DiskShare
            // prueba listar raíz
            try {
                disk.list("").take(3)
            } catch (_: Exception) {}
            SmbResult(true, "Conectado a //$h/${share.trim()}")
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, h), detail = e.message ?: "")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
        }
    }

    suspend fun listShares(
        host: String,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        val h = host.trim()
        val (tcpOk, tcpMsg) = tcpProbe(h)
        if (!tcpOk) return@withContext SmbResult(false, tcpMsg)

        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val triple = connectWithAuth(h, username, password, domain)
            client = triple.first
            connection = triple.second
            session = triple.third

            val found = linkedSetOf<String>()
            tryEnumerateShares(session, found)

            for (name in COMMON_SHARES) {
                try {
                    val sh = session.connectShare(name)
                    try {
                        found.add(name)
                    } finally {
                        try { sh.close() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                }
            }

            val list = found.toList().sorted()
            if (list.isEmpty()) {
                SmbResult(
                    false,
                    "Login OK en $h, pero no se encontró ningún share.\n" +
                        "En el PC: crea una carpeta compartida (ej. «share») y vuelve a listar,\n" +
                        "o escribe el nombre exacto del recurso en «Share manual»."
                )
            } else {
                SmbResult(true, "${list.size} recurso(s) en $h", shares = list)
            }
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, h), detail = e.message ?: "")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
        }
    }

    private fun tryEnumerateShares(session: Session, out: MutableSet<String>) {
        try {
            val m = session.javaClass.methods.firstOrNull {
                it.name.equals("getShares", true) || it.name.equals("listShares", true)
            }
            if (m != null && m.parameterCount == 0) {
                when (val result = m.invoke(session)) {
                    is Collection<*> -> result.forEach { extractShareName(it)?.let { n -> out.add(n) } }
                    is Array<*> -> result.forEach { extractShareName(it)?.let { n -> out.add(n) } }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractShareName(item: Any?): String? {
        if (item == null) return null
        if (item is String) return item.takeIf { it.isNotBlank() && !it.equals("IPC$", true) }
        return try {
            val m = item.javaClass.methods.firstOrNull {
                it.name.equals("getNetName", true) ||
                    it.name.equals("getName", true) ||
                    it.name.equals("getShareName", true)
            }
            (m?.invoke(item) as? String)?.takeIf { it.isNotBlank() && !it.equals("IPC$", true) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun listFiles(
        host: String,
        share: String,
        username: String,
        password: String,
        path: String = "",
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val triple = connectWithAuth(host, username, password, domain)
            client = triple.first
            connection = triple.second
            session = triple.third
            val disk = session.connectShare(share) as DiskShare
            val remotePath = path.trim('/').ifEmpty { "" }
            val list = disk.list(remotePath).mapNotNull { info ->
                val name = info.fileName ?: return@mapNotNull null
                if (name == "." || name == "..") return@mapNotNull null
                FileEntry(
                    name = name,
                    isDirectory = info.fileAttributes?.and(0x10L) != 0L,
                    sizeBytes = info.endOfFile ?: 0L,
                    path = if (remotePath.isEmpty()) name else "$remotePath/$name"
                )
            }
            SmbResult(true, "OK", files = list)
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, host), detail = e.message ?: "")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
        }
    }

    fun createDriveFromSmb(name: String, host: String, share: String): DriveItem =
        DriveItem(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "SMB $host/$share" },
            type = DriveType.SMB,
            path = "//$host/$share",
            status = DriveStatus.ONLINE
        )
}
