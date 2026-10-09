package com.galaxyrio.gracelauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.ui.components.statusBarContentFade
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatusBarFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun foregroundFadesFromTransparentToFullOpacityWithoutAnOpaqueScrim() {
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.Black).testTag("fade")) {
                Box(Modifier.size(100.dp).statusBarContentFade().background(Color.White))
            }
        }
        val bitmap = compose.onNodeWithTag("fade").captureToImage().asAndroidBitmap()
        val x = bitmap.width / 2
        fun brightness(y: Int) = bitmap.getPixel(x, y) and 255
        assertTrue(brightness(0) in 0..3)
        // The default 56dp fade is still visible below a typical 28dp status bar.
        assertTrue(brightness(bitmap.height * 28 / 100) in 125..131)
        assertTrue(brightness(bitmap.height / 2) in 225..232)
        assertEquals(255, brightness(bitmap.height * 3 / 5))
        assertEquals(255, brightness(bitmap.height - 1))
    }

    @Test fun transparentContentDoesNotFadeOrTintWallpaper() {
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.Green).testTag("wallpaper")) {
                Box(Modifier.size(100.dp).statusBarContentFade(40.dp))
            }
        }
        val bitmap = compose.onNodeWithTag("wallpaper").captureToImage().asAndroidBitmap()
        assertEquals(bitmap.getPixel(50, bitmap.height - 1), bitmap.getPixel(50, 0))
    }
}
