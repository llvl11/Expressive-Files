package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Null progress is indeterminate; a known value is clamped and announced to TalkBack. */
@Composable
fun SquigglyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    // When false only the squiggly progress stroke is drawn (no straight
    // track line behind it).
    showTrack: Boolean = true
) {
    val normalized = progress?.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    val animatedProgress by animateFloatAsState(normalized ?: 1f, tween(350), label = "wave_progress")
    // Only unknown-duration work runs continuously; static storage bars stay idle.
    val phase = if (normalized == null) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            initialValue = 0f, targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "wave_phase"
        )
    } else null
    Canvas(
        modifier.fillMaxWidth().height(20.dp).then(
            if (normalized == null) Modifier.progressSemantics()
            else Modifier.progressSemantics(normalized)
        )
    ) {
        val stroke = 4.dp.toPx().coerceAtMost(size.height / 3)
        val inset = stroke / 2
        val width = (size.width - stroke).coerceAtLeast(0f)
        val middle = size.height / 2
        val amplitude = 3.dp.toPx().coerceAtMost((size.height - stroke) / 2)
        val wavelength = 28.dp.toPx()
        fun x(distance: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - inset - distance else inset + distance
        if (showTrack) {
            drawLine(trackColor, Offset(x(0f), middle), Offset(x(width), middle), stroke, StrokeCap.Round)
        }
        val activeWidth = width * animatedProgress.coerceIn(0f, 1f)
        if (activeWidth > 0f) {
            val points = (activeWidth / 2.dp.toPx()).toInt().coerceAtLeast(2)
            val path = Path()
            for (i in 0..points) {
                val distance = activeWidth * i / points
                val y = middle + sin(distance / wavelength * (2 * PI).toFloat() - (phase?.value ?: 0f)) * amplitude
                if (i == 0) path.moveTo(x(distance), y) else path.lineTo(x(distance), y)
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}