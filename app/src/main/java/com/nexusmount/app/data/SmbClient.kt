package com.nexusmount.app.data

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit

object SmbHelper {

    data class SmbResult(
        val success: Boolean,
        val message: String,
        val files: List<FileEntry> = emptyList(),
        val shares: List<String> = emptyList(),
        val detail: String = ""
    )

    private val COMMON_SHARES = listOf(
        "Users", "users", "User", "user",
        "share", "Share", "Shared", "shared", "SHARE",
        "public", "Public", "PUBLIC",
        "data", "Data", "Datos", "datos", "DATA",
        "Documents", "Documentos", "documents",
        "Downloads", "Descargas",
        "Videos", "videos", "Music", "music", "Pictures", "Fotos", "fotos",
        "media", "Media", "homes", "home",
        "backup", "Backup", "Backups", "backups",
        "nas", "NAS", "files", "Files", "storage", "Storage",
        "smb", "SMB", "disk", "Disk",
        "C$", "D$", "E$", "F$", "G$", "H$"
    )

    private fun config(signing: Boolean): SmbConfig {
        return SmbConfig.builder()
            .withTimeout(45, TimeUnit.SECONDS)
            .withSoTimeout(45, TimeUnit.SECONDS)
            .withTransactTimeout(45, TimeUnit.SECONDS)
            .withDialects(
                SMB2Dialect.SMB_3_1_1,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_2_0_2
            )
            .withMultiProtocolNegotiate(true)
            .withSigningRequired(signing)
            .withDfsEnabled(true)
            .build()
    }

    fun tcpProbe(host: String, port: Int = 445, timeoutMs: Int = 10000): Pair<Boolean, String> {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            }
            true to "Puerto $port abierto en $host"
        } catch (e: Exception) {
            false to "No hay TCP a $host:$port (${e.message}). Tailscale + firewall SMB."
        }
    }

    private fun authVariants(username: String, password: String, domain: String): List<AuthenticationContext> {
        val list = mutableListOf<AuthenticationContext>()
        val user = username.trim()
        val dom = domain.trim()
        val pass = password.toCharArray()
        if (user.isNotEmpty()) {
            list.add(AuthenticationContext(user, pass, dom))
            list.add(AuthenticationContext(user, pass, ""))
            list.add(AuthenticationContext(user, pass, "WORKGROUP"))
            if (dom.isNotEmpty()) list.add(AuthenticationContext(user, pass, dom.uppercase()))
            if (user.contains("\\")) {
                val p = user.split("\\", limit = 2)
                list.add(AuthenticationContext(p[1], pass, p[0]))
            }
            if (user.contains("@")) {
                val p = user.split("@", limit = 2)
                list.add(AuthenticationContext(p[0], pass, p[1]))
            }
        }
        try { list.add(AuthenticationContext.guest()) } catch (_: Throwable) {
            list.add(AuthenticationContext("Guest", CharArray(0), ""))
        }
        try { list.add(AuthenticationContext.anonymous()) } catch (_: Throwable) {
            list.add(AuthenticationContext("", CharArray(0), ""))
        }
        return list.distinctBy { "${it.username}|${it.domain}|${String(it.password)}" }
    }

    private fun connectSession(
        host: String,
        username: String,
        password: String,
        domain: String
    ): Triple<SMBClient, Connection, Session> {
        var last: Exception? = null
        for (signing in listOf(false, true)) {
            val client = SMBClient(config(signing))
            try {
                val connection = client.connect(host.trim())
                for (auth in authVariants(username, password, domain)) {
                    try {
                        val session = connection.authenticate(auth)
                        return Triple(client, connection, session)
                    } catch (e: Exception) {
                        last = e
                    }
                }
                try { connection.close() } catch (_: Exception) {}
            } catch (e: Exception) {
                last = e
            }
            try { client.close() } catch (_: Exception) {}
        }
        throw last ?: Exception("No se pudo autenticar en $host")
    }

    private fun closeAll(client: SMBClient?, connection: Connection?, session: Session?) {
        try { session?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
    }

    private fun friendlyError(e: Exception, host: String): String {
        val raw = e.message ?: e.javaClass.simpleName
        val m = raw.lowercase()
        return when {
            m.contains("timeout") || m.contains("timed out") ->
                "Tiempo agotado con $host. Tailscale Connected + PC despierto + IP 100.x."
            m.contains("unreachable") ->
                "Red inalcanzable ($host). Misma cuenta Tailscale."
            m.contains("refused") || m.contains("econnrefused") ->
                "Rechazado $host:445. Activa compartir archivos / Samba."
            m.contains("logon") || m.contains("access denied") || m.contains("status_logon") ||
                m.contains("authentication") || m.contains("status_access") ->
                "Acceso denegado. Usuario/clave de la cuenta LOCAL del PC. Dominio vacio si no aplica."
            m.contains("object_name_not_found") || m.contains("not found") ||
                m.contains("bad_network_name") || m.contains("path_not_covered") ->
                "El share o la carpeta no existe con ese nombre.\n" +
                    "En el PC: Propiedades → Compartir → nombre EXACTO del recurso (no C:\\Users\\...)."
            m.contains("negotiate") || m.contains("dialect") ->
                "SMB incompatible. Activa SMB 2/3 en el PC."
            m.contains("resolve") || m.contains("unknown host") ->
                "Host desconocido. Usa IP numerica."
            else -> "Error: $raw"
        }
    }

    private fun isDirectory(info: FileIdBothDirectoryInformation): Boolean {
        return try {
            EnumWithValue.EnumUtils.isSet(
                info.fileAttributes,
                FileAttributes.FILE_ATTRIBUTE_DIRECTORY
            )
        } catch (_: Exception) {
            try {
                val v = info.fileAttributes
                when (v) {
                    is Long -> (v and 0x10L) != 0L
                    is Number -> (v.toLong() and 0x10L) != 0L
                    else -> v.toString().contains("DIRECTORY", ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun diagnose(host: String): SmbResult = withContext(Dispatchers.IO) {
        val h = host.trim()
        if (h.isEmpty()) return@withContext SmbResult(false, "IP vacia")
        val (ok445, msg445) = tcpProbe(h, 445)
        SmbResult(ok445, "Diagnostico de $h\n• $msg445\n" +
            if (!ok445) "Sin 445 no hay Samba." else "445 OK. Si falla listado: usuario o nombre de share.")
    }

    suspend fun testConnection(
        host: String,
        share: String,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        val h = host.trim()
        val s = share.trim().trim('/')
        val (tcpOk, tcpMsg) = tcpProbe(h)
        if (!tcpOk) return@withContext SmbResult(false, tcpMsg)
        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val t = connectSession(h, username, password, domain)
            client = t.first; connection = t.second; session = t.third
            val disk = session.connectShare(s) as DiskShare
            val entries = disk.list("").filter {
                val n = it.fileName ?: ""
                n.isNotEmpty() && n != "." && n != ".."
            }
            SmbResult(true, "Conectado a //$h/$s (${entries.size} elementos en raiz)")
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, h), detail = e.message ?: "")
        } finally {
            closeAll(client, connection, session)
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
            val t = connectSession(h, username, password, domain)
            client = t.first; connection = t.second; session = t.third
            val found = linkedSetOf<String>()
            var lastShareErr = ""
            for (name in COMMON_SHARES) {
                try {
                    val sh = session.connectShare(name)
                    try {
                        if (sh is DiskShare) {
                            try { sh.list("").take(1) } catch (_: Exception) {}
                            found.add(name)
                        } else found.add(name)
                    } finally {
                        try { sh.close() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    lastShareErr = "$name: ${(e.message ?: "").take(100)}"
                }
            }
            val list = found.toList().sorted()
            if (list.isEmpty()) {
                SmbResult(
                    false,
                    "Login OK en $h, pero no se detecto ningun share conocido.\n\n" +
                        "Pon el nombre EXACTO del recurso en «Share manual».\n" +
                        "Windows: carpeta → Propiedades → Compartir → nombre del recurso.\n\n" +
                        "Ultimo error: $lastShareErr"
                )
            } else {
                SmbResult(true, "${list.size} recurso(s) en $h", shares = list)
            }
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, h), detail = e.message ?: "")
        } finally {
            closeAll(client, connection, session)
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
            val t = connectSession(host, username, password, domain)
            client = t.first; connection = t.second; session = t.third
            val disk = session.connectShare(share.trim()) as DiskShare
            val remotePath = path.trim().trim('/').replace('\\', '/')
            val rawList: List<FileIdBothDirectoryInformation> = try {
                disk.list(remotePath)
            } catch (e: Exception) {
                disk.list(remotePath.replace('/', '\\'))
            }
            val list = rawList.mapNotNull { info ->
                val name = info.fileName ?: return@mapNotNull null
                if (name == "." || name == ".." || name.isBlank()) return@mapNotNull null
                val dir = isDirectory(info)
                val size = try { info.endOfFile } catch (_: Exception) { 0L }
                val childPath = if (remotePath.isEmpty()) name else "$remotePath/$name"
                FileEntry(name = name, isDirectory = dir, sizeBytes = size, path = childPath)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SmbResult(
                true,
                if (list.isEmpty()) "Carpeta vacia o sin permiso de listado" else "OK (${list.size})",
                files = list
            )
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, host), detail = e.message ?: "")
        } finally {
            closeAll(client, connection, session)
        }
    }

    suspend fun downloadFile(
        host: String,
        share: String,
        username: String,
        password: String,
        remotePath: String,
        destFile: File,
        domain: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): SmbResult = withContext(Dispatchers.IO) {
        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val t = connectSession(host, username, password, domain)
            client = t.first; connection = t.second; session = t.third
            val disk = session.connectShare(share) as DiskShare
            val path = remotePath.trim().trimStart('/').replace('\\', '/')
            destFile.parentFile?.mkdirs()
            val access = EnumSet.of(AccessMask.GENERIC_READ, AccessMask.FILE_READ_DATA)
            val attrs = EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL)
            val shareAccess = EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ)
            val options = EnumSet.noneOf(SMB2CreateOptions::class.java)
            disk.openFile(path, access, attrs, shareAccess, SMB2CreateDisposition.FILE_OPEN, options).use { smbFile ->
                val total = try { smbFile.fileInformation.standardInformation.endOfFile } catch (_: Exception) { 0L }
                smbFile.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            copied += n
                            if (total > 0 && onProgress != null) {
                                onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
            SmbResult(true, "Descargado: ${destFile.name}")
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, host), detail = e.message ?: "")
        } finally {
            closeAll(client, connection, session)
        }
    }

    suspend fun deleteRemote(
        host: String,
        share: String,
        username: String,
        password: String,
        remotePath: String,
        isDirectory: Boolean,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        var client: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        try {
            val t = connectSession(host, username, password, domain)
            client = t.first; connection = t.second; session = t.third
            val disk = session.connectShare(share) as DiskShare
            val path = remotePath.trim().trimStart('/').replace('\\', '/')
            if (isDirectory) disk.rmdir(path, true) else disk.rm(path)
            SmbResult(true, "Eliminado: $path")
        } catch (e: Exception) {
            SmbResult(false, friendlyError(e, host), detail = e.message ?: "")
        } finally {
            closeAll(client, connection, session)
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
