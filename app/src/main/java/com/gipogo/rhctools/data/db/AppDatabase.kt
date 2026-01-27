package com.gipogo.rhctools.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.StudyDao
import com.gipogo.rhctools.data.db.dao.TagDao
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.PatientTagCrossRef
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.db.entities.TagEntity

@Database(
    entities = [
        PatientEntity::class,
        StudyEntity::class,
        TagEntity::class,
        PatientTagCrossRef::class,
        RhcStudyDataEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun studyDao(): StudyDao
    abstract fun tagDao(): TagDao
    abstract fun rhcStudyDao(): RhcStudyDao

    companion object {
        private const val DB_NAME = "gipogo_rhc_tools.db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // en dev tú ya dijiste destructive (version=8 bump)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
