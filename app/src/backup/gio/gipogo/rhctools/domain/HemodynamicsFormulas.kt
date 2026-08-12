package com.gipogo.rhctools.domain

import kotlin.math.max

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

    // VO2 estimado en reposo: ~125 mL/min/m2 * BSA (StatPearls) :contentReference[oaicite:18]{index=18}
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

    // PVR (Wood Units) = (mPAP - PCWP)/CO ; dyn = WU*80 :contentReference[oaicite:19]{index=19}
    fun pulmonaryVascularResistance(
        meanPap_mmHg: Double,
        pcwp_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        val wu = (meanPap_mmHg - pcwp_mmHg) / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // SVR (dyn·s·cm−5) = 80*(MAP - RAP)/CO ; WU = (MAP - RAP)/CO :contentReference[oaicite:20]{index=20}
    fun systemicVascularResistance(
        map_mmHg: Double,
        rap_mmHg: Double,
        cardiacOutput_LMin: Double
    ): ResistanceResult {
        val wu = (map_mmHg - rap_mmHg) / cardiacOutput_LMin
        return ResistanceResult(woodUnits = wu, dynesSecCm5 = wu * 80.0)
    }

    // CPO (W) = (MAP * CO)/451 :contentReference[oaicite:21]{index=21}
    fun cardiacPowerOutput(
        map_mmHg: Double,
        cardiacOutput_LMin: Double,
        bsa_m2: Double?
    ): CpoResult {
        val cpo = (map_mmHg * cardiacOutput_LMin) / 451.0
        val cpi = bsa_m2?.let { (map_mmHg * (cardiacOutput_LMin / it)) / 451.0 }
        return CpoResult(cpoWatts = cpo, cpiWattsPerM2 = cpi)
    }

    // PAPi = (PASP - PADP)/RAP :contentReference[oaicite:22]{index=22}
    fun papi(
        pasp_mmHg: Double,
        padp_mmHg: Double,
        rap_mmHg: Double
    ): PapiResult {
        val safeRap = max(rap_mmHg, 0.000001)
        val value = (pasp_mmHg - padp_mmHg) / safeRap
        return PapiResult(papi = value)
    }
}

