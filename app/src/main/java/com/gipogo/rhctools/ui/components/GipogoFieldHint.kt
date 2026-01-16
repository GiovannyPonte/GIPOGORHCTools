package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.ui.validation.Severity

@Composable
fun GipogoFieldHint(
    severity: Severity,
    text: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val color = when (severity) {
        Severity.ERROR -> cs.error
        Severity.WARNING -> cs.tertiary
        Severity.OK -> cs.onSurfaceVariant
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 2.dp)
    )
}

