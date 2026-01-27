package com.gipogo.rhctools.reporting.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.reporting.model.*

private val HeaderShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(22.dp)

@Composable
fun ReportPage(
    page: ReportPageUi,
    modifier: Modifier = Modifier
) {
    when (page) {
        is CoverPageUi -> CoverPage(page, modifier)
        is StudyPageUi -> StudyPage(page, modifier)
        is TrendsPageUi -> TrendsPage(page, modifier)
    }
}

/**
 * A4 page container for Compose preview.
 * This is deterministic (no scroll inside a page).
 */
@Composable
private fun A4PageScaffold(
    pageIndex1Based: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ReportLayout.A4_ASPECT)
            .background(ReportColors.PageBg)
            .padding(12.dp)
    ) {
        // Page indicator (top-left)
        Text(
            text = stringResource(R.string.report_page_indicator, pageIndex1Based, pageCount),
            color = ReportColors.HeaderSub,
            style = MaterialTheme.typography.labelSmall
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            header()
            content()
        }
    }
}

@Composable
private fun ReportHeaderBlock(
    header: ReportHeaderUi,
    pageIndex1Based: Int,
    pageCount: Int,
    studyAtMillis: Long?,
    extraLine: String,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val createdAt = ReportFormat.formatDateTime(ctx, header.createdAtMillis)
    val studyAtText = studyAtMillis?.let { ReportFormat.formatDateTime(ctx, it) }
        ?: stringResource(R.string.report_longitudinal_label)

    Surface(
        shape = HeaderShape,
        color = ReportColors.HeaderBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${header.appName} · ${header.patientDisplayName}",
                color = ReportColors.HeaderText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.pdf_header_subtitle),
                color = ReportColors.HeaderSub,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.report_header_meta, createdAt, studyAtText),
                color = ReportColors.HeaderSub,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = extraLine,
                color = ReportColors.HeaderSub,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun WhiteMainCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CardShape,
        color = ReportColors.CardBg,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ReportColors.CardBorder, CardShape)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun Section(
    titleRes: Int,
    rows: List<RowUi>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(titleRes),
            color = ReportColors.Value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        rows.forEach { row ->
            RowKV(
                label = stringResource(row.labelRes),
                value = buildString {
                    append(row.valueText)
                    row.unitRes?.let { ur ->
                        append(" ")
                        append(stringResource(ur))
                    }
                }
            )
        }
    }
}

@Composable
private fun RowKV(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = ReportColors.Label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = ReportColors.Value,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun CoverPage(
    page: CoverPageUi,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val first = ReportFormat.formatDateTime(ctx, page.header.firstStudyAtMillis)
    val last = ReportFormat.formatDateTime(ctx, page.header.lastStudyAtMillis)

    A4PageScaffold(
        pageIndex1Based = page.pageIndex1Based,
        pageCount = page.pageCount,
        modifier = modifier,
        header = {
            ReportHeaderBlock(
                header = page.header,
                pageIndex1Based = page.pageIndex1Based,
                pageCount = page.pageCount,
                studyAtMillis = null,
                extraLine = stringResource(
                    R.string.report_cover_extra,
                    page.header.totalStudies,
                    first,
                    last
                )
            )
        },
        content = {
            WhiteMainCard {
                Text(
                    text = stringResource(R.string.report_cover_title),
                    color = ReportColors.Value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Divider(color = ReportColors.CardBorder)

                RowKV(
                    label = stringResource(R.string.report_cover_period_label),
                    value = stringResource(R.string.report_cover_period_value, first, last)
                )
                RowKV(
                    label = stringResource(R.string.report_cover_total_studies_label),
                    value = page.header.totalStudies.toString()
                )
            }
        }
    )
}

@Composable
private fun StudyPage(page: StudyPageUi, modifier: Modifier = Modifier) {
    StudyReportPageCardLayout(
        page = page,
        modifier = modifier,
        topRightChipText = null, // o algo si lo quieres
        classificationLabelRes = null,
        classificationChipText = null
    )
}

@Composable
private fun TrendsPage(
    page: TrendsPageUi,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val first = ReportFormat.formatDateTime(ctx, page.header.firstStudyAtMillis)
    val last = ReportFormat.formatDateTime(ctx, page.header.lastStudyAtMillis)
    val extraLine = stringResource(
        R.string.report_trends_extra_line,
        page.header.totalStudies,
        first,
        last
    )

    A4PageScaffold(
        pageIndex1Based = page.pageIndex1Based,
        pageCount = page.pageCount,
        modifier = modifier,
        header = {
            ReportHeaderBlock(
                header = page.header,
                pageIndex1Based = page.pageIndex1Based,
                pageCount = page.pageCount,
                studyAtMillis = null,
                extraLine = extraLine
            )
        },
        content = {
            WhiteMainCard {
                Text(
                    text = stringResource(page.titleRes),
                    color = ReportColors.Value,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Divider(color = ReportColors.CardBorder)

                // Charts (minimal placeholder rendering for now: we will implement actual chart composable in PASO 4.2)
                // For now: list series names and last value so the page is deterministic and uses no hardcoded text.
                page.charts.forEach { chart ->
                    Text(
                        text = stringResource(chart.titleRes),
                        color = ReportColors.Value,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )

                    chart.series.forEach { s ->
                        val last = s.points.lastOrNull()?.y
                        val lastText = last?.let { it.toString() } ?: stringResource(R.string.common_value_na)
                        RowKV(
                            label = stringResource(s.labelRes),
                            value = lastText
                        )
                    }

                    Divider(color = ReportColors.CardBorder)
                }
            }
        }
    )
}

