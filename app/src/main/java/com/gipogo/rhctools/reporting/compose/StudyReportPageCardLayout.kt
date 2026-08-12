package com.gipogo.rhctools.reporting.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.reporting.model.RowUi
import com.gipogo.rhctools.reporting.model.SectionUi
import com.gipogo.rhctools.reporting.model.StudyPageUi

private val PageShape = RoundedCornerShape(24.dp)
private val HeaderShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun StudyReportPageCardLayout(
    page: StudyPageUi,
    modifier: Modifier = Modifier,
    // Chip superior derecha opcional (como “Variant 2” en tu imagen)
    topRightChipText: String? = null,
    // Bloque final de clasificación opcional (como “PH CLASSIFICATION”)
    classificationLabelRes: Int? = null,
    classificationChipText: String? = null
) {
    // Paleta “PDF friendly”
    val pageBg = Color(0xFFF6F5FA)
    val headerBg = Color(0xFF5E4B9A) // morado sobrio
    val cardBg = Color(0xFFF3EFF9)
    val border = Color(0xFFE5E7EB)
    val labelColor = Color(0xFF6B7280)
    val valueColor = Color(0xFF111827)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(210f / 297f) // A4 portrait preview
            .clip(PageShape)
            .background(pageBg)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Page indicator + optional chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.report_page_indicator, page.pageIndex1Based, page.pageCount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = valueColor
                )

                if (!topRightChipText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(topRightChipText) }
                    )
                }
            }

            // Header card (purple)
            HeaderCard(
                appName = page.header.appName,
                patientName = page.header.patientDisplayName,
                subtitleRes = R.string.pdf_header_subtitle,
                createdAtMillis = page.header.createdAtMillis,
                studyAtMillis = page.study.studyAtMillis,
                headerBg = headerBg
            )

            // Section cards
            page.study.sections.forEach { section ->
                SectionCard(
                    titleRes = section.titleRes,
                    rows = section.rows,
                    container = cardBg,
                    border = border,
                    labelColor = labelColor,
                    valueColor = valueColor
                )
            }

            // Optional classification block (as a final slim card)
            if (classificationLabelRes != null && !classificationChipText.isNullOrBlank()) {
                ClassificationCard(
                    labelRes = classificationLabelRes,
                    chipText = classificationChipText,
                    container = cardBg,
                    border = border
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderCard(
    appName: String,
    patientName: String,
    @StringRes subtitleRes: Int,
    createdAtMillis: Long,
    studyAtMillis: Long,
    headerBg: Color
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Surface(
        shape = HeaderShape,
        color = headerBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = appName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = patientName,
                    color = Color.White.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = stringResource(subtitleRes).uppercase(),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall
            )

            Divider(color = Color.White.copy(alpha = 0.18f))

            val createdAt = ReportFormat.formatDateTime(ctx, createdAtMillis)
            val studyAt = ReportFormat.formatDateTime(ctx, studyAtMillis)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaPill(
                    icon = Icons.Outlined.CalendarMonth,
                    text = createdAt
                )
                MetaPill(
                    icon = Icons.Outlined.Schedule,
                    text = studyAt
                )
            }
        }
    }
}

@Composable
private fun MetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionCard(
    @StringRes titleRes: Int,
    rows: List<RowUi>,
    container: Color,
    border: Color,
    labelColor: Color,
    valueColor: Color
) {
    Surface(
        shape = CardShape,
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(titleRes).uppercase(),
                color = valueColor.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = stringResource(row.labelRes),
                        color = labelColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(12.dp))

                    // Value + unit aligned to the right
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = row.valueText,
                            color = valueColor,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        row.unitRes?.let { ur ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(ur),
                                color = labelColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.alpha(0.95f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassificationCard(
    @StringRes labelRes: Int,
    chipText: String,
    container: Color,
    border: Color
) {
    Surface(
        shape = CardShape,
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF111827)
            )

            AssistChip(
                onClick = {},
                label = { Text(chipText) }
            )
        }
    }
}
