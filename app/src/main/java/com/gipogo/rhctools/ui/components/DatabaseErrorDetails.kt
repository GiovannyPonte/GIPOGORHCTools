package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.DatabaseErrorCategory
import com.gipogo.rhctools.data.db.DatabaseErrorDiagnostic

@Composable
fun DatabaseErrorDetails(
    diagnostic: DatabaseErrorDiagnostic,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val summaryRes = when {
        diagnostic.code.startsWith("DB-CORRUPT") -> R.string.db_error_corrupt
        diagnostic.code.startsWith("DB-LOCKED") -> R.string.db_error_locked
        diagnostic.code.startsWith("DB-STORAGE-FULL") -> R.string.db_error_storage_full
        diagnostic.code.startsWith("DB-STORAGE-PERM") -> R.string.db_error_storage_permission
        diagnostic.code.startsWith("DB-STORAGE-IO") -> R.string.db_error_storage_io
        diagnostic.code.startsWith("DB-SCHEMA-DOWNGRADE") -> R.string.db_error_downgrade
        else -> when (diagnostic.category) {
            DatabaseErrorCategory.KEY -> R.string.db_error_key
            DatabaseErrorCategory.ENCRYPTION -> R.string.db_error_encryption
            DatabaseErrorCategory.SCHEMA -> R.string.db_error_schema
            DatabaseErrorCategory.SQL -> R.string.db_error_sql
            DatabaseErrorCategory.STORAGE -> R.string.db_error_storage
            DatabaseErrorCategory.NATIVE_COMPONENT -> R.string.db_error_native
            DatabaseErrorCategory.OPEN -> R.string.db_error_open
        }
    }
    val reference = stringResource(R.string.error_reference, diagnostic.code)
    val summary = stringResource(summaryRes)
    val technicalTitle = stringResource(R.string.error_technical_detail)
    val copyText = "$reference\n$summary\n$technicalTitle: ${diagnostic.technicalDetail}"

    Column(
        modifier = modifier.padding(16.dp).testTag("database_error_details"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.common_error), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        Text(technicalTitle, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            diagnostic.technicalDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("database_error_technical_detail")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            onRetry?.let { Button(onClick = it) { Text(stringResource(R.string.common_retry)) } }
            onBack?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.common_back)) } }
        }
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(copyText))
                copied = true
            },
            modifier = Modifier.testTag("database_error_copy_button")
        ) {
            Text(stringResource(if (copied) R.string.error_details_copied else R.string.error_copy_details))
        }
    }
}
