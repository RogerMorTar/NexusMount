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
 * Cliente SMB real usando smbj.
 * Permite listar y probar conexión a shares de red (incluido vía Tailscale si el VPN está activo).
 */
object SmbHelper {

    data class SmbResult(
        val success: Boolean,
        val message: String,
        val files: List<FileEntry> = emptyList()
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
            SmbResult(true, "OK", list)
        } catch (e: Exception) {
            SmbResult(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    fun createDriveFromSmb(
        name: String,
        host: String,
        share: String
    ): DriveItem = DriveItem(
        id = UUID.randomUUID().toString(),
        name = name.ifBlank { "SMB $host" },
        type = DriveType.SMB,
        path = "//$host/$share",
        status = DriveStatus.ONLINE
    )
}
