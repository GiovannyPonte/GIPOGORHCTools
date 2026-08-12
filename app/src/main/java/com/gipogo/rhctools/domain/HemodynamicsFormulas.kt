package com.gipogo.rhctools.domain

import kotlin.math.sqrt

object HemodynamicsFormulas {

    // O2 content (mL O2 / dL)
    // CaO2 = 1.36 * Hb * SaO2 + 0.0031 * PaO2
    // Nota: la parte disuelta (0.0031*PaO2) suele ser pequeña; la app permite incluirla o no.
    fun oxygenContentMlPerDl(
        hb_gDl: Double,
        sat_percent: Double,
        po2_mmHg: Double?,
        includeDissolved: Boolean
    ): Double {
        // Fail-clean: si inputs no son finitos, no “inventar” contenido de O2.
        if (!hb_gDl.isFinite() || !sat_percent.isFinite()) return Double.NaN

        // A.6.1: sat_percent se espera en % (0–100). Fuera de rango → fail-clean.
        if (sat_percent < 0.0 || sat_percent > 100.0) return Double.NaN

        // Si se incluye oxígeno disuelto, PO2 debe ser finita y no negativa.
        if (includeDissolved) {
            if (po2_mmHg == null || !po2_mmHg.isFinite() || po2_mmHg < 0.0) return Double.NaN
        }

        val sat = sat_percent / 100.0
        val bound = 1.36 * hb_gDl * sat
        val dissolved = if (includeDissolved) 0.0031 * po2_mmHg!! else 0.0
        return bound + dissolved
    }


    // VO2 estimado en reposo: ~125 mL/min/m2 * BSA (StatPearls)
    fun estimatedVo2MlMin(bsa_m2: Double, factor_mlMinM2: Double = 125.0): Double {
        // Fail-clean: requiere inputs finitos y BSA > 0.
        if (!bsa_m2.isFinite() || !factor_mlMinM2.isFinite()) return Double.NaN
        if (bsa_m2 <= 0.0 || factor_mlMinM2 <= 0.0) return Double.NaN
        return factor_mlMinM2 * bsa_m2
    }

    // Fick: CO (L/min) = VO2 (mL/min) / [(CaO2 - CvO2) (mL/dL) * 10]
    fun fickCardiacOutput(
        vo2_mlMin: Double,
        caO2_mlPerDl: Double,
        cvO2_mlPerDl: Double,
        bsa_m2: Double?
    ): FickResult {

        // Fail-clean: si VO2 o contenidos no son finitos, no calcular.
        if (!vo2_mlMin.isFinite() || !caO2_mlPerDl.isFinite() || !cvO2_mlPerDl.isFinite()) {
            return FickResult(
                cardiacOutputLMin = Double.NaN,
                cardiacIndexLMinM2 = null,
                caO2_mlPerDl = caO2_mlPerDl,
                cvO2_mlPerDl = cvO2_mlPerDl,
                avDiff_mlPerDl = Double.NaN
            )
        }
        if (vo2_mlMin <= 0.0) {
            return FickResult(
                cardiacOutputLMin = Double.NaN,
                cardiacIndexLMinM2 = null,
                caO2_mlPerDl = caO2_mlPerDl,
                cvO2_mlPerDl = cvO2_mlPerDl,
                avDiff_mlPerDl = caO2_mlPerDl - cvO2_mlPerDl
            )
        }

        val avDiff = caO2_mlPerDl - cvO2_mlPerDl

        // Fail-fast fisiológico: sin gradiente válido no existe CO por Fick
        if (avDiff <= 0.0) {
            return FickResult(
                cardiacOutputLMin = Double.NaN,
                cardiacIndexLMinM2 = null,
                caO2_mlPerDl = caO2_mlPerDl,
                cvO2_mlPerDl = cvO2_mlPerDl,
                avDiff_mlPerDl = avDiff
            )
        }

        val co = vo2_mlMin / (avDiff * 10.0)
        val ci = bsa_m2?.takeIf { it.isFinite() && it > 0.0 }?.let { co / it }

        return FickResult(
            cardiacOutputLMin = co,
            cardiacIndexLMinM2 = ci,
            caO2_mlPerDl = caO2_mlPerDl,
            cvO2_mlPerDl = cvO2_mlPerDl,
            avDiff_mlPerDl = avDiff
        )
    }

    // PVR (Wood Units) = (mPAP - PCWP)/CO ; dyn·s·cm⁻⁵ = WU × 80
    fun pulmonaryVascularResistance(
        meanPap_mmHg: Double,
        pcwp_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        // Fail-clean: inputs no finitos o CO <= 0 → no calcular resistencia.
        if (!meanPap_mmHg.isFinite() || !pcwp_mmHg.isFinite()) {
            return ResistanceResult(woodUnits = Double.NaN, dynesSecCm5 = Double.NaN)
        }
        if (!cardiacOutput_LMin.isFinite() || cardiacOutput_LMin <= 0.0) {
            return ResistanceResult(woodUnits = Double.NaN, dynesSecCm5 = Double.NaN)
        }

        // Fórmula estándar: PVR (WU) = (mPAP - PCWP) / CO. La conversión a dyn·s·cm⁻⁵ es WU × 80.
        val wu = (meanPap_mmHg - pcwp_mmHg) / cardiacOutput_LMin
        // A.6.2: Conversión estándar: 1 Wood Unit = 80 dyn·s·cm⁻⁵

        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // SVR (Wood Units) = (MAP - RAP)/CO ; dyn·s·cm⁻⁵ = WU × 80
    fun systemicVascularResistance(
        map_mmHg: Double,
        rap_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {

        // Fail-clean: CO inválido → no calcular resistencia.
        if (!cardiacOutput_LMin.isFinite() || cardiacOutput_LMin <= 0.0) {
            return ResistanceResult(woodUnits = Double.NaN, dynesSecCm5 = Double.NaN)
        }
        if (!map_mmHg.isFinite() || !rap_mmHg.isFinite()) {
            return ResistanceResult(woodUnits = Double.NaN, dynesSecCm5 = Double.NaN)
        }

        val wu = (map_mmHg - rap_mmHg) / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // CPO (W) = (MAP * CO)/451
    fun cardiacPowerOutput(
        map_mmHg: Double,
        cardiacOutput_LMin: Double,
        bsa_m2: Double?
    ): CpoResult {
        // Fail-clean: si MAP/CO no son finitos, devolver NaN (no excepción).
        if (!map_mmHg.isFinite() || !cardiacOutput_LMin.isFinite()) {
            return CpoResult(cpoWatts = Double.NaN, cpiWattsPerM2 = null)
        }
// A.6.2: CPO(W) = (MAP[mmHg] * CO[L/min]) / 451  (constante de conversión a Watts)
        val cpo = (map_mmHg * cardiacOutput_LMin) / 451.0

        val cpi = bsa_m2
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (map_mmHg * (cardiacOutput_LMin / it)) / 451.0 }

        return CpoResult(cpoWatts = cpo, cpiWattsPerM2 = cpi)
    }

    // PAPi = (PASP - PADP)/RAP
    fun papi(
        pasp_mmHg: Double,
        padp_mmHg: Double,
        rap_mmHg: Double
    ): PapiResult {

        // Fail-clean: RAP <= 0 o inputs no finitos → NaN.
        if (!pasp_mmHg.isFinite() || !padp_mmHg.isFinite() || !rap_mmHg.isFinite()) {
            return PapiResult(papi = Double.NaN)
        }
        if (rap_mmHg <= 0.0) {
            return PapiResult(papi = Double.NaN)
        }

        val value = (pasp_mmHg - padp_mmHg) / rap_mmHg
        return PapiResult(papi = value)
    }

    // -------------------------------------------------------------------------
    // Resistencias pulmonares: PVR + TPR en un solo cálculo (fail-clean)
    // -------------------------------------------------------------------------

    data class PulmonaryResistanceResult(
        val gradientMmhg: Double,  // mPAP - PAWP
        val pvrWu: Double?,        // null si gradiente <= 0
        val pvrDynes: Double?,     // null si gradiente <= 0
        val tprWu: Double,         // NaN si CO inválido
        val tprDynes: Double       // NaN si CO inválido
    )

    /**
     * Devuelve simultáneamente:
     * - PVR = (mPAP - PAWP)/CO
     * - TPR = mPAP/CO
     *
     * Regla:
     * - Requiere CO > 0 (fail-clean: si no, devuelve NaN)
     * - Si (mPAP - PAWP) <= 0 → PVR = null, pero TPR se calcula (si CO válido)
     */
    fun pulmonaryResistanceWithTpr(
        mpap_mmHg: Double,
        pawp_mmHg: Double,
        cardiacOutput_LMin: Double
    ): PulmonaryResistanceResult {

        if (!mpap_mmHg.isFinite() || !pawp_mmHg.isFinite() || !cardiacOutput_LMin.isFinite() || cardiacOutput_LMin <= 0.0) {
            return PulmonaryResistanceResult(
                gradientMmhg = Double.NaN,
                pvrWu = null,
                pvrDynes = null,
                tprWu = Double.NaN,
                tprDynes = Double.NaN
            )
        }

        val gradient = mpap_mmHg - pawp_mmHg

        val tprWu = mpap_mmHg / cardiacOutput_LMin
        // A.6.2: Conversión estándar: 1 Wood Unit = 80 dyn·s·cm⁻⁵
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
     * TPR (WU) = mPAP / CO ; dyn·s·cm⁻⁵ = WU × 80
     */
    fun totalPulmonaryResistance(
        mpap_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        if (!mpap_mmHg.isFinite() || !cardiacOutput_LMin.isFinite() || cardiacOutput_LMin <= 0.0) {
            return ResistanceResult(woodUnits = Double.NaN, dynesSecCm5 = Double.NaN)
        }
        val wu = mpap_mmHg / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // -------------------------------------------------------------------------
    // Derivados útiles
    // -------------------------------------------------------------------------

    /**
     * Stroke Volume (mL/beat) = CO(L/min) * 1000 / HR(bpm)
     * Fail-clean: no inventar resultados si HR o CO no son válidos.
     */
    fun strokeVolumeMlBeat(co_LMin: Double, hr_bpm: Double): Double {
        if (!co_LMin.isFinite() || co_LMin <= 0.0) return Double.NaN
        if (!hr_bpm.isFinite() || hr_bpm <= 0.0) return Double.NaN
        return (co_LMin * 1000.0) / hr_bpm
    }

    /**
     * Pulmonary Artery Pulse Pressure (PAPP) = PASP - PADP (mmHg)
     */
    fun pulmonaryArteryPulsePressure(
        pasp_mmHg: Double,
        padp_mmHg: Double
    ): Double {
        if (!pasp_mmHg.isFinite() || !padp_mmHg.isFinite()) return Double.NaN
        return pasp_mmHg - padp_mmHg
    }

    /**
     * BSA (Mosteller) = sqrt( (heightCm * weightKg) / 3600 )
     * Fail-clean: devuelve NaN si inputs inválidos.
     */
    fun bsaMosteller(heightCm: Double, weightKg: Double): Double {
        if (!heightCm.isFinite() || !weightKg.isFinite()) return Double.NaN
        if (heightCm <= 0.0 || weightKg <= 0.0) return Double.NaN
        return sqrt((heightCm * weightKg) / 3600.0)
    }

    // -------------------------------------------------------------------------
    // Estimaciones / puentes clínicos
    // -------------------------------------------------------------------------

    /**
     * mPAP estimada: mPAP ≈ (PASP + 2*PADP) / 3
     */
    fun meanPulmonaryArteryPressureFromSystolicDiastolic(
        pasp_mmHg: Double,
        padp_mmHg: Double
    ): Double {
        if (!pasp_mmHg.isFinite() || !padp_mmHg.isFinite()) return Double.NaN
        return (pasp_mmHg + 2.0 * padp_mmHg) / 3.0
    }

    /**
     * CVP ≈ RAP (solo equivalencia semántica).
     */
    fun rapFromCvp(cvp_mmHg: Double): Double {
        if (!cvp_mmHg.isFinite()) return Double.NaN
        return cvp_mmHg
    }

    /**
     * mPAP: si medido, úsalo; si no, estima con PASP+PADP.
     * Devuelve Pair(valor, wasEstimated)
     */
    fun mpapMeasuredOrEstimated(
        mpapMeasured_mmHg: Double?,
        pasp_mmHg: Double?,
        padp_mmHg: Double?
    ): Pair<Double?, Boolean> {
        if (mpapMeasured_mmHg != null) {
            return mpapMeasured_mmHg.takeIf { it.isFinite() } to false
        }
        if (pasp_mmHg != null && padp_mmHg != null) {
            val est = meanPulmonaryArteryPressureFromSystolicDiastolic(pasp_mmHg, padp_mmHg)
            return est.takeIf { it.isFinite() } to true
        }
        return null to false
    }

    // -------------------------------------------------------------------------
    // Termodilución
    // -------------------------------------------------------------------------

    /**
     * Promedio de corridas de termodilución (L/min).
     * Fail-clean: si no hay valores válidos, devuelve NaN (no excepción).
     */
    fun thermodilutionAverageCardiacOutputLMin(runs_LMin: List<Double>): Double {
        val valid = runs_LMin.filter { it.isFinite() && it > 0.0 }
        if (valid.isEmpty()) return Double.NaN
        return valid.average()
    }

    /**
     * Stewart–Hamilton (conceptual/avanzado)
     * Fail-clean: si inputs inválidos, devuelve NaN (no excepción).
     */
    fun stewartHamiltonThermodilutionCardiacOutputLMin(
        injectateVolumeMl: Double,
        deltaTempC: Double,
        k: Double,
        integralDegreeCSeconds: Double
    ): Double {
        if (!injectateVolumeMl.isFinite() || !deltaTempC.isFinite() || !k.isFinite() || !integralDegreeCSeconds.isFinite()) {
            return Double.NaN
        }
        if (injectateVolumeMl <= 0.0 || deltaTempC <= 0.0 || k <= 0.0 || integralDegreeCSeconds <= 0.0) {
            return Double.NaN
        }

        // Resultado en mL/s -> convertir a L/min:
        // (mL/s) * (60 s/min) / (1000 mL/L) = * 0.06
        val coMlPerSec = (injectateVolumeMl * deltaTempC * k) / integralDegreeCSeconds
        return coMlPerSec * 0.06
    }

    data class ThermodilutionDerivedResult(
        val cardiacOutputLMin: Double,
        val cardiacIndexLMinM2: Double?,   // null si BSA inválida/ausente
        val strokeVolumeMlBeat: Double?    // null si HR inválida/ausente
    )

    /**
     * Termodilución (TD): calcula promedio de corridas + derivados (CI, SV).
     *
     * Reglas (fail-clean):
     * - Requiere al menos 1 corrida finita y > 0.
     * - BSA opcional: si no es finita o <= 0 -> CI = null.
     * - HR opcional: si no es finita o <= 0 -> SV = null.
     * - Si algo no es válido, devuelve NaN en CO (para canalizar error arriba).
     */
    fun thermodilutionDerived(
        runs_LMin: List<Double>,
        bsa_m2: Double?,
        hr_bpm: Double?
    ): ThermodilutionDerivedResult {
        val co = thermodilutionAverageCardiacOutputLMin(runs_LMin)
        if (!co.isFinite() || co <= 0.0) {
            return ThermodilutionDerivedResult(
                cardiacOutputLMin = Double.NaN,
                cardiacIndexLMinM2 = null,
                strokeVolumeMlBeat = null
            )
        }

        val bsa = bsa_m2?.takeIf { it.isFinite() && it > 0.0 }
        val ci = bsa?.let { (co / it).takeIf { v -> v.isFinite() && v > 0.0 } }

        val hr = hr_bpm?.takeIf { it.isFinite() && it > 0.0 }
        val sv = hr?.let { strokeVolumeMlBeat(co, it).takeIf { v -> v.isFinite() && v > 0.0 } }

        return ThermodilutionDerivedResult(
            cardiacOutputLMin = co,
            cardiacIndexLMinM2 = ci,
            strokeVolumeMlBeat = sv
        )
    }

}
