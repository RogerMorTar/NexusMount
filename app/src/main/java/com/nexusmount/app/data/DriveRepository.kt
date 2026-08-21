package com.nexusmount.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class DriveRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexusmount", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getDrives(): MutableList<DriveItem> {
        val json = prefs.getString("drives", null)
        if (json == null) {
            val defaults = defaultDrives()
            saveDrives(defaults)
            return defaults
        }
        val type = object : TypeToken<MutableList<DriveItem>>() {}.type
        return try {
            gson.fromJson<MutableList<DriveItem>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveDrives(drives: List<DriveItem>) {
        prefs.edit().putString("drives", gson.toJson(drives)).apply()
    }

    /** Añade unidad. Si ya existe el mismo path, no duplica y devuelve false. */
    fun addDrive(drive: DriveItem): Boolean {
        val list = getDrives()
        if (list.any { it.path.equals(drive.path, ignoreCase = true) }) {
            return false
        }
        list.add(drive)
        saveDrives(list)
        return true
    }

    fun updateDrive(id: String, transform: (DriveItem) -> DriveItem) {
        val list = getDrives().map { if (it.id == id) transform(it) else it }.toMutableList()
        saveDrives(list)
    }

    fun removeDrive(id: String) {
        saveDrives(getDrives().filter { it.id != id })
    }

    fun smbCount(): Int = getDrives().count { it.type == DriveType.SMB }

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
            usedGb = 0.0,
            totalGb = 0.0
        )
    )
}
