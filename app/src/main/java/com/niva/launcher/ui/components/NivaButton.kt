package com.niva.launcher.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.niva.launcher.R
import com.niva.launcher.data.NivaButtonGesture
import com.niva.launcher.data.NivaButtonSettings
import com.niva.launcher.data.IconDesign
import com.niva.launcher.data.IconShape
import com.niva.launcher.data.icons.NivaButtonIcon
import com.niva.launcher.data.icons.iconShapePath
import com.niva.launcher.platform.NivaSystemActions
import kotlin.math.abs

/** A stable hit area with a separately transformed surface; moving the hit area
 * itself would feed its translation back into the pointer coordinates. */
@Composable
internal fun NivaButton(settings: NivaButtonSettings, editing: Boolean, enabled: Boolean,
    onGesture: (NivaButtonGesture, NivaButtonOrigin) -> Unit, onFinishEditing: () -> Unit,
    modifier: Modifier = Modifier, hidden: Boolean = false, heldOrigin: NivaButtonOrigin? = null,
    artwork: ImageBitmap? = null, design: IconDesign = NivaButtonIcon.defaults) {
    val latestSettings by rememberUpdatedState(settings)
    val perform by rememberUpdatedState(onGesture)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val resistanceDistance = with(density) { 72.dp.toPx() }
    val swipeThreshold = with(density) { 20.dp.toPx() }
    var touching by remember { mutableStateOf(false) }
    var displacement by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tracker = remember { VelocityTracker() }
    var releaseVelocity by remember { mutableStateOf(Offset.Zero) }
    val distance = displacement.getDistance()
    val stretch = (distance / resistanceDistance).coerceIn(0f, 1f) * 0.08f
    val animateTouch = settings.animationsEnabled && touching
    val movement by animateOffsetAsState(
        if (animateTouch) displacement * (0.42f / (1f + distance / resistanceDistance)) else Offset.Zero,
        animationSpec = if (touching || !settings.animationsEnabled) snap() else spring(dampingRatio = 0.58f, stiffness = 420f), label = "nivaButtonDrag")
    val scaleX by animateFloatAsState(if (!animateTouch) 1f else 0.92f + if (abs(displacement.x) > abs(displacement.y)) stretch else 0f,
        if (settings.animationsEnabled) spring(dampingRatio = 0.6f, stiffness = 650f) else snap(), label = "nivaButtonWidth")
    val scaleY by animateFloatAsState(if (!animateTouch) 1f else 0.92f + if (abs(displacement.y) >= abs(displacement.x)) stretch else 0f,
        if (settings.animationsEnabled) spring(dampingRatio = 0.6f, stiffness = 650f) else snap(), label = "nivaButtonHeight")
    // Snapshot the *displayed* pose before releasing the spring. The next page
    // starts here, not back at the button's resting layout position.
    val visualCenter = bounds.center + movement
    val halfSize = Offset(bounds.width * scaleX / 2f, bounds.height * scaleY / 2f)
    val origin by rememberUpdatedState(NivaButtonOrigin(Rect(visualCenter - halfSize, visualCenter + halfSize), artwork = artwork))
    val shape = remember(design.shape, design.cookieSides) {
        if (design.shape == IconShape.None) RectangleShape else GenericShape { size, _ ->
            addPath(iconShapePath(design.shape, design.cookieSides, size.minDimension).asComposePath())
        }
    }
    fun activate(gesture: NivaButtonGesture) { perform(gesture, origin.copy(velocity = releaseVelocity)) }
    val activateLatest by rememberUpdatedState(::activate)
    val description = stringResource(if (editing) R.string.done else R.string.settings_niva_button)
    val extraActions = if (editing || !enabled) emptyList() else NivaButtonGesture.entries
        .filter { it != NivaButtonGesture.Tap && it != NivaButtonGesture.LongPress && settings.target(it).active }
        .map { gesture -> CustomAccessibilityAction(stringResource(gesture.labelRes)) { activate(gesture); true } }

    Box(modifier.size(54.dp).testTag("launcher_fab")
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        // Observe the initial pass without consuming: feedback starts at touch-down
        // and follows every movement, before tap/drag recognition has completed.
        .pointerInput(enabled, editing) {
            if (enabled) awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true
                displacement = Offset.Zero
                releaseVelocity = Offset.Zero
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, down.position)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val velocity = tracker.calculateVelocity()
                            val drag = change.position - down.position
                            val length = drag.getDistance()
                            val resistance = 1f + length / resistanceDistance
                            val raw = Offset(velocity.x, velocity.y)
                            // Derivative of the rubber-band transform used above.
                            releaseVelocity = raw * (0.42f / resistance) - if (length > 0f)
                                drag * (0.42f * (drag.x * raw.x + drag.y * raw.y) /
                                    (resistanceDistance * resistance * resistance * length)) else Offset.Zero
                        }
                        val held = change?.pressed == true && event.changes.count { it.pressed } == 1
                        if (held) displacement = change.position - down.position
                    } while (held)
                } finally {
                    touching = false
                    displacement = Offset.Zero
                }
            }
        }
        .pointerInput(enabled, editing, swipeThreshold) {
            if (enabled && !editing) {
                var travel = Offset.Zero
                detectDragGestures(
                    onDragStart = { travel = Offset.Zero },
                    onDragCancel = { travel = Offset.Zero },
                    onDragEnd = {
                        if (travel.getDistance() >= swipeThreshold) {
                            val gesture = if (abs(travel.x) > abs(travel.y)) {
                                if (travel.x > 0f) NivaButtonGesture.SwipeRight else NivaButtonGesture.SwipeLeft
                            } else if (travel.y > 0f) NivaButtonGesture.SwipeDown else NivaButtonGesture.SwipeUp
                            if (latestSettings.target(gesture).active) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activateLatest(gesture)
                            }
                        }
                        travel = Offset.Zero
                    },
                ) { change, amount ->
                    change.consume() // Cancels the pending click/long press and home scrolling.
                    travel += amount
                }
            }
        }
        .combinedClickable(enabled = enabled, role = Role.Button,
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { if (editing) onFinishEditing() else if (latestSettings.tap.active) activate(NivaButtonGesture.Tap) },
            onLongClick = if (editing || !settings.longPress.active) null else ({ activate(NivaButtonGesture.LongPress) }),
            // Keep ordinary taps immediate when double-tap has not been enabled.
            onDoubleClick = if (editing || !settings.doubleTap.active) null else ({ activate(NivaButtonGesture.DoubleTap) }),
        ).semantics { contentDescription = description; customActions = extraActions }, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize().testTag("launcher_fab_surface").graphicsLayer {
            alpha = if (hidden) 0f else 1f
            translationX = heldOrigin?.let { it.bounds.center.x - bounds.center.x } ?: movement.x
            translationY = heldOrigin?.let { it.bounds.center.y - bounds.center.y } ?: movement.y
            this.scaleX = heldOrigin?.let { it.bounds.width / bounds.width.coerceAtLeast(1f) } ?: scaleX
            this.scaleY = heldOrigin?.let { it.bounds.height / bounds.height.coerceAtLeast(1f) } ?: scaleY
        }, shape = if (editing) CircleShape else shape,
            color = if (editing || artwork == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!editing && artwork != null) Image(artwork, null, Modifier.fillMaxSize())
                else Icon(painterResource(if (editing) R.drawable.ms_check else R.drawable.ic_launcher_foreground), null,
                    Modifier.size(if (editing) 26.dp else 48.dp))
            }
        }
    }
}

internal val NivaButtonGesture.labelRes: Int get() = when (this) {
    NivaButtonGesture.Tap -> R.string.niva_button_tap
    NivaButtonGesture.LongPress -> R.string.niva_button_long_press
    NivaButtonGesture.DoubleTap -> R.string.niva_button_double_tap
    NivaButtonGesture.SwipeUp -> R.string.niva_button_swipe_up
    NivaButtonGesture.SwipeDown -> R.string.niva_button_swipe_down
    NivaButtonGesture.SwipeLeft -> R.string.niva_button_swipe_left
    NivaButtonGesture.SwipeRight -> R.string.niva_button_swipe_right
}

@Composable
internal fun NivaSystemAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.niva_accessibility_title)) },
        text = { Text(stringResource(R.string.niva_accessibility_explanation)) },
        confirmButton = { TextButton(onClick = {
            onDismiss()
            if (!NivaSystemActions.openAccessibilitySettings(context))
                Toast.makeText(context, R.string.niva_action_unavailable, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.media_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
