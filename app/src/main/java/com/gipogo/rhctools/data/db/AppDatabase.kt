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
import java.io.File
import java.nio.charset.StandardCharsets

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
                    val mayReplaceUnusableKeyMaterial = mayReplaceUnusableKeyMaterial(dbFile)
                    if (!mayReplaceUnusableKeyMaterial &&
                        !DbKeyStore.hasStoredPassphraseMaterial(appContext)
                    ) {
                        throw com.gipogo.rhctools.data.security.DbKeyStoreException(
                            "Existe una base clínica cifrada, pero falta el material protegido " +
                                "necesario para abrirla; el archivo se conservó sin cambios"
                        )
                    }
                    val passphraseText = DbKeyStore.getOrCreatePassphraseText(
                        context = appContext,
                        mayReplaceUnusableMaterial = mayReplaceUnusableKeyMaterial,
                        allowCreationIfMissing = mayReplaceUnusableKeyMaterial
                    )
                    DbEncryptionMigrator.ensureEncrypted(
                        dbFile = dbFile,
                        passphraseText = passphraseText
                    )

                    val instance = openDatabase(
                        passphraseText = passphraseText,
                        appContext = appContext
                    )

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

        private fun mayReplaceUnusableKeyMaterial(dbFile: File): Boolean {
            val temp = File(dbFile.parentFile, dbFile.name + ".enc_tmp")
            val backup = File(dbFile.parentFile, dbFile.name + ".bak_plain")
            val protectedArtifactExists = listOf(dbFile, temp, backup).any { file ->
                file.exists() && file.length() > 0L && !DbEncryptionMigrator.isPlaintextDatabase(file)
            }
            return !protectedArtifactExists
        }

        private fun openDatabase(
            passphraseText: String,
            appContext: Context
        ): AppDatabase {
            val factory = SupportOpenHelperFactory(
                passphraseText.toByteArray(StandardCharsets.UTF_8)
            )
            val instance = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_8_9)
                .openHelperFactory(factory)
                .build()
            try {
                // Room opens lazily. Force validation here so the UI receives
                // the real open failure instead of a later repository error.
                instance.openHelper.writableDatabase
                return instance
            } catch (error: Throwable) {
                instance.close()
                throw error
            }
        }
    }
}
