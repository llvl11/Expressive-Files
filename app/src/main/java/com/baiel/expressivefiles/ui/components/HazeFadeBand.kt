package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Progressive frosted edge band (Haze): a blurred replay of the scrolling
 * content whose intensity is dissolved by a vertical gradient - full frost
 * at the screen edge, melting to sharp content toward the middle. Draw-only,
 * so touches pass straight through to the content underneath.
 *
 * The gradient's end offset is pinned to the band's measured height in px:
 * Compose gradients default their end to +infinity, which renders as a flat
 * fill (no fade at all) - the cause of every previous hard line here.
 *
 * @param top true for a top-edge band (frost at the band's top edge fading
 * downward), false for a bottom-edge band (mirrored).
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun HazeFadeBand(
    hazeState: HazeState,
    height: Dp,
    top: Boolean,
    modifier: Modifier = Modifier,
    // Named "blur" on purpose: a parameter called blurRadius would shadow
    // HazeEffectScope.blurRadius inside the effect block, turning the
    // assignment into an illegal val reassign.
    blur: Dp = 26.dp
) {
    val heightPx = with(LocalDensity.current) { height.toPx() }.coerceAtLeast(1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag(if (top) "fade_band_top" else "fade_band_bottom")
            .hazeEffect(hazeState) {
                blurRadius = blur
                // Downsample the source before blurring: ~55% fewer pixels
                // for the effect pass, visually imperceptible for frost.
                // This claws back most of the progressive shader's extra
                // cost, so the true gradient blur stays affordable.
                inputScale = HazeInputScale.Fixed(0.66f)
                // True progressive blur: the radius varies across the band
                // (full frost at the edge melting to sharp). Explicit px
                // spans: an unbounded gradient end renders as a flat fill.
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = if (top) 1f else 0f,
                    endIntensity = if (top) 0f else 1f,
                    startY = 0f,
                    endY = heightPx
                )
            }
    )
}
