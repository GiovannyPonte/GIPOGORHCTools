package com.gipogo.rhctools.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.StudyDao
import com.gipogo.rhctools.data.db.dao.TagDao
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.PatientTagCrossRef
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.db.entities.TagEntity
import com.gipogo.rhctools.data.security.DbEncryptionMigrator
import com.gipogo.rhctools.data.security.DbKeyStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PatientEntity::class,
        StudyEntity::class,
        TagEntity::class,
        PatientTagCrossRef::class,
        RhcStudyDataEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun patientDao(): PatientDao
    abstract fun studyDao(): StudyDao
    abstract fun tagDao(): TagDao
    abstract fun rhcStudyDao(): RhcStudyDao

    companion object {
        private const val DB_NAME = "gipogo_rhc_tools.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacOutputFickLMin REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacIndexFickLMinM2 REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacOutputTdLMin REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacIndexTdLMinM2 REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacOutputSelectedLMin REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN cardiacIndexSelectedLMinM2 REAL")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN coSelectedMethod TEXT")
                db.execSQL("ALTER TABLE rhc_study_data ADD COLUMN coSelectionReason TEXT")

                db.execSQL(
                    "UPDATE rhc_study_data " +
                            "SET cardiacOutputSelectedLMin = cardiacOutputLMin " +
                            "WHERE cardiacOutputSelectedLMin IS NULL AND cardiacOutputLMin IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE rhc_study_data " +
                            "SET cardiacIndexSelectedLMinM2 = cardiacIndexLMinM2 " +
                            "WHERE cardiacIndexSelectedLMinM2 IS NULL AND cardiacIndexLMinM2 IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE rhc_study_data " +
                            "SET coSelectedMethod = coMethod " +
                            "WHERE coSelectedMethod IS NULL AND coMethod IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE rhc_study_data " +
                            "SET coSelectionReason = 'LEGACY_MIGRATION' " +
                            "WHERE coSelectionReason IS NULL AND cardiacOutputLMin IS NOT NULL"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext

                    val dbFile = appContext.getDatabasePath(DB_NAME)
                    val dbBuilder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .addMigrations(MIGRATION_8_9)
                    val passphraseText = DbKeyStore.getOrCreatePassphraseText(appContext)
                    DbEncryptionMigrator.ensureEncrypted(
                        dbFile = dbFile,
                        passphraseText = passphraseText
                    )

                    val factory = SupportOpenHelperFactory(
                        DbKeyStore.getOrCreatePassphraseBytes(appContext)
                    )
                    val instance = dbBuilder
                        .openHelperFactory(factory)
                        .build()

                    // Room opens lazily. Force verification here so callers get a
                    // typed open failure instead of a later crash in a repository.
                    try {
                        instance.openHelper.writableDatabase
                    } catch (error: Throwable) {
                        instance.close()
                        throw error
                    }

                    instance.also { INSTANCE = it }
                }
            }
        }

        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
