package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LauncherSurfaceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val density get() = context.resources.displayMetrics.density
    private val artwork = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(android.graphics.Color.RED) }.asImageBitmap()
    private val apps = (0..40).map {
        LauncherApp(ComponentName("test.surface", "App$it"), "App $it", artwork)
    }

    private fun show(drawer: Boolean = false) {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    LauncherScreen(
                        LauncherUiState(apps = apps, favoriteKeys = setOf(apps.first().key), isLoadingApps = false,
                            textMode = WallpaperTextMode.Dark,
                            media = MediaSnapshot(true, NowPlaying("surface", "Music", "Title", "Artist", artwork,
                                playing = true, canToggle = true, canPrevious = true, canNext = true))),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                        initialDrawerOpen = drawer,
                    )
                }
            }
        }
    }

    @Test fun drawerReachesThePhysicalTopAndFadesItsAppPixels() {
        show(drawer = true)
        val drawer = compose.onNodeWithTag("app_drawer")
        assertEquals("No status-bar padding may clip the scroll viewport", 0f, drawer.fetchSemanticsNode().boundsInRoot.top, 1f)
        val target = compose.onNodeWithTag("app:${apps[1].key}:icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        drawer.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target.center.y) }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val x = target.center.x.toInt()
        val topGreen = (image.getPixel(x, 1) shr 8) and 255
        val lowerGreen = (image.getPixel(x, (8 * density).toInt()) shr 8) and 255
        assertTrue("Red icon should disappear at the physical edge ($topGreen)", topGreen in 250..255)
        assertTrue("Content fades in gradually below the edge ($lowerGreen)", lowerGreen in 210..225)
        screenshot(image, "drawer-status-bar-fade.png")
    }

    @Test fun albumAndFabShadowsAreVisibleOutsideTheirOwnOutlines() {
        show()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        fun darkness(x: Float, y: Float): Int {
            val pixel = image.getPixel(x.toInt(), y.toInt())
            return 765 - ((pixel shr 16) and 255) - ((pixel shr 8) and 255) - (pixel and 255)
        }
        val album = compose.onNodeWithTag("home_media_artwork").fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithTag("launcher_fab").fetchSemanticsNode().boundsInRoot
        assertTrue("Artwork shadow must survive the outer container", darkness(album.center.x, album.bottom + density) > 0)
        assertTrue("FAB shadow must not be clipped to its circular surface", darkness(fab.center.x, fab.bottom + 2 * density) > 3)
        screenshot(image, "home-floating-shadows.png")
    }

    private fun screenshot(bitmap: Bitmap, name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification")).also { it.mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
