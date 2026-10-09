package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.SystemClock
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.HomeMediaPlayer
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMediaPlayerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = LauncherApp(ComponentName("fixture", "fixture.Chrome"), "Chrome", null)
    private val track = NowPlaying("fixture-session", "Music", "春弦", "塞壬唱片-MSR",
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF47655C.toInt()) }.asImageBitmap(),
        playing = true, canToggle = true, canPrevious = true, canNext = true)
    private var state by mutableStateOf(LauncherUiState(
        apps = listOf(app), favoriteKeys = setOf(app.key), isLoadingApps = false,
        textMode = WallpaperTextMode.Light, media = MediaSnapshot(true, track),
    ))
    private val commands = mutableListOf<Pair<String, MediaCommand>>()
    private var dismissals = 0

    private fun home() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF23312E))) {
                    LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                        actions = LauncherActions(controlMedia = { id, command -> commands += id to command },
                            dismissMedia = { id, revision ->
                                assertEquals(track.sessionId, id)
                                assertEquals(state.media.nowPlaying!!.revision, revision)
                                dismissals++
                                state = state.copy(media = state.media.copy(nowPlaying = null))
                                true
                            }))
                }
            }
        }
    }

    @Test fun rowFitsBetweenDateAndFavoritesWithAlignedCoverAndRealActions() {
        home()
        val date = compose.onNodeWithTag("home_date").fetchSemanticsNode().boundsInRoot
        val media = compose.onNodeWithTag("home_media_player").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("home_media_artwork").fetchSemanticsNode().boundsInRoot
        val favorite = compose.onNodeWithTag("app:${app.key}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(date.bottom <= media.top)
        assertTrue(media.bottom <= favorite.top)
        assertEquals(cover.width, cover.height, 1f)
        val density = context.resources.displayMetrics.density
        assertEquals("Date and media retain only a compact gap", 4 * density, media.top - date.bottom, 1f)
        assertEquals("Media and favorites retain only a compact gap", 4 * density, favorite.top - media.bottom, 1f)
        assertEquals(favorite.left + 8 * density, cover.left, 1f)
        assertEquals(date.left, media.left, 1f)
        assertEquals(date.right, media.right, 1f)
        assertEquals(favorite.left, media.left, 1f)
        assertEquals(favorite.right, media.right, 1f)
        assertTrue("Album and shadow have room above", cover.top - media.top >= 6 * density - 1)
        assertTrue("Album and shadow have room below", media.bottom - cover.bottom >= 6 * density - 1)
        val metadata = compose.onNodeWithTag("home_media_metadata").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("media_toggle").fetchSemanticsNode().boundsInRoot
        assertEquals("Compact top inset", 6 * density, minOf(cover.top, metadata.top) - media.top, 1f)
        assertEquals("Compact bottom inset", 6 * density, media.bottom - maxOf(cover.bottom, toggle.bottom), 1f)
        val toggleOutline = compose.onNodeWithTag("media_toggle_outline", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(40 * density, toggleOutline.width, 1f)
        compose.onNodeWithText("春弦", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("塞壬唱片-MSR", useUnmergedTree = true).assertIsDisplayed()
        screenshot("home-media-playing.png")
        compose.onNodeWithTag("media_previous").performClick()
        compose.onNodeWithTag("media_toggle").performClick()
        compose.onNodeWithTag("media_next").performClick()
        compose.onNodeWithTag("home_media_artwork").performClick()
        compose.runOnIdle { assertEquals(listOf(MediaCommand.Previous, MediaCommand.TogglePlayback, MediaCommand.Next,
            MediaCommand.OpenPlayer).map { track.sessionId to it }, commands) }
        compose.runOnIdle { state = state.copy(media = state.media.copy(nowPlaying = track.copy(playing = false))) }
        compose.onNodeWithContentDescription(context.getString(R.string.media_play)).assertIsDisplayed()
    }

    @Test fun noPermissionDisabledPreferenceAndSettingsLoadingLeaveHomeClean() {
        home()
        val musicClock = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state = state.copy(media = state.media.copy(hasAccess = false)) }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.top > musicClock)
        compose.runOnIdle { state = state.copy(media = MediaSnapshot(true, track), settings = state.settings.copy(mediaPlayer = false)) }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(mediaPlayer = true), isLoadingSettings = true) }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(isLoadingSettings = false, media = MediaSnapshot(true)) }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
    }

    @Test fun narrowLayoutKeepsThreeTouchTargetsAndDisablesUnsupportedActions() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                val density = LocalDensity.current
                val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalConfiguration provides configuration) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF23312E)).padding(top = 60.dp)) {
                        HomeMediaPlayer(track.copy(canPrevious = false, canNext = false, artwork = null, title = null, artist = null),
                            { id, command -> commands += id to command }, Modifier.width(224.dp))
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("home_media_player").fetchSemanticsNode().boundsInRoot
        val previous = compose.onNodeWithTag("media_previous").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("media_toggle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("media_next").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        listOf(previous, toggle, next).forEach { assertTrue(it.width >= 48 * density - 1); assertTrue(it.right <= row.right + 1) }
        assertTrue(previous.right <= toggle.left + 1); assertTrue(toggle.right <= next.left + 1)
        compose.onNodeWithTag("media_next").performTouchInput { click() }
        compose.runOnIdle { assertTrue(commands.isEmpty()) }
        screenshot("home-media-narrow-large-font.png")
    }

    @Test fun permissionNeedsAnExplicitActionAndToggleNeverGrantsItSilently() {
        var requests = 0
        state = state.copy(media = MediaSnapshot(false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(
                    requestMediaAccess = { requests++ },
                    updateSettings = { change -> state = state.copy(settings = change(state.settings)) },
                ), onBack = {})
            }
        }
        compose.onNodeWithTag("settings_category_productivity").performClick()
        compose.onNodeWithTag("media_access").performScrollTo().performClick()
        compose.onNodeWithTag("media_access_cancel").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("settings_media_player").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithTag("media_access").assertDoesNotExist()
        compose.onNodeWithTag("settings_media_player").performClick().assertIsOn()
        compose.onNodeWithTag("media_access_continue").performClick()
        compose.runOnIdle { assertEquals(1, requests); state = state.copy(media = MediaSnapshot(true)) }
        compose.onNodeWithTag("media_access").assertDoesNotExist()
        screenshot("media-player-setting.png")
    }

    @Test fun bothSwipeDirectionsDismissWithoutPlaybackCommandsAndNewRevisionCanReturn() {
        home()
        compose.onNodeWithTag("home_media_player").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissals); assertTrue(commands.isEmpty())
            state = state.copy(media = MediaSnapshot(true, track.copy(playing = false, revision = 1)))
        }
        compose.onNodeWithTag("home_media_player").assertIsDisplayed().performTouchInput { swipeRight() }
        compose.onNodeWithTag("home_media_player").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, dismissals); assertTrue(commands.isEmpty()) }
    }

    @Test fun cancelledSwipeReturnsToItsPlaceAndDoesNotOpenThePlayer() {
        home()
        compose.onNodeWithTag("home_media_player").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-50f, 0f), 250)
            moveBy(androidx.compose.ui.geometry.Offset.Zero, 250)
            up()
        }
        compose.onNodeWithTag("home_media_player").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals); assertTrue(commands.isEmpty()) }
    }

    @Test fun metadataIsPlainTextWithoutAPressedRippleOrClippedGlyphs() {
        home()
        val metadata = compose.onNodeWithTag("home_media_metadata")
        val idle = metadata.captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = false
        try {
            metadata.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(250)
            SystemClock.sleep(300)
            val pressed = metadata.captureToImage().asAndroidBitmap()
            assertTrue("Metadata does not paint an inner ripple", idle.sameAs(pressed))
        } finally {
            metadata.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
        metadata.performClick()
        compose.runOnIdle { assertEquals(listOf(track.sessionId to MediaCommand.OpenPlayer), commands) }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification")).also { it.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
