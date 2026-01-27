package com.gipogo.rhctools.domain

import kotlin.math.max
import kotlin.math.sqrt

object HemodynamicsFormulas {

    // O2 content (ml O2 / dL)
    // CaO2 = 1.36 * Hb * SaO2 + 0.0031 * PaO2
    // Nota: la parte disuelta (0.0031*PaO2) suele ser pequeña; la app permite incluirla o no.
    fun oxygenContentMlPerDl(
        hb_gDl: Double,
        sat_percent: Double,
        po2_mmHg: Double?,
        includeDissolved: Boolean
    ): Double {
        val sat = sat_percent / 100.0
        val bound = 1.36 * hb_gDl * sat
        val dissolved = if (includeDissolved && po2_mmHg != null) 0.0031 * po2_mmHg else 0.0
        return bound + dissolved
    }

    // VO2 estimado en reposo: ~125 mL/min/m2 * BSA (StatPearls)
    fun estimatedVo2MlMin(bsa_m2: Double, factor_mlMinM2: Double = 125.0): Double {
        return factor_mlMinM2 * bsa_m2
    }

    // Fick: CO (L/min) = VO2 (mL/min) / [(CaO2 - CvO2) (mL/dL) * 10]
    fun fickCardiacOutput(
        vo2_mlMin: Double,
        caO2_mlPerDl: Double,
        cvO2_mlPerDl: Double,
        bsa_m2: Double?
    ): FickResult {
        val avDiff = caO2_mlPerDl - cvO2_mlPerDl
        val safeAvDiff = max(avDiff, 0.000001) // evita división por 0
        val co = vo2_mlMin / (safeAvDiff * 10.0)
        val ci = bsa_m2?.let { co / it }
        return FickResult(
            cardiacOutputLMin = co,
            cardiacIndexLMinM2 = ci,
            caO2_mlPerDl = caO2_mlPerDl,
            cvO2_mlPerDl = cvO2_mlPerDl,
            avDiff_mlPerDl = avDiff
        )
    }

    // PVR (Wood Units) = (mPAP - PCWP)/CO ; dyn = WU*80
    fun pulmonaryVascularResistance(
        meanPap_mmHg: Double,
        pcwp_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        val wu = (meanPap_mmHg - pcwp_mmHg) / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // SVR (dyn·s·cm−5) = 80*(MAP - RAP)/CO ; WU = (MAP - RAP)/CO
    fun systemicVascularResistance(
        map_mmHg: Double,
        rap_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        val wu = (map_mmHg - rap_mmHg) / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // CPO (W) = (MAP * CO)/451
    fun cardiacPowerOutput(
        map_mmHg: Double,
        cardiacOutput_LMin: Double,
        bsa_m2: Double?
    ): CpoResult {
        val cpo = (map_mmHg * cardiacOutput_LMin) / 451.0
        val cpi = bsa_m2?.let { (map_mmHg * (cardiacOutput_LMin / it)) / 451.0 }
        return CpoResult(cpoWatts = cpo, cpiWattsPerM2 = cpi)
    }

    // PAPi = (PASP - PADP)/RAP
    fun papi(
        pasp_mmHg: Double,
        padp_mmHg: Double,
        rap_mmHg: Double
    ): PapiResult {
        val safeRap = max(rap_mmHg, 0.000001)
        val value = (pasp_mmHg - padp_mmHg) / safeRap
        return PapiResult(papi = value)
    }

    // -------------------------------------------------------------------------
    // ✅ NUEVO: Resistencias pulmonares "tipo QxMD": PVR + TPR en un solo cálculo
    // -------------------------------------------------------------------------

    data class PulmonaryResistanceResult(
        val gradientMmhg: Double,  // mPAP - PAWP
        val pvrWu: Double?,        // null si gradiente <= 0
        val pvrDynes: Double?,     // null si gradiente <= 0
        val tprWu: Double,         // siempre calculable si CO > 0
        val tprDynes: Double       // siempre calculable si CO > 0
    )

    /**
     * Devuelve simultáneamente:
     * - PVR = (mPAP - PAWP)/CO
     * - TPR = mPAP/CO
     *
     * Regla:
     * - Requiere CO > 0
     * - Si (mPAP - PAWP) <= 0 → PVR = null, pero TPR se calcula.
     */
    fun pulmonaryResistanceWithTpr(
        mpap_mmHg: Double,
        pawp_mmHg: Double,
        cardiacOutput_LMin: Double
    ): PulmonaryResistanceResult {
        require(cardiacOutput_LMin > 0.0) { "Cardiac output must be > 0" }

        val gradient = mpap_mmHg - pawp_mmHg

        val tprWu = mpap_mmHg / cardiacOutput_LMin
        val tprDyn = tprWu * 80.0

        return if (gradient <= 0.0) {
            PulmonaryResistanceResult(
                gradientMmhg = gradient,
                pvrWu = null,
                pvrDynes = null,
                tprWu = tprWu,
                tprDynes = tprDyn
            )
        } else {
            val pvrWu = gradient / cardiacOutput_LMin
            PulmonaryResistanceResult(
                gradientMmhg = gradient,
                pvrWu = pvrWu,
                pvrDynes = pvrWu * 80.0,
                tprWu = tprWu,
                tprDynes = tprDyn
            )
        }
    }

    /**
     * Solo TPR (Total Pulmonary Resistance):
     * TPR (WU) = mPAP / CO ; dyn = WU*80
     */
    fun totalPulmonaryResistance(
        mpap_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        require(cardiacOutput_LMin > 0.0) { "Cardiac output must be > 0" }
        val wu = mpap_mmHg / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // -------------------------------------------------------------------------
    // ✅ NUEVO: Derivados útiles (usados por tus screens)
    // -------------------------------------------------------------------------

    /**
     * Stroke Volume (mL/beat) = CO(L/min) * 1000 / HR(bpm)
     */
    fun strokeVolumeMlBeat(co_LMin: Double, hr_bpm: Double): Double {
        require(co_LMin >= 0.0) { "Cardiac output must be >= 0" }
        val safeHr = max(hr_bpm, 0.000001)
        return (co_LMin * 1000.0) / safeHr
    }

    /**
     * Pulmonary Artery Pulse Pressure (PAPP) = PASP - PADP (mmHg)
     */
    fun pulmonaryArteryPulsePressure(
        pasp_mmHg: Double,
        padp_mmHg: Double
    ): Double = pasp_mmHg - padp_mmHg

    /**
     * BSA (Mosteller) = sqrt( (heightCm * weightKg) / 3600 )
     */
    fun bsaMosteller(heightCm: Double, weightKg: Double): Double {
        require(heightCm > 0.0) { "Height must be > 0" }
        require(weightKg > 0.0) { "Weight must be > 0" }
        return sqrt((heightCm * weightKg) / 3600.0)
    }
    // -------------------------------------------------------------------------
    // ✅ NUEVO: Estimaciones y “puentes” clínicos para reutilización entre pantallas
    // -------------------------------------------------------------------------

    /**
     * mPAP estimada a partir de PASP y PADP.
     * Fórmula clásica: mPAP ≈ (PASP + 2*PADP) / 3
     *
     * Útil cuando solo tienes PASP/PADP (por ejemplo, ya capturados en PAPi)
     * y necesitas mPAP para PVR.
     */
    fun meanPulmonaryArteryPressureFromSystolicDiastolic(
        pasp_mmHg: Double,
        padp_mmHg: Double
    ): Double {
        return (pasp_mmHg + 2.0 * padp_mmHg) / 3.0
    }

    /**
     * En la práctica CVP (presión venosa central) ≈ RAP (presión auricular derecha).
     * Esta función NO cambia el número, solo formaliza la equivalencia semántica.
     *
     * Se recomienda usarla bajo acción explícita del usuario (botón “usar CVP como RAP”),
     * no automática.
     */
    fun rapFromCvp(cvp_mmHg: Double): Double = cvp_mmHg

    /**
     * Pulmonary Artery Mean Pressure “de una vez”:
     * - Si tienes mPAP medido, úsalo.
     * - Si no, y tienes PASP+PADP, estima.
     *
     * Devuelve Pair(valor, wasEstimated)
     */
    fun mpapMeasuredOrEstimated(
        mpapMeasured_mmHg: Double?,
        pasp_mmHg: Double?,
        padp_mmHg: Double?
    ): Pair<Double?, Boolean> {
        if (mpapMeasured_mmHg != null) return mpapMeasured_mmHg to false
        if (pasp_mmHg != null && padp_mmHg != null) {
            return meanPulmonaryArteryPressureFromSystolicDiastolic(pasp_mmHg, padp_mmHg) to true
        }
        return null to false
    }
    // -------------------------------------------------------------------------
    // ✅ NUEVO: Termodilución (TD)
    // -------------------------------------------------------------------------

    /**
     * Termodilución práctica para tu UI:
     * - El usuario introduce 1–3 corridas (L/min) y se calcula el promedio.
     *
     * Reglas:
     * - Requiere al menos 1 valor > 0
     */
    fun thermodilutionAverageCardiacOutputLMin(runs_LMin: List<Double>): Double {
        val valid = runs_LMin.filter { it.isFinite() && it > 0.0 }
        require(valid.isNotEmpty()) { "At least one thermodilution run must be > 0" }
        return valid.average()
    }

    /**
     * Stewart–Hamilton (conceptual/avanzado):
     *
     * CO = (V_i * (T_b - T_i) * K) / ∫ΔT(t) dt
     *
     * - V_i: volumen de inyectado (mL)
     * - (T_b - T_i): diferencia de temperatura (°C)
     * - K: constante (densidad/calor específico, etc.) depende del sistema/monitor
     * - integralDegreeCSeconds: área bajo la curva de termodilución (°C·s)
     *
     * Nota: tu UI actual NO captura el integral; por ahora usa el promedio.
     */
    fun stewartHamiltonThermodilutionCardiacOutputLMin(
        injectateVolumeMl: Double,
        deltaTempC: Double,
        k: Double,
        integralDegreeCSeconds: Double
    ): Double {
        require(injectateVolumeMl > 0.0) { "Injectate volume must be > 0" }
        require(deltaTempC > 0.0) { "Delta temperature must be > 0" }
        require(k > 0.0) { "K must be > 0" }
        require(integralDegreeCSeconds > 0.0) { "Integral must be > 0" }

        // Resultado en mL/s -> convertir a L/min:
        // (mL/s) * (60 s/min) / (1000 mL/L) = * 0.06
        val coMlPerSec = (injectateVolumeMl * deltaTempC * k) / integralDegreeCSeconds
        return coMlPerSec * 0.06
    }


}
