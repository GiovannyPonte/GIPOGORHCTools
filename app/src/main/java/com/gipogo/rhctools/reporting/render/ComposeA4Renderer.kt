package com.gipogo.rhctools.reporting.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ComposeA4Renderer {

    /**
     * A4 @ 300 DPI
     * - Width: 2480 px
     * - Height: 3508 px
     */
    const val A4_WIDTH_PX_300 = 2480
    const val A4_HEIGHT_PX_300 = 3508

    /**
     * Render a single composable page into a bitmap with fixed A4 pixel size.
     * Must run view operations on Main thread.
     */
    suspend fun renderPageToBitmap(
        context: Context,
        widthPx: Int = A4_WIDTH_PX_300,
        heightPx: Int = A4_HEIGHT_PX_300,
        content: @Composable () -> Unit
    ): Bitmap = withContext(Dispatchers.Main) {

        val composeView = ComposeView(context).apply {
            // Dispose composition to avoid leaks
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                // Keep deterministic rendering (no animations)
                MaterialTheme {
                    content()
                }
            }
        }

        // Measure/layout with exact px
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, widthPx, heightPx)

        // Draw to bitmap
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)

        // Detach composition
        composeView.disposeComposition()

        bitmap
    }

    /**
     * Render many pages (one bitmap per page).
     */
    suspend fun renderPagesToBitmaps(
        context: Context,
        widthPx: Int = A4_WIDTH_PX_300,
        heightPx: Int = A4_HEIGHT_PX_300,
        pageCount: Int,
        pageRenderer: (index: Int) -> (@Composable () -> Unit)
    ): List<Bitmap> {
        val result = ArrayList<Bitmap>(pageCount)
        for (i in 0 until pageCount) {
            val bmp = renderPageToBitmap(
                context = context,
                widthPx = widthPx,
                heightPx = heightPx,
                content = pageRenderer(i)
            )
            result.add(bmp)
        }
        return result
    }
}
