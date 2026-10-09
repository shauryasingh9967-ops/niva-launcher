package com.niva.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.niva.launcher.data.IconDesign
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.icons.IconLayers
import com.niva.launcher.ui.settings.DesignerPreviewIcon
import com.niva.launcher.ui.theme.NivaLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconDesignerDragTest {
    @get:Rule val compose = createComposeRule()
    private var style by mutableStateOf(IconDesign())
    private fun show(draggable: Boolean) {
        val back = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val symbol = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(symbol).drawRect(47f, 47f, 53f, 53f, Paint().apply { color = Color.RED })
        val original = back.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(original).drawBitmap(symbol, 0f, 0f, null)
        val layers = IconLayers(original, back, symbol)
        compose.setContent {
            NivaLauncherTheme {
                DesignerPreviewIcon(LauncherApp(ComponentName("test", "App"), "App", null), layers, style,
                    Color.GREEN to Color.YELLOW, 128.dp, Modifier.testTag("preview"), draggable) { style = it }
            }
        }
    }
    @Test fun smallForegroundCanBeDraggedFromItsInitialTouchPoint() {
        show(true)
        compose.onNodeWithTag("preview").performTouchInput { swipe(center, center + Offset(width * .25f, height * .15f), 400) }
        compose.runOnIdle { assertTrue(style.x > 10f); assertTrue(style.y > 5f); assertEquals(100, style.size) }
    }
    @Test fun trayAndBulkPreviewCannotBeDragged() {
        show(true)
        compose.onNodeWithTag("preview").performTouchInput { swipe(Offset(width * .2f, height * .2f), Offset(width * .4f, height * .4f), 400) }
        compose.runOnIdle { assertEquals(IconDesign(), style) }
    }
    @Test fun bulkPreviewHasNoDragEditing() {
        show(false)
        compose.onNodeWithTag("preview").performTouchInput { swipe(center, center + Offset(width * .25f, height * .15f), 400) }
        compose.runOnIdle { assertEquals(IconDesign(), style) }
    }
}
