package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Elastic scroll-follow: an overlay (filter strip, FAB) observes the scroll of
 * any inner list, drifts against the scroll, then springs back to rest when
 * scrolling stops. No coupling to any specific list state - it works for
 * every list/grid/tab underneath the observer connection at once.
 *
 * The drift runs WITH the scroll: dragging content up pulls the overlay up
 * and vice versa, like the overlay rides along with the motion. Per-strip
 * placement weights keep the differential parallax on top of the follow
 * direction.
 *
 * The drift follows the scroll SPEED, not just the distance: slow scrolling
 * barely moves the overlay while a fast fling gives it the full elastic
 * swing, saturating at [maxOffsetPx] (see [rememberElasticScrollState]).
 *
 * Attach [elasticScrollObserver] to an ancestor Box of the scrolling content
 * and apply the displacement (via `Modifier.offset { }`) to the overlay. A
 * layout offset (not a graphicsLayer translation) is used so blur
 * sources/effects stay aligned: onGloballyPositioned reports the moved
 * position every frame and the backdrop re-samples there.
 */
@Stable
class ElasticScrollState internal constructor(
    private val maxOffsetPx: Float,
    // Scroll speed (px per ms) at which the drift saturates at [maxOffsetPx].
    private val fullDriftSpeedPxPerMs: Float,
    // Quiet time after the last scroll delta before springing back, and the
    // stiffness of that spring. Per-instance so each surface (strips, FAB)
    // can tune its own recovery feel.
    private val settleDelay: Duration,
    private val settleStiffness: Float,
    private val offset: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope
) {
    /** Current elastic displacement in px; negative = moved up, positive = down. */
    val offsetPx: Float get() = offset.value

    private var settleJob: Job? = null
    private var lastScrollTimestampMs = 0L
    private var smoothedSpeedPxPerMs = 0f

    /** Feed one raw scroll delta; the overlay follows it elastically. */
    internal fun onScroll(deltaY: Float) {
        if (deltaY == 0f) return
        // A new delta means the user is still scrolling - cancel any pending
        // settle so the spring only fires once scrolling goes quiet.
        settleJob?.cancel()
        // Estimate how fast the content is being scrolled from the spacing
        // between nested-scroll events. dt is clamped so dropped frames (or
        // several events landing in the same frame) cannot spike the estimate.
        val nowMs = System.nanoTime() / 1_000_000L
        val dtMs = if (lastScrollTimestampMs == 0L) FRAME_MS
        else (nowMs - lastScrollTimestampMs).coerceIn(MIN_FRAME_MS, MAX_FRAME_MS).toFloat()
        lastScrollTimestampMs = nowMs
        val speedPxPerMs = abs(deltaY) / dtMs
        smoothedSpeedPxPerMs += (speedPxPerMs - smoothedSpeedPxPerMs) * SPEED_SMOOTHING
        // Speed-proportional follow: lower speed = weaker animation, growing
        // linearly until the speed reaches fullDriftSpeed at the full swing.
        val speedFactor = (smoothedSpeedPxPerMs / fullDriftSpeedPxPerMs).coerceIn(MIN_SPEED_FACTOR, 1f)
        scope.launch {
            offset.snapTo((offset.value + deltaY * DRAG_FOLLOW * speedFactor).coerceIn(-maxOffsetPx, maxOffsetPx))
        }
        settleJob = scope.launch {
            delay(settleDelay)
            // Scrolling went quiet - the next gesture ramps its speed from zero.
            smoothedSpeedPxPerMs = 0f
            offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = settleStiffness))
        }
    }

    /** Fling finished - spring back to rest immediately. */
    internal fun settle() {
        settleJob?.cancel()
        // The next gesture starts its speed ramp from zero.
        smoothedSpeedPxPerMs = 0f
        lastScrollTimestampMs = 0L
        settleJob = scope.launch {
            offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = settleStiffness))
        }
    }

    private companion object {
        /** Fraction of the raw scroll delta the overlay follows at full speed. */
        const val DRAG_FOLLOW = 0.05f

        /** Low-pass factor for the instantaneous scroll-speed estimate. */
        const val SPEED_SMOOTHING = 0.3f

        /** Floor for the speed-derived factor: even the slowest scroll still
         *  nudges the overlay a little instead of freezing it entirely. */
        const val MIN_SPEED_FACTOR = 0.02f

        /** Event-gap assumptions (ms) for the speed estimate: the gap used for
         *  a gesture's first event, and the clamp bounds for real gaps. */
        const val FRAME_MS = 16f
        const val MIN_FRAME_MS = 1L
        const val MAX_FRAME_MS = 100L
    }
}

@Composable
fun rememberElasticScrollState(
    maxOffset: Dp = 14.dp,
    // Scroll speed (per second) at which the elastic drift saturates at
    // [maxOffset]; slower scrolling produces proportionally weaker drift.
    fullDriftSpeed: Dp = 1500.dp,
    // Quiet time after the last scroll delta before springing back.
    // Zero starts recovery instantly but also damps the mid-scroll swing,
    // since the spring pulls back between individual scroll events.
    settleDelay: Duration = 120.milliseconds,
    // Stiffness of the recovery spring (damping stays 0.8).
    settleStiffness: Float = 120f
): ElasticScrollState {
    val maxOffsetPx = with(LocalDensity.current) { maxOffset.toPx() }
    val fullDriftSpeedPxPerMs = with(LocalDensity.current) { fullDriftSpeed.toPx() } / 1000f
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return remember(maxOffsetPx, fullDriftSpeedPxPerMs, settleDelay, settleStiffness) {
        ElasticScrollState(maxOffsetPx, fullDriftSpeedPxPerMs, settleDelay, settleStiffness, offset, scope)
    }
}

/**
 * Observes every scroll/fling underneath this node (NestedScrollConnection)
 * and feeds the deltas into [state]. Does not consume anything.
 */
@Composable
fun Modifier.elasticScrollObserver(state: ElasticScrollState): Modifier {
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                state.onScroll(available.y)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                consumed // unused: the connection never claims consumed velocity
                available // unused: the settle never returns leftover velocity
                state.settle()
                return Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
