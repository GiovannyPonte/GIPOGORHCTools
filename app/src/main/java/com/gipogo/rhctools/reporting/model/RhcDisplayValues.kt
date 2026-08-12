package com.gipogo.rhctools.reporting.model

import androidx.annotation.StringRes
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import java.util.Locale

data class DisplayMetricValue(
    val value: Double?,
    val unitRes: Int?
)

data class SelectedCoDisplay(
    val cardiacOutputLMin: Double?,
    val cardiacIndexLMinM2: Double?,
    @StringRes val methodLabelRes: Int?
)

private fun String?.normalizedUnits(): String? =
    this?.trim()?.uppercase(Locale.getDefault())

fun RhcStudyDataEntity?.displayPvr(): DisplayMetricValue {
    val units = this?.pvrUnits.normalizedUnits()
    return if (units == "DYN") {
        DisplayMetricValue(value = this?.pvrDyn, unitRes = R.string.common_unit_dynes)
    } else {
        DisplayMetricValue(value = this?.pvrWood, unitRes = R.string.common_unit_wu_short)
    }
}

fun RhcStudyDataEntity?.displaySvr(): DisplayMetricValue {
    val units = this?.svrUnits.normalizedUnits()
    return if (units == "DYN") {
        DisplayMetricValue(value = this?.svrDyn, unitRes = R.string.common_unit_dynes)
    } else {
        DisplayMetricValue(value = this?.svrWood, unitRes = R.string.common_unit_wu)
    }
}

fun RhcStudyDataEntity?.displaySelectedCo(): SelectedCoDisplay {
    val method = this?.coSelectedMethod.normalizedUnits() ?: this?.coMethod.normalizedUnits()
    val methodLabelRes = when (method) {
        "FICK" -> R.string.co_method_fick
        "TD" -> R.string.co_method_thermodilution
        else -> null
    }

    return SelectedCoDisplay(
        cardiacOutputLMin = this?.cardiacOutputSelectedLMin ?: this?.cardiacOutputLMin,
        cardiacIndexLMinM2 = this?.cardiacIndexSelectedLMinM2 ?: this?.cardiacIndexLMinM2,
        methodLabelRes = methodLabelRes
    )
}
