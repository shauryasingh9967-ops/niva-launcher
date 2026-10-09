package com.niva.launcher.ui.components

import android.content.ComponentName
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.niva.launcher.platform.LauncherAppTransitions
import com.niva.launcher.platform.LauncherIconTarget
import kotlin.math.roundToInt

internal val LocalAppTransitions = staticCompositionLocalOf<LauncherAppTransitions?> { null }
internal val LocalHomeAnimationTarget = staticCompositionLocalOf { false }

/** Record the exact Compose artwork, including themed and third-party icons. */
@Composable
internal fun Modifier.launcherAnimationTarget(components: List<ComponentName>, folder: Boolean = false): Modifier {
    val transitions = LocalAppTransitions.current ?: return this
    val home = LocalHomeAnimationTarget.current
    val view = LocalView.current
    val layer = rememberGraphicsLayer()
    val location = remember { IconLocation() }
    val target = remember(transitions, components, home, folder, view, layer) {
        LauncherIconTarget(components, home, folder, view, boundsInWindow = {
            location.coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.let {
                Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
                    .takeUnless(Rect::isEmpty)
            }
        }, capture = {
            if (layer.size.width > 0 && layer.size.height > 0) layer.toImageBitmap().asAndroidBitmap() else null
        })
    }
    DisposableEffect(transitions, target) {
        transitions.register(target)
        onDispose { transitions.unregister(target) }
    }
    return this.onGloballyPositioned { location.coordinates = it }.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        if (!target.hidden) drawLayer(layer)
    }
}

private class IconLocation { var coordinates: LayoutCoordinates? = null }
