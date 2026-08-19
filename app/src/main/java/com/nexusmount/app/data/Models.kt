package com.nexusmount.app.data

data class DriveItem(
    val id: String,
    val name: String,
    val type: DriveType,
    val path: String,
    val status: DriveStatus = DriveStatus.OFFLINE,
    val usedGb: Double = 0.0,
    val totalGb: Double = 0.0
)

enum class DriveType { SMB, LOCAL, TAILSCALE, CLOUD, NFS, WEBDAV }

enum class DriveStatus { ONLINE, OFFLINE, SYNCING, ERROR }

data class TransferItem(
    val id: String,
    val name: String,
    val from: String,
    val to: String,
    val progress: Int = 0,
    val status: String = "pending"
)

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val path: String
)
