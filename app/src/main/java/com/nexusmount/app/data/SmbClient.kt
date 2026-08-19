package com.nexusmount.app.data

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Cliente SMB real (smbj).
 * - Probar conexión a un share
 * - Listar recursos del servidor (enumeración + sondeo de nombres comunes)
 * - Listar archivos de un share
 */
object SmbHelper {

    data class SmbResult(
        val success: Boolean,
        val message: String,
        val files: List<FileEntry> = emptyList(),
        val shares: List<String> = emptyList()
    )

    /** Nombres habituales a probar si la API no enumera shares. */
    private val COMMON_SHARES = listOf(
        "share", "Shared", "Share", "public", "Public", "data", "Data", "Datos",
        "media", "Media", "homes", "Users", "user", "documents", "Documents",
        "backup", "Backups", "nas", "NAS", "files", "Files", "smb", "storage",
        "C$", "D$", "E$", "F$", "ADMIN$", "IPC$"
    )

    suspend fun testConnection(
        host: String,
        share: String,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        try {
            val client = SMBClient()
            connection = client.connect(host)
            val auth = AuthenticationContext(username, password.toCharArray(), domain)
            session = connection.authenticate(auth)
            session.connectShare(share) as DiskShare
            SmbResult(true, "Conectado a //$host/$share")
        } catch (e: Exception) {
            SmbResult(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Lista recursos compartidos del servidor solo con IP + credenciales.
     * Intenta enumerar por API; si no, prueba nombres comunes.
     */
    suspend fun listShares(
        host: String,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        try {
            val client = SMBClient()
            connection = client.connect(host.trim())
            val auth = AuthenticationContext(
                username.ifBlank { "Guest" },
                password.toCharArray(),
                domain
            )
            session = connection.authenticate(auth)

            val found = linkedSetOf<String>()

            // 1) Intentar métodos de enumeración del session/connection por reflexión (varía según smbj)
            tryEnumerateShares(session, found)

            // 2) Sondeo de nombres habituales (abre y cierra el share)
            for (name in COMMON_SHARES) {
                try {
                    val sh = session.connectShare(name)
                    try {
                        if (name != "IPC$") found.add(name)
                    } finally {
                        try { sh.close() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    // no existe o sin permiso
                }
            }

            val list = found.toList().sorted()
            if (list.isEmpty()) {
                SmbResult(
                    false,
                    "No se encontraron shares en $host. " +
                        "Revisa usuario/contraseña o el nombre del recurso en el PC."
                )
            } else {
                SmbResult(
                    true,
                    "${list.size} recurso(s) en $host",
                    shares = list
                )
            }
        } catch (e: Exception) {
            SmbResult(
                false,
                "No se pudo conectar a $host: ${e.message ?: e.javaClass.simpleName}. " +
                    "¿Tailscale conectado? ¿IP correcta?"
            )
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    private fun tryEnumerateShares(session: Session, out: MutableSet<String>) {
        // smbj no siempre expone listShares; intentamos APIs conocidas sin romper compilación
        try {
            val m = session.javaClass.methods.firstOrNull {
                it.name.equals("getShares", true) || it.name.equals("listShares", true)
            }
            if (m != null && m.parameterCount == 0) {
                val result = m.invoke(session)
                when (result) {
                    is Collection<*> -> result.forEach { item ->
                        extractShareName(item)?.let { out.add(it) }
                    }
                    is Array<*> -> result.forEach { item ->
                        extractShareName(item)?.let { out.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val conn = session.connection
            val m = conn.javaClass.methods.firstOrNull { it.name.equals("listShares", true) }
            if (m != null && m.parameterCount == 0) {
                val result = m.invoke(conn)
                if (result is Collection<*>) {
                    result.forEach { extractShareName(it)?.let { n -> out.add(n) } }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractShareName(item: Any?): String? {
        if (item == null) return null
        if (item is String) return item.takeIf { it.isNotBlank() && it != "IPC$" }
        return try {
            val m = item.javaClass.methods.firstOrNull {
                it.name.equals("getNetName", true) ||
                    it.name.equals("getName", true) ||
                    it.name.equals("getShareName", true)
            }
            (m?.invoke(item) as? String)?.takeIf { it.isNotBlank() && it != "IPC$" }
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
        var connection: Connection? = null
        var session: Session? = null
        try {
            val client = SMBClient()
            connection = client.connect(host)
            val auth = AuthenticationContext(username, password.toCharArray(), domain)
            session = connection.authenticate(auth)
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
            SmbResult(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
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
