package com.nexusmount.app.cloud

/**
 * Conectores cloud — estructura lista para APIs reales.
 * Google Drive y S3 requieren credenciales OAuth / AWS keys del usuario.
 */
object CloudConnectors {

    data class CloudAccount(
        val id: String,
        val provider: Provider,
        val displayName: String,
        val configured: Boolean
    )

    enum class Provider { GOOGLE_DRIVE, S3, ONEDRIVE, WEBDAV }

    fun describeSetup(provider: Provider): String = when (provider) {
        Provider.GOOGLE_DRIVE -> """
            Google Drive:
            1. Crea un proyecto en Google Cloud Console
            2. Activa Google Drive API
            3. OAuth 2.0 Client ID (Android)
            4. Configura el SHA-1 de la app
            Luego se usará Google Sign-In + Drive REST API.
        """.trimIndent()
        Provider.S3 -> """
            Amazon S3 / compatible:
            • Access Key ID
            • Secret Access Key
            • Region
            • Bucket name
            Endpoint opcional (MinIO, Wasabi, R2…).
        """.trimIndent()
        Provider.ONEDRIVE -> """
            OneDrive: Microsoft Graph + MSAL (Azure app registration).
        """.trimIndent()
        Provider.WEBDAV -> """
            WebDAV: URL, usuario y contraseña (Nextcloud, ownCloud…).
        """.trimIndent()
    }

    /** Validación local de parámetros S3 (sin red). */
    fun validateS3(accessKey: String, secret: String, bucket: String, region: String): String? {
        if (accessKey.isBlank()) return "Access Key obligatorio"
        if (secret.isBlank()) return "Secret Key obligatorio"
        if (bucket.isBlank()) return "Bucket obligatorio"
        if (region.isBlank()) return "Region obligatoria"
        return null
    }
}
