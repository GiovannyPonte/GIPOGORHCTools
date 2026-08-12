package com.gipogo.rhctools.domain

/** Derived values calculated exclusively from canonical inputs. */
data class CanonicalDerivedHemodynamics(
    val cardiacIndexLMinM2: Double?,
    val svrWood: Double?,
    val svrDyn: Double?,
    val pvrWood: Double?,
    val pvrDyn: Double?,
    val papi: Double?,
    val cardiacPowerW: Double?,
    val cardiacPowerIndexWm2: Double?
)

object CanonicalHemodynamics {
    fun derive(
        cardiacOutputLMin: Double?,
        bsaM2: Double?,
        mapMmHg: Double?,
        rapMmHg: Double?,
        paspMmHg: Double?,
        padpMmHg: Double?,
        mpapMmHg: Double?,
        pawpMmHg: Double?
    ): CanonicalDerivedHemodynamics {
        val co = cardiacOutputLMin?.takeIf { it.isFinite() && it > 0.0 }
        val bsa = bsaM2?.takeIf { it.isFinite() && it > 0.0 }
        val ci = if (co != null && bsa != null) (co / bsa).takeIf { it.isFinite() && it > 0.0 } else null

        val svr = if (co != null && mapMmHg?.isFinite() == true && rapMmHg?.isFinite() == true && mapMmHg >= rapMmHg) {
            HemodynamicsFormulas.systemicVascularResistance(mapMmHg, rapMmHg, co)
                .takeIf { it.woodUnits.isFinite() && it.woodUnits >= 0.0 }
        } else null

        val pvr = if (co != null && mpapMmHg?.isFinite() == true && pawpMmHg?.isFinite() == true) {
            HemodynamicsFormulas.pulmonaryResistanceWithTpr(mpapMmHg, pawpMmHg, co)
                .takeIf { it.pvrWu?.isFinite() == true && it.pvrWu >= 0.0 }
        } else null

        val papi = if (
            paspMmHg?.isFinite() == true && padpMmHg?.isFinite() == true && rapMmHg?.isFinite() == true &&
            paspMmHg >= padpMmHg && rapMmHg > 0.0
        ) HemodynamicsFormulas.papi(paspMmHg, padpMmHg, rapMmHg).papi.takeIf { it.isFinite() && it >= 0.0 }
        else null

        val power = if (co != null && mapMmHg?.isFinite() == true && mapMmHg > 0.0) {
            HemodynamicsFormulas.cardiacPowerOutput(mapMmHg, co, bsa)
        } else null

        return CanonicalDerivedHemodynamics(
            cardiacIndexLMinM2 = ci,
            svrWood = svr?.woodUnits,
            svrDyn = svr?.dynesSecCm5,
            pvrWood = pvr?.pvrWu,
            pvrDyn = pvr?.pvrDynes,
            papi = papi,
            cardiacPowerW = power?.cpoWatts?.takeIf { it.isFinite() && it >= 0.0 },
            cardiacPowerIndexWm2 = power?.cpiWattsPerM2?.takeIf { it.isFinite() && it >= 0.0 }
        )
    }
}
