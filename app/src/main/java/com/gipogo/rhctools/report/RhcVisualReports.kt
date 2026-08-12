package com.gipogo.rhctools.ui.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.gipogo.rhctools.R
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.ui.components.InterpretationGaugeCardGeneric
import com.gipogo.rhctools.ui.interpretation.CpoInterpretation
import com.gipogo.rhctools.ui.interpretation.FickCoInterpretation
import com.gipogo.rhctools.ui.interpretation.InterpretationSpec
import com.gipogo.rhctools.ui.interpretation.PapiInterpretation
import com.gipogo.rhctools.ui.interpretation.PvrInterpretation
import com.gipogo.rhctools.ui.interpretation.SvrInterpretation
import com.gipogo.rhctools.util.Format

object RhcVisualReports {

    data class Group(
        val id: String,
        val cards: List<CardModel>
    )

    data class CardModel(
        val id: String,
        @StringRes val titleRes: Int,
        val mainValueText: String,
        @StringRes val mainUnitRes: Int,
        val secondary: List<SecondaryLine> = emptyList(),
        val gauge: GaugeModel? = null
    )

    data class SecondaryLine(
        @StringRes val labelRes: Int,
        val valueText: String,
        @StringRes val unitRes: Int
    )

    data class GaugeModel(
        val value: Double,
        val spec: InterpretationSpec
    )

    fun collectAvailableGroups(): List<Group> {
        val list = mutableListOf<Group>()
        fromCO()?.let(list::add)
        fromSVR()?.let(list::add)
        fromCPO()?.let(list::add)
        fromPAPI()?.let(list::add)
        fromPVRwithTPR()?.let(list::add)
        return list
    }

    // ----------------------------
    // Builders (1 por cálculo)
    // ----------------------------

    private fun fromCO(): Group? {
        val co = ReportStore.latestValueDoubleByKey(SharedKeys.CO_LMIN) ?: return null
        val ci = ReportStore.latestValueDoubleByKey(SharedKeys.CI_LMIN_M2)
        val hr = ReportStore.latestValueDoubleByKey(SharedKeys.HR_BPM)
        val sv = if (hr != null && hr > 0.0) (co / hr) * 1000.0 else null

        val secondary = buildList {
            ci?.let {
                add(
                    SecondaryLine(
                        labelRes = R.string.fick_result_ci_label,
                        valueText = Format.d(it, 2),
                        unitRes = R.string.common_unit_lmin_m2
                    )
                )
            }
            sv?.let {
                add(
                    SecondaryLine(
                        labelRes = R.string.fick_result_sv_label,
                        valueText = Format.d(it, 0),
                        unitRes = R.string.common_unit_ml
                    )
                )
            }
        }

        return Group(
            id = "CO_GROUP",
            cards = listOf(
                CardModel(
                    id = "CO",
                    titleRes = R.string.fick_result_eyebrow_co,
                    mainValueText = Format.d(co, 2),
                    mainUnitRes = R.string.common_unit_lmin,
                    secondary = secondary,
                    gauge = GaugeModel(co, FickCoInterpretation.spec)
                )
            )
        )
    }

    private fun fromSVR(): Group? {
        val wu = ReportStore.latestValueDoubleByKey(SharedKeys.SVR_WOOD)
        val dyn = ReportStore.latestValueDoubleByKey(SharedKeys.SVR_DYN)
        if (wu == null || dyn == null) return null

        val units = (ReportStore.latestValueStringByKey(SharedKeys.SVR_UNITS) ?: "WOOD").uppercase()
        val usingWu = units != "DYN"

        val mainValue = if (usingWu) wu else dyn
        val mainValueText = if (usingWu) Format.d(wu, 2) else Format.d(dyn, 0)
        val mainUnitRes = if (usingWu) R.string.common_unit_wu else R.string.common_unit_dynes
        val spec = if (usingWu) SvrInterpretation.specWu else SvrInterpretation.specDynes

        val secondary = listOf(
            SecondaryLine(R.string.svr_units_wood, Format.d(wu, 2), R.string.common_unit_wu),
            SecondaryLine(R.string.svr_units_dynes, Format.d(dyn, 0), R.string.common_unit_dynes)
        )

        return Group(
            id = "SVR_GROUP",
            cards = listOf(
                CardModel(
                    id = "SVR",
                    titleRes = R.string.svr_result_title,
                    mainValueText = mainValueText,
                    mainUnitRes = mainUnitRes,
                    secondary = secondary,
                    gauge = GaugeModel(mainValue, spec)
                )
            )
        )
    }

    private fun fromCPO(): Group? {
        val cpo = ReportStore.latestValueDoubleByKey(SharedKeys.CPO_W) ?: return null
        val cpi = ReportStore.latestValueDoubleByKey(SharedKeys.CPI_W_M2)
        val bsa = ReportStore.latestValueDoubleByKey(SharedKeys.BSA_M2)

        val secondary = buildList {
            cpi?.let {
                add(
                    SecondaryLine(
                        labelRes = R.string.cpo_metric_cpi,
                        valueText = Format.d(it, 2),
                        unitRes = R.string.common_unit_w_m2
                    )
                )
            }
            bsa?.let {
                add(
                    SecondaryLine(
                        labelRes = R.string.common_label_bsa,
                        valueText = Format.d(it, 2),
                        unitRes = R.string.common_unit_m2
                    )
                )
            }
        }

        return Group(
            id = "CPO_GROUP",
            cards = listOf(
                CardModel(
                    id = "CPO",
                    titleRes = R.string.cpo_result_eyebrow,
                    mainValueText = Format.d(cpo, 2),
                    mainUnitRes = R.string.common_unit_w,
                    secondary = secondary,
                    gauge = GaugeModel(cpo, CpoInterpretation.spec)
                )
            )
        )
    }

    private fun fromPAPI(): Group? {
        val papi = ReportStore.latestValueDoubleByKey(SharedKeys.PAPI) ?: return null
        val rap = ReportStore.latestValueDoubleByKey(SharedKeys.RAP_MMHG)

        val secondary = buildList {
            rap?.let {
                add(
                    SecondaryLine(
                        labelRes = R.string.papi_hero_right_label, // "RAP"
                        valueText = Format.d(it, 0),
                        unitRes = R.string.common_unit_mmhg
                    )
                )
            }
        }

        return Group(
            id = "PAPI_GROUP",
            cards = listOf(
                CardModel(
                    id = "PAPI",
                    titleRes = R.string.papi_screen_title,
                    mainValueText = Format.d(papi, 2),
                    mainUnitRes = R.string.common_unit_none,
                    secondary = secondary,
                    gauge = GaugeModel(papi, PapiInterpretation.spec)
                )
            )
        )
    }

    /**
     * PVR: 1 tarjeta con gauge + 1 tarjeta TPR (sin gauge).
     * TPR actualmente NO tiene SharedKeys, así que lo leemos del CalcEntry (por label "TPR").
     */
    private fun fromPVRwithTPR(): Group? {
        val pvrWu = ReportStore.latestValueDoubleByKey(SharedKeys.PVR_WOOD)
        val pvrDyn = ReportStore.latestValueDoubleByKey(SharedKeys.PVR_DYN)
        if (pvrWu == null || pvrDyn == null) return null

        val units = (ReportStore.latestValueStringByKey(SharedKeys.PVR_UNITS) ?: "WOOD").uppercase()
        val usingWu = units != "DYN"

        val pvrMainValue = if (usingWu) pvrWu else pvrDyn
        val pvrMainText = if (usingWu) Format.d(pvrWu, 2) else Format.d(pvrDyn, 0)
        val pvrMainUnitRes = if (usingWu) R.string.common_unit_wu else R.string.common_unit_dynes
        val pvrSpec = if (usingWu) PvrInterpretation.specWu else PvrInterpretation.specDynes

        val cards = mutableListOf<CardModel>()

        cards += CardModel(
            id = "PVR",
            titleRes = R.string.pvr_result_title,
            mainValueText = pvrMainText,
            mainUnitRes = pvrMainUnitRes,
            secondary = listOf(
                SecondaryLine(R.string.pvr_hero_left_label, Format.d(pvrWu, 2), R.string.common_unit_wu),
                SecondaryLine(R.string.pvr_hero_right_label, Format.d(pvrDyn, 0), R.string.common_unit_dynes)
            ),
            gauge = GaugeModel(pvrMainValue, pvrSpec)
        )

        val (tprWu, tprDyn) = latestTprPairFromPvrEntry() ?: (null to null)
        val tprMainText = when {
            usingWu && tprWu != null -> Format.d(tprWu, 2)
            !usingWu && tprDyn != null -> Format.d(tprDyn, 0)
            else -> null
        }

        if (tprMainText != null) {
            val tprUnitRes = if (usingWu) R.string.common_unit_wu else R.string.common_unit_dynes

            cards += CardModel(
                id = "TPR",
                titleRes = R.string.tpr_result_title,
                mainValueText = tprMainText,
                mainUnitRes = tprUnitRes,
                secondary = buildList {
                    tprWu?.let { add(SecondaryLine(R.string.pvr_hero_left_label, Format.d(it, 2), R.string.common_unit_wu)) }
                    tprDyn?.let { add(SecondaryLine(R.string.pvr_hero_right_label, Format.d(it, 0), R.string.common_unit_dynes)) }
                },
                gauge = null // como tu pantalla: TPR sin interpretación
            )
        }

        return Group(id = "PVR_GROUP", cards = cards)
    }

    private fun latestTprPairFromPvrEntry(): Pair<Double?, Double?>? {
        val entry = ReportStore.entries.value[CalcType.PVR] ?: return null

        var wu: Double? = null
        var dyn: Double? = null

        for (li in entry.outputs) {
            if (li.label != "TPR") continue
            val v = li.value.toDoubleOrNull() ?: continue

            when (li.unit) {
                "WU", "Wood Units" -> wu = v
                "dyn·s·cm⁻⁵", "dyn·s·cm-5", "dynes" -> dyn = v
            }
        }
        if (wu == null && dyn == null) return null
        return wu to dyn
    }

    // ----------------------------
    // Compact renderer (HomeCalculator)
    // ----------------------------

    @Composable
    fun SummaryGroupCard(
        group: Group,
        modifier: Modifier = Modifier
    ) {
        val cs = MaterialTheme.colorScheme

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                group.cards.forEach { card ->
                    SummaryCard(card)
                }
            }
        }
    }

    @Composable
    private fun SummaryCard(card: CardModel) {
        val cs = MaterialTheme.colorScheme

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(card.titleRes).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )

            Text(
                text = "${card.mainValueText} ${stringResource(card.mainUnitRes)}".trim(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )

            card.secondary.take(2).forEach { s ->
                Text(
                    text = "${stringResource(s.labelRes)}: ${s.valueText} ${stringResource(s.unitRes)}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            card.gauge?.let { g ->
                InterpretationGaugeCardGeneric(
                    value = g.value,
                    spec = g.spec
                )
            }
        }
    }
}
