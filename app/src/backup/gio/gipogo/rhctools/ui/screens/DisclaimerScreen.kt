package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    var acceptedCheckbox by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.disclaimer_title),
            style = MaterialTheme.typography.titleLarge
        )

        ElevatedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.disclaimer_section_edu_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_section_edu_body), style = MaterialTheme.typography.bodyMedium)

                Text(stringResource(R.string.disclaimer_section_user_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_section_user_body), style = MaterialTheme.typography.bodyMedium)

                Text(stringResource(R.string.disclaimer_section_limit_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_section_limit_body), style = MaterialTheme.typography.bodyMedium)

                Text(stringResource(R.string.disclaimer_section_privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_section_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
        }

        ElevatedCard {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = acceptedCheckbox,
                    onCheckedChange = { acceptedCheckbox = it }
                )
                Text(stringResource(R.string.disclaimer_checkbox_text), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onDecline
            ) { Text(stringResource(R.string.disclaimer_btn_decline)) }

            Button(
                modifier = Modifier.weight(1f),
                enabled = acceptedCheckbox,
                onClick = onAccept
            ) { Text(stringResource(R.string.disclaimer_btn_accept)) }
        }
    }
}
