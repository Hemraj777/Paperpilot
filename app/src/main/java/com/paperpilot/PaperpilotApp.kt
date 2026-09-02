package com.paperpilot

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.paperpilot.data.AppDatabase

class PaperpilotApp : Application(), Configuration.Provider {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "paperpilot.db"
        )
            .fallbackToDestructiveMigration()
            .build()
        instance = this
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    companion object {
        lateinit var instance: PaperpilotApp
            private set
    }
}
