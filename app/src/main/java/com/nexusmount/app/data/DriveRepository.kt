package com.nexusmount.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Persistencia local de unidades y transferencias.
 * Las conexiones SMB reales se manejan en SmbClient.
 */
class DriveRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexusmount", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getDrives(): MutableList<DriveItem> {
        val json = prefs.getString("drives", null) ?: return defaultDrives()
        val type = object : TypeToken<MutableList<DriveItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: defaultDrives()
        } catch (_: Exception) {
            defaultDrives()
        }
    }

    fun saveDrives(drives: List<DriveItem>) {
        prefs.edit().putString("drives", gson.toJson(drives)).apply()
    }

    fun addDrive(drive: DriveItem) {
        val list = getDrives()
        list.add(drive)
        saveDrives(list)
    }

    fun updateDrive(id: String, transform: (DriveItem) -> DriveItem) {
        val list = getDrives().map { if (it.id == id) transform(it) else it }.toMutableList()
        saveDrives(list)
    }

    fun removeDrive(id: String) {
        saveDrives(getDrives().filter { it.id != id })
    }

    fun getTransfers(): MutableList<TransferItem> {
        val json = prefs.getString("transfers", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<TransferItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveTransfers(items: List<TransferItem>) {
        prefs.edit().putString("transfers", gson.toJson(items)).apply()
    }

    private fun defaultDrives() = mutableListOf(
        DriveItem(
            id = UUID.randomUUID().toString(),
            name = "Almacenamiento interno",
            type = DriveType.LOCAL,
            path = "/storage/emulated/0",
            status = DriveStatus.ONLINE,
            usedGb = 32.0,
            totalGb = 128.0
        )
    )
}
