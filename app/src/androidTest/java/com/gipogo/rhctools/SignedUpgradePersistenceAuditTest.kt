package com.gipogo.rhctools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.data.db.AppDatabase
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Auditoría manual en dos procesos de instrumentación, destinada a probar una
 * actualización firmada real. No contiene limpieza automática entre fases.
 */
@RunWith(AndroidJUnit4::class)
class SignedUpgradePersistenceAuditTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun phase1SeedClinicalDataBeforeSignedUpgrade() = runBlocking {
        val result = DbProvider.getResult(context)
        assertTrue(result is DbProvider.DbOpenResult.Success)
        val db = (result as DbProvider.DbOpenResult.Success).db
        val now = 1_787_000_000_000L

        if (db.patientDao().getById(PATIENT_ID) == null) {
            db.patientDao().insert(
                PatientEntity(
                    id = PATIENT_ID,
                    internalCode = PATIENT_CODE,
                    displayName = "Paciente actualización firmada",
                    sex = "F",
                    birthDateMillis = 536_457_600_000L,
                    weightKg = 64.0,
                    heightCm = 162.0,
                    notes = "DATOS FICTICIOS - auditoría de persistencia",
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            db.studyDao().insert(
                StudyEntity(
                    id = STUDY_ID,
                    patientId = PATIENT_ID,
                    type = "RHC",
                    startedAtMillis = now,
                    endedAtMillis = now + 1_800_000L,
                    notes = STUDY_NOTE,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            db.rhcStudyDao().insert(
                RhcStudyDataEntity(
                    id = RHC_ID,
                    studyId = STUDY_ID,
                    weightKg = 64.0,
                    heightCm = 162.0,
                    bsaM2 = 1.69,
                    saO2Percent = 97.0,
                    svO2Percent = 66.0,
                    hemoglobinGdl = 12.8,
                    heartRateBpm = 76.0,
                    mapMmHg = 84.0,
                    rapMmHg = 8.0,
                    paspMmHg = 42.0,
                    padpMmHg = 17.0,
                    mpapMmHg = 27.0,
                    pawpMmHg = 15.0,
                    cardiacOutputLMin = 4.8,
                    cardiacIndexLMinM2 = 2.84,
                    cardiacOutputSelectedLMin = 4.8,
                    cardiacIndexSelectedLMinM2 = 2.84,
                    coSelectedMethod = "TD",
                    coSelectionReason = "USER_SELECTED",
                    pvrWood = 2.5,
                    cardiacPowerW = 0.90,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        assertClinicalFixture(db)
    }

    @Test
    fun phase2VerifyClinicalDataAfterSignedUpgrade() = runBlocking {
        val result = DbProvider.getResult(context)
        assertTrue(
            (result as? DbProvider.DbOpenResult.Failure)?.diagnostic?.technicalDetail,
            result is DbProvider.DbOpenResult.Success
        )
        val db = (result as DbProvider.DbOpenResult.Success).db

        assertClinicalFixture(db)
        db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9, cursor.getInt(0))
        }
    }

    private suspend fun assertClinicalFixture(db: AppDatabase) {
        val patient = db.patientDao().getById(PATIENT_ID)
        val study = db.studyDao().getById(STUDY_ID)
        val rhc = db.rhcStudyDao().getByStudyId(STUDY_ID)
        assertNotNull(patient)
        assertNotNull(study)
        assertNotNull(rhc)
        assertEquals(PATIENT_CODE, patient?.internalCode)
        assertEquals(STUDY_NOTE, study?.notes)
        assertEquals(27.0, rhc?.mpapMmHg ?: 0.0, 0.0001)
        assertEquals(15.0, rhc?.pawpMmHg ?: 0.0, 0.0001)
        assertEquals(4.8, rhc?.cardiacOutputSelectedLMin ?: 0.0, 0.0001)
        assertEquals(2.5, rhc?.pvrWood ?: 0.0, 0.0001)
    }

    private companion object {
        const val PATIENT_ID = "signed-upgrade-audit-patient"
        const val PATIENT_CODE = "AUD-UPGRADE-SIGNED"
        const val STUDY_ID = "signed-upgrade-audit-study"
        const val RHC_ID = "signed-upgrade-audit-rhc"
        const val STUDY_NOTE = "Persistencia entre APK firmados"
    }
}
