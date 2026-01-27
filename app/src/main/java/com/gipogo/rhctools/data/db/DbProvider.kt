package com.gipogo.rhctools.data.db

import android.content.Context
import androidx.room.Room

object DbProvider {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rhctools.db"
            )
                // ✅ DEV: destruye y recrea si cambia el schema (evita “migration required”)
                .fallbackToDestructiveMigration(true)
                .build()
                .also { INSTANCE = it }
        }
    }
}
