package com.gipogo.rhctools.reporting.model

import androidx.annotation.StringRes

/**
 * Single source UI model for:
 * Room -> UI model -> Compose pages (A4) -> Bitmap render -> PDF -> Preview/share.
 *
 * No clinical calculations here. This is pure presentation structure.
 */

data class ReportDocumentUi(
    val header: ReportHeaderUi,
    val pages: List<ReportPageUi>
)

data class ReportHeaderUi(
    val appName: String,
    val patientDisplayName: String,
    val createdAtMillis: Long,
    val firstStudyAtMillis: Long,
    val lastStudyAtMillis: Long,
    val totalStudies: Int
)

sealed interface ReportPageUi {
    val pageIndex1Based: Int
    val pageCount: Int
}

/** Optional cover (only when totalStudies >= 2). */
data class CoverPageUi(
    override val pageIndex1Based: Int,
    override val pageCount: Int,
    val header: ReportHeaderUi
) : ReportPageUi

/** One study per page. */
data class StudyPageUi(
    override val pageIndex1Based: Int,
    override val pageCount: Int,
    val header: ReportHeaderUi,
    val study: StudyUi
) : ReportPageUi

/** Trends appendix pages (at the end, only when totalStudies >= 2). */
data class TrendsPageUi(
    override val pageIndex1Based: Int,
    override val pageCount: Int,
    val header: ReportHeaderUi,
    @StringRes val titleRes: Int,
    val charts: List<ChartUi>
) : ReportPageUi

data class StudyUi(
    val studyId: String,
    val studyAtMillis: Long,
    val studyNumber1Based: Int,
    val totalStudies: Int,
    val sections: List<SectionUi>
)

data class SectionUi(
    @StringRes val titleRes: Int,
    val rows: List<RowUi>
)

/**
 * Row policy:
 * - The builder decides whether to include the row or not.
 * - Missing values should be materialized as a "N/A" string (common_value_na) per your rule.
 */
data class RowUi(
    @StringRes val labelRes: Int,
    val valueText: String,
    @StringRes val unitRes: Int? = null
)

data class ChartUi(
    @StringRes val titleRes: Int,
    @StringRes val unitRes: Int?,
    val series: List<ChartSeriesUi>
)

data class ChartSeriesUi(
    @StringRes val labelRes: Int,
    val points: List<ChartPointUi>
)

data class ChartPointUi(
    val xMillis: Long,
    val y: Double
)
