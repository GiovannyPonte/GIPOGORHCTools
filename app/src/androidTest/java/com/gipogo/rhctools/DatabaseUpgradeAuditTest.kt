package com.gipogo.rhctools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.data.db.AppDatabase
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.security.DbKeyStore
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeAuditTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile get() = context.getDatabasePath(DB_NAME)

    @Before
    fun prepare() = reset()

    @After
    fun cleanup() = reset()

    @Test
    fun publishedSchema8PlaintextUpgradesToEncryptedSchema9WithoutLosingClinicalRows() = runBlocking {
        createPublishedSchema8Database()

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Success)
        val db = (result as DbProvider.DbOpenResult.Success).db
        val patient = db.patientDao().getById(PATIENT_ID)
        val study = db.studyDao().getById(STUDY_ID)
        val rhc = db.rhcStudyDao().getByStudyId(STUDY_ID)
        assertNotNull(patient)
        assertNotNull(study)
        assertNotNull(rhc)
        assertEquals("UPGRADE-8", patient?.internalCode)
        assertEquals(4.7, rhc?.cardiacOutputLMin ?: 0.0, 0.0001)
        assertEquals(4.7, rhc?.cardiacOutputSelectedLMin ?: 0.0, 0.0001)
        assertEquals(2.35, rhc?.cardiacIndexSelectedLMinM2 ?: 0.0, 0.0001)
        assertEquals("FICK", rhc?.coSelectedMethod)
        assertEquals("LEGACY_MIGRATION", rhc?.coSelectionReason)

        db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9, cursor.getInt(0))
        }
        assertTrue(dbFile.exists())
        assertTrue(dbFile.inputStream().use { input ->
            val header = ByteArray(16)
            input.read(header)
            !header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        })
    }

    @Test
    fun plaintextSchema8WithIncompleteNewKeyMaterialRepairsKeyAndPreservesUpgradeData() = runBlocking {
        createPublishedSchema8Database()
        context.getSharedPreferences("db_key_material_v2", Context.MODE_PRIVATE)
            .edit()
            .putInt("format_version", 2)
            .putString("wrapped_passphrase_b64", "incomplete")
            .commit()

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Success)
        val db = (result as DbProvider.DbOpenResult.Success).db
        assertEquals("UPGRADE-8", db.patientDao().getById(PATIENT_ID)?.internalCode)
        assertEquals(4.7, db.rhcStudyDao().getByStudyId(STUDY_ID)?.cardiacOutputSelectedLMin ?: 0.0, 0.0001)
    }

    @Test
    fun databaseFromNewerAppFailsAsDowngradeAndPreservesClinicalRow() = runBlocking {
        val passphrase = createCurrentEncryptedDatabaseWithPatient("DOWNGRADE-AUDIT")
        setEncryptedUserVersion(passphrase, 10)

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Failure)
        val failure = result as DbProvider.DbOpenResult.Failure
        assertEquals("DB-SCHEMA-DOWNGRADE-01", failure.diagnostic.code)
        assertEquals("DOWNGRADE-AUDIT", readEncryptedPatientCode(passphrase))
    }

    @Test
    fun unsupportedOlderSchemaFailsWithoutDestructiveFallbackAndPreservesClinicalRow() = runBlocking {
        val passphrase = createCurrentEncryptedDatabaseWithPatient("MISSING-MIGRATION")
        setEncryptedUserVersion(passphrase, 7)

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Failure)
        val failure = result as DbProvider.DbOpenResult.Failure
        assertEquals("DB-SCHEMA-01", failure.diagnostic.code)
        assertEquals("MISSING-MIGRATION", readEncryptedPatientCode(passphrase))
    }

    @Test
    fun changedSchemaWithoutVersionBumpFailsClosedAndPreservesClinicalRow() = runBlocking {
        val passphrase = createCurrentEncryptedDatabaseWithPatient("IDENTITY-MISMATCH")
        CipherSQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            CipherSQLiteDatabase.OPEN_READWRITE,
            null,
            null
        ).use { db ->
            db.execSQL(
                "UPDATE room_master_table SET identity_hash=? WHERE id=42",
                arrayOf("unexpected-schema-identity")
            )
        }

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Failure)
        val failure = result as DbProvider.DbOpenResult.Failure
        assertEquals("DB-SCHEMA-01", failure.diagnostic.code)
        assertEquals("IDENTITY-MISMATCH", readEncryptedPatientCode(passphrase))
    }

    private suspend fun createCurrentEncryptedDatabaseWithPatient(code: String): String {
        val db = AppDatabase.getInstance(context)
        val now = 1_723_456_789_000L
        db.patientDao().insert(
            com.gipogo.rhctools.data.db.entities.PatientEntity(
                id = PATIENT_ID,
                internalCode = code,
                displayName = "Persistir",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        val passphrase = DbKeyStore.getOrCreatePassphraseText(context)
        AppDatabase.clearInstance()
        return passphrase
    }

    private fun setEncryptedUserVersion(passphrase: String, version: Int) {
        CipherSQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            CipherSQLiteDatabase.OPEN_READWRITE,
            null,
            null
        ).use { db -> db.rawExecSQL("PRAGMA user_version=$version") }
    }

    private fun readEncryptedPatientCode(passphrase: String): String =
        CipherSQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            CipherSQLiteDatabase.OPEN_READONLY,
            null,
            null
        ).use { db ->
            db.rawQuery("SELECT internalCode FROM patients WHERE id=?", arrayOf(PATIENT_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
        }

    private fun createPublishedSchema8Database() {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("CREATE TABLE patients (id TEXT NOT NULL PRIMARY KEY, internalCode TEXT NOT NULL, displayName TEXT, sex TEXT, birthDateMillis INTEGER, weightKg REAL, heightCm REAL, notes TEXT, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_patients_internalCode ON patients(internalCode)")
            db.execSQL("CREATE TABLE studies (id TEXT NOT NULL PRIMARY KEY, patientId TEXT NOT NULL, type TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, endedAtMillis INTEGER, notes TEXT, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(patientId) REFERENCES patients(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_studies_patientId ON studies(patientId)")
            db.execSQL("CREATE INDEX index_studies_startedAtMillis ON studies(startedAtMillis)")
            db.execSQL("CREATE TABLE tags (`key` TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("CREATE TABLE patient_tags (patientId TEXT NOT NULL, tagKey TEXT NOT NULL, PRIMARY KEY(patientId, tagKey), FOREIGN KEY(patientId) REFERENCES patients(id) ON DELETE CASCADE, FOREIGN KEY(tagKey) REFERENCES tags(`key`) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_patient_tags_patientId ON patient_tags(patientId)")
            db.execSQL("CREATE INDEX index_patient_tags_tagKey ON patient_tags(tagKey)")
            db.execSQL(
                "CREATE TABLE rhc_study_data (" +
                    "id TEXT NOT NULL PRIMARY KEY, studyId TEXT NOT NULL, weightKg REAL, heightCm REAL, bsaM2 REAL, " +
                    "saO2Percent REAL, svO2Percent REAL, hemoglobinGdl REAL, heartRateBpm REAL, vo2MlMin REAL, vo2Mode TEXT, " +
                    "mapMmHg REAL, rapMmHg REAL, paspMmHg REAL, padpMmHg REAL, mpapMmHg REAL, pawpMmHg REAL, " +
                    "cardiacOutputLMin REAL, cardiacIndexLMinM2 REAL, svrWood REAL, svrDyn REAL, pvrWood REAL, pvrDyn REAL, " +
                    "papi REAL, cardiacPowerW REAL, cardiacPowerIndexWm2 REAL, svrUnits TEXT, pvrUnits TEXT, coMethod TEXT, " +
                    "createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, " +
                    "FOREIGN KEY(studyId) REFERENCES studies(id) ON DELETE CASCADE)"
            )
            db.execSQL("CREATE UNIQUE INDEX index_rhc_study_data_studyId ON rhc_study_data(studyId)")
            db.execSQL("CREATE INDEX index_rhc_study_data_updatedAtMillis ON rhc_study_data(updatedAtMillis)")

            val now = 1_723_456_789_000L
            db.execSQL("INSERT INTO patients VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(PATIENT_ID, "UPGRADE-8", "Paciente Actualización", "F", 315532800000L, 68.0, 164.0, "Conservar", now, now))
            db.execSQL("INSERT INTO studies VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(STUDY_ID, PATIENT_ID, "RHC", now, now, "Estudio previo", now, now))
            db.execSQL(
                "INSERT INTO rhc_study_data (id, studyId, weightKg, heightCm, bsaM2, saO2Percent, svO2Percent, hemoglobinGdl, heartRateBpm, mapMmHg, rapMmHg, paspMmHg, padpMmHg, mpapMmHg, pawpMmHg, cardiacOutputLMin, cardiacIndexLMinM2, pvrWood, coMethod, createdAtMillis, updatedAtMillis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("rhc-v8", STUDY_ID, 68.0, 164.0, 1.82, 97.0, 66.0, 12.5, 78.0, 82.0, 8.0, 42.0, 18.0, 27.0, 15.0, 4.7, 2.35, 2.55, "FICK", now, now)
            )
            db.execSQL("PRAGMA user_version=8")
        }
    }

    private fun reset() {
        AppDatabase.clearInstance()
        context.deleteDatabase(DB_NAME)
        DbKeyStore.clear(context)
    }

    private companion object {
        const val DB_NAME = "gipogo_rhc_tools.db"
        const val PATIENT_ID = "patient-schema-8"
        const val STUDY_ID = "study-schema-8"
    }
}
