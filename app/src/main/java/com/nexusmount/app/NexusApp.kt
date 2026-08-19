package com.nexusmount.app

import android.app.Application
import com.nexusmount.app.data.DriveRepository

class NexusApp : Application() {
    lateinit var repository: DriveRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DriveRepository(this)
    }
}
