package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import kotlin.math.abs



fun Modifier.calcSwipeNavigation(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    thresholdPx: Float = 90f
): Modifier = pointerInput(Unit) {
    var totalDx = 0f

    detectHorizontalDragGestures(
        onDragStart = { totalDx = 0f },
        onHorizontalDrag = { _, dx -> totalDx += dx },
        onDragEnd = {
            if (abs(totalDx) < thresholdPx) return@detectHorizontalDragGestures
            if (totalDx < 0f) onNext() else onPrev()
        }
    )
}
