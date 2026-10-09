package com.niva.launcher.ui.home

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.unit.Velocity
import com.niva.launcher.data.NivaButtonGesture
import kotlin.math.abs

/** Observe after descendants handle each event. Unlike detectTapGestures, this
 * never consumes a down/up or cancels a row's horizontal swipe recognizer. */
internal suspend fun PointerInputScope.observeBlankDoubleTaps(doubleTapSlop: Float, intervalMs: Int?, onDoubleTap: (Offset) -> Unit) {
    val timeout = intervalMs?.coerceIn(150, 700)?.toLong() ?: viewConfiguration.doubleTapTimeoutMillis
    var firstUp: PointerInputChange? = null
    var firstPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val previousUp = firstUp
        // Any intervening app/widget interaction, drag or multi-touch breaks the pair.
        firstUp = null
        if (down.isConsumed || currentEvent.changes.size != 1) return@awaitEachGesture
        val secondTap = previousUp != null &&
            down.uptimeMillis - previousUp.uptimeMillis in viewConfiguration.doubleTapMinTimeMillis..timeout &&
            (down.position - firstPosition).getDistance() <= doubleTapSlop
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.singleOrNull() ?: return@awaitEachGesture
            if (change.id != down.id || change.isConsumed ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis ||
                change.position.x !in 0f..size.width.toFloat() || change.position.y !in 0f..size.height.toFloat()
            ) return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) {
                if (secondTap) onDoubleTap(change.position) else {
                    firstUp = change
                    firstPosition = down.position
                }
                return@awaitEachGesture
            }
        }
    }
}

/** Observe only motion left over at a list boundary; keep Android's native stretch. */
internal class HomeGestureConnection(
    private val effect: OverscrollEffect?,
    private val list: LazyListState,
    private val pullThreshold: Float,
    private val flingThreshold: Float,
    private val canTrigger: (NivaButtonGesture) -> Boolean,
    private val onGesture: (NivaButtonGesture) -> Unit,
) : NestedScrollConnection {
    private var edgePull = 0f
    private var armed = false
    private var fired = false

    fun beginGesture() { edgePull = 0f; armed = true; fired = false }
    fun cancelGesture() { armed = false; edgePull = 0f }

    private fun boundaryGesture(delta: Float): NivaButtonGesture? = when {
        delta > 0 && !list.canScrollBackward -> NivaButtonGesture.SwipeDown
        delta < 0 && !list.canScrollForward -> NivaButtonGesture.SwipeUp
        else -> null
    }

    private fun trigger(delta: Float): Boolean {
        val gesture = boundaryGesture(delta) ?: return false
        if (!armed || fired || !canTrigger(gesture)) return false
        fired = true
        onGesture(gesture)
        return true
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Reversing a pull cancels its accumulated distance, including stretch relaxation.
        if (source == NestedScrollSource.UserInput && available.y * edgePull < 0f) {
            edgePull = if (abs(available.y) >= abs(edgePull)) 0f else edgePull + available.y
        }
        if (effect?.isInProgress != true) return Offset.Zero
        var forwarded = Offset.Zero
        val consumed = effect.applyToScroll(Offset(0f, available.y), source) { delta ->
            forwarded = delta
            delta
        }
        return consumed - forwarded
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            if (abs(consumed.y) > 0.5f) edgePull = 0f
            if (boundaryGesture(available.y) != null && abs(available.y) > 0.5f) {
                if (edgePull * available.y < 0f) edgePull = 0f
                edgePull += available.y
            }
        }
        return effect?.applyToScroll(Offset(0f, available.y), source) { Offset.Zero } ?: Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Commit on release, not halfway through a drag. This also handles slow,
        // deliberate pulls with almost zero release velocity on a short list.
        val direction = when {
            abs(edgePull) >= pullThreshold -> edgePull
            // A nested widget gets to consume its own fling first unless the
            // pointer has already pulled past the HOME list's boundary.
            abs(edgePull) > 0.5f && edgePull * available.y > 0f && abs(available.y) >= flingThreshold -> available.y
            else -> 0f
        }
        if (!trigger(direction)) return Velocity.Zero
        effect?.applyToFling(Velocity(0f, available.y)) { Velocity.Zero }
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A fast fling may reach the boundary after release. Only its remaining
        // velocity counts; an ordinary fling ending inside the list does not.
        if (abs(available.y) >= flingThreshold) trigger(available.y)
        armed = false
        effect?.applyToFling(Velocity(0f, available.y)) { Velocity.Zero }
        return if (effect == null) Velocity.Zero else Velocity(0f, available.y)
    }
}
