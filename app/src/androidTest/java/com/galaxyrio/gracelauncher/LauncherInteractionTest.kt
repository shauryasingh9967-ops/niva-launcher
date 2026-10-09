package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf("Alarm", "Calendar", "Camera", "Chrome", "Clock", "Zen").map { label ->
        LauncherApp(ComponentName("test.${label.lowercase()}", "$label.Activity"), label, null)
    }

    private fun showLauncher(
        events: List<ScheduleEvent> = emptyList(),
        initialDrawerOpen: Boolean = false,
        actions: LauncherActions = LauncherActions(),
        status: ScheduleStatus = ScheduleStatus.Ready,
        displayedApps: List<LauncherApp> = apps,
        favoriteCount: Int = 5,
        onLaunch: (LauncherApp) -> Unit = {},
        onToggle: () -> Unit = {},
        onRequestCalendar: () -> Unit = {},
    ) {
        compose.setContent {
            var favorites by remember { mutableStateOf(displayedApps.take(favoriteCount).mapTo(linkedSetOf(), LauncherApp::key).toSet()) }
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(
                            apps = displayedApps,
                            favoriteKeys = favorites,
                            isLoadingApps = false,
                            events = events,
                            scheduleStatus = status,
                            textMode = WallpaperTextMode.Light,
                        ),
                        onDateClick = onRequestCalendar, onClockClick = {}, onLaunchApp = onLaunch,
                        onToggleFavorite = {
                            favorites = if (it.key in favorites) favorites - it.key else favorites + it.key
                            onToggle()
                        },
                        actions = actions, initialDrawerOpen = initialDrawerOpen,
                    )
                }
            }
        }
    }

    @Test
    fun homeShowsOneCompactEventAndOnlyPopulatedLetters() {
        val now = Instant.now()
        showLauncher(listOf(
            ScheduleEvent(1, "Movie night", now.plusSeconds(1680), now.plusSeconds(7200), false, "Cinema", null),
            ScheduleEvent(2, "Second appointment", now.plusSeconds(10800), now.plusSeconds(14400), false, null, null),
        ))
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithText("Movie night", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Second appointment", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Cinema", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Swipe up for all apps").assertDoesNotExist()
        listOf("A", "C", "Z").forEach {
            compose.onNodeWithTag("alphabet:$it").assertIsDisplayed()
        }
        listOf("B", "D", "#").forEach {
            compose.onNodeWithTag("alphabet:$it").assertDoesNotExist()
        }
        saveScreenshot("home-sample.png")
    }

    @Test
    fun oneUnbrokenDragOpensScrubsReversesAndReturnsHome() {
        showLauncher()
        val rail = compose.onNodeWithTag("alphabet_rail")
        // Four cells: home, A, C, Z. Do not release the pointer between pages.
        rail.performTouchInput { down(Offset(centerX, height * 0.375f)) }
        compose.onNodeWithTag("section:A").assertIsDisplayed()

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.625f), delayMillis = 250) }
        compose.onNodeWithTag("section:C").assertIsDisplayed()
        compose.onNodeWithTag("section:A").assertDoesNotExist()
        compose.onNodeWithTag("section:Z").assertDoesNotExist()
        saveScreenshot("drawer-wave.png")

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.875f), delayMillis = 250) }
        compose.onNodeWithTag("section:Z").assertIsDisplayed()
        compose.onNodeWithTag("section:C").assertDoesNotExist()

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.125f), delayMillis = 250) }
        compose.onNodeWithTag("home_clock").assertIsDisplayed()

        rail.performTouchInput {
            moveTo(Offset(centerX, height * 0.625f), delayMillis = 250)
            up()
        }
        compose.onNodeWithTag("section:C").assertIsDisplayed()
        listOf("A", "C", "Z").forEach {
            compose.onNodeWithTag("section:$it").assertIsDisplayed()
        }
        listOf("All apps", "Search apps", "Back to home").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        saveScreenshot("drawer-sample.png")
    }

    @Test
    fun emptyAgendaDoesNotLeaveACardOrPermissionPrompt() {
        showLauncher()
        compose.onNodeWithTag("schedule_line").assertDoesNotExist()
        compose.onNodeWithText("Connect calendar").assertDoesNotExist()
        compose.onNodeWithText("Up next").assertDoesNotExist()
        compose.onNodeWithText("You’re all clear").assertDoesNotExist()
        compose.onNodeWithTag("home_date").assertIsDisplayed()
    }

    @Test
    fun normalDrawerReservesLeadingSpaceAndContainsAllSections() {
        showLauncher(initialDrawerOpen = true)
        listOf("A", "C", "Z").forEach { compose.onNodeWithTag("section:$it").assertIsDisplayed() }
        val first = compose.onNodeWithTag("section:A").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("app_drawer").fetchSemanticsNode().boundsInRoot
        assertTrue("Drawer should start below a scrollable leading area", first.top - viewport.top in viewport.height * 0.27f..viewport.height * 0.34f)
        compose.onNodeWithTag("alphabet:C").performClick()
        // An accessibility click has no held finger: it jumps into the complete list.
        listOf("A", "C", "Z").forEach { compose.onNodeWithTag("section:$it").assertIsDisplayed() }
        compose.onNodeWithTag("alphabet_home").performClick()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithTag("alphabet:A").performClick()
        listOf("A", "C", "Z").forEach { compose.onNodeWithTag("section:$it").assertIsDisplayed() }
    }

    @Test
    fun cancellingHeldAlphabetSelectionRestoresTheCompleteList() {
        showLauncher()
        val rail = compose.onNodeWithTag("alphabet_rail")
        rail.performTouchInput { down(Offset(centerX, height * 0.625f)) }
        compose.onNodeWithTag("section:C").assertIsDisplayed()
        compose.onNodeWithTag("section:A").assertDoesNotExist()
        compose.onNodeWithTag("section:Z").assertDoesNotExist()
        rail.performTouchInput { cancel() }
        listOf("A", "C", "Z").forEach { compose.onNodeWithTag("section:$it").assertIsDisplayed() }
    }

    @Test
    fun homeVerticalGesturesNeverOpenDrawerAndFloatingButtonLongPressOpensSettings() {
        showLauncher()
        compose.onNodeWithTag("home_content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithTag("app_drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("home_content").performTouchInput { swipeDown() }
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithTag("app_drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("open_all_apps").assertDoesNotExist()
        compose.onNodeWithTag("launcher_fab").performTouchInput { longClick() }
        compose.onNodeWithTag("app_drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        compose.onNodeWithTag("launcher_sheet").assertDoesNotExist()
    }

    @Test
    fun rightSwipeOpensProvidedShortcutRowsAndDoesNotLaunchTheApp() {
        var launches = 0
        var toggles = 0
        var openedShortcut: String? = null
        showLauncher(
            actions = LauncherActions(
                shortcuts = { ShortcutResult(ShortcutStatus.Ready, listOf(
                    LauncherShortcut("compose", it.packageName, "Compose", null),
                    LauncherShortcut("inbox", it.packageName, "Inbox", null),
                )) },
                launchShortcut = { openedShortcut = it.id },
            ),
            onLaunch = { launches++ }, onToggle = { toggles++ },
        )
        compose.onNodeWithTag("app:${apps[1].key}").performTouchInput { swipeRight(durationMillis = 240) }
        awaitSurface("shortcut_popup")
        compose.onNodeWithTag("shortcut_popup").assertIsDisplayed()
        compose.onNodeWithText("Compose").assertIsDisplayed()
        compose.onNodeWithText("Inbox").assertIsDisplayed()
        saveScreenshot("shortcuts-sample.png")
        assertEquals(0, launches)
        assertEquals(0, toggles)
        compose.onNodeWithTag("shortcut:compose").performClick()
        assertEquals("compose", openedShortcut)
        compose.onNodeWithTag("shortcut_popup").assertDoesNotExist()
    }

    @Test
    fun shortSwipeOpensShortcutsWithoutFurtherMovementAndReverseDragCancels() {
        var launches = 0
        var prepared = 0
        val result = CompletableDeferred<ShortcutResult>()
        showLauncher(
            actions = LauncherActions(shortcuts = { result.await() }, prepareShortcuts = { prepared++ }),
            onLaunch = { launches++ },
        )
        val row = compose.onNodeWithTag("app:${apps[1].key}")
        val dragDistance = 40f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        row.performTouchInput {
            down(Offset(8f, centerY))
            moveTo(Offset(8f + dragDistance, centerY), delayMillis = 150)
        }
        awaitSurface("shortcut_popup")
        val progress = compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals("Opening continues with the finger stationary and still down", 1f, progress, 0.001f)
        compose.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        )).assertCountEquals(0)
        assertTrue("Touch down should warm shortcut data before the gesture commits", prepared > 0)
        result.complete(ShortcutResult(ShortcutStatus.Ready, listOf(
            LauncherShortcut("compose", apps[1].packageName, "Compose", null),
        )))
        compose.onNodeWithText("Compose").assertIsDisplayed()
        saveScreenshot("shortcuts-partial.png")
        row.performTouchInput {
            moveTo(Offset(8f, centerY), delayMillis = 150)
            up()
        }
        compose.onNodeWithTag("shortcut_popup").assertDoesNotExist()
        assertEquals(0, launches)
    }

    @Test
    fun shortcutDirectionCanReverseAndReopenWithoutReturningToTheGestureOrigin() {
        showLauncher(actions = shortcutFixtureActions())
        val row = compose.onNodeWithTag("app:${apps[1].key}")
        val dragDistance = 24f * deviceDensity()
        row.performTouchInput {
            down(Offset(8f, centerY))
            moveTo(Offset(8f + dragDistance, centerY), delayMillis = 150)
        }
        awaitSurface("shortcut_popup")
        val progress = compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals("A 24dp held drag should trigger a complete opening animation", 1f, progress, 0.001f)
        compose.onNodeWithTag("shortcut:compose").assertIsDisplayed()
        saveScreenshot("shortcuts-short-drag-expanded.png")
        row.performTouchInput {
            moveTo(Offset(8f + 200f * deviceDensity(), centerY), delayMillis = 100)
            moveTo(Offset(8f + 184f * deviceDensity(), centerY), delayMillis = 100)
        }
        assertEquals(
            "A short leftward reversal closes even after a long right swipe, before UP",
            0f, compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f,
        )
        row.performTouchInput {
            moveTo(Offset(8f + 200f * deviceDensity(), centerY), delayMillis = 100)
            up()
        }
        assertEquals("Reversing right reopens the same panel and releasing keeps it open",
            1f, compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
    }

    @Test
    fun appSurfaceHighlightsWhileFingerIsHeldAndClearsOnCancel() {
        showLauncher()
        val row = compose.onNodeWithTag("app:${apps[1].key}")
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        row.assertIsSelected()
        saveScreenshot("app-pressed.png")
        row.performTouchInput { cancel() }
        row.assertIsNotSelected()
        compose.onNodeWithTag("app_details").assertDoesNotExist()
    }

    @Test
    fun appLaunchProvidesTheIconBoundsForThePublicPlatformTransition() {
        var launched: LauncherApp? = null
        var bounds: Rect? = null
        var fallbackLaunches = 0
        showLauncher(
            actions = LauncherActions(launchAppAt = { app, source -> launched = app; bounds = source }),
            onLaunch = { fallbackLaunches++ },
        )
        val row = compose.onNodeWithTag("app:${apps[1].key}")
        val rowBounds = row.fetchSemanticsNode().boundsInWindow
        row.performClick()
        assertEquals(apps[1], launched)
        assertEquals(0, fallbackLaunches)
        val iconSize = 40f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertEquals(iconSize, bounds?.width ?: 0f, 1f)
        assertEquals(iconSize, bounds?.height ?: 0f, 1f)
        val iconBounds = checkNotNull(bounds)
        val inset = 8f * deviceDensity()
        assertTrue("The icon needs at least 8dp horizontal breathing room", iconBounds.left - rowBounds.left >= inset - 1f)
        assertTrue("The icon needs at least 8dp top padding", iconBounds.top - rowBounds.top >= inset - 1f)
        assertTrue("The icon needs at least 8dp bottom padding", rowBounds.bottom - iconBounds.bottom >= inset - 1f)
    }

    @Test
    fun appRowRippleChangesInteriorPixelsButStaysInsideRoundedCorners() {
        showLauncher()
        assertRoundedRipple(compose.onNodeWithTag("app:${apps[1].key}"), "ripple-app-row.png")
    }

    @Test
    fun settingsActionRippleChangesInteriorPixelsButStaysInsideRoundedCorners() {
        showLauncher()
        compose.onNodeWithTag("launcher_fab").performTouchInput { longClick() }
        awaitSurface("settings_root")
        assertRoundedRipple(compose.onNodeWithTag("settings_category_productivity"), "ripple-settings-action.png")
    }

    @Test
    fun shortcutHeaderAndItemRipplesStayInsideTheirOwnRoundedCorners() {
        showLauncher(actions = shortcutFixtureActions())
        compose.onNodeWithTag("app:${apps[1].key}").performTouchInput { swipeRight(durationMillis = 200) }
        awaitSurface("shortcut_popup")
        assertRoundedRipple(compose.onNodeWithTag("shortcut_header"), "ripple-shortcut-header.png")
        assertRoundedRipple(compose.onNodeWithTag("shortcut:compose"), "ripple-shortcut-row.png")
    }

    @Test
    fun floatingButtonRippleStaysInsideItsCircularSurface() {
        showLauncher()
        assertRoundedRipple(compose.onNodeWithTag("launcher_fab"), "ripple-settings-fab.png")
    }

    @Test
    fun longPressOpensDetailsAndFavoritesAreEditedOnlyInThePanel() {
        var toggles = 0
        showLauncher(onToggle = { toggles++ })
        compose.onNodeWithTag("app:${apps[1].key}").performTouchInput { longClick() }
        awaitSurface("app_details")
        compose.onNodeWithTag("app_details").assertIsDisplayed()
        listOf("Edit favorites", "App info", "Screen time", "Add to folder", "Uninstall", "Advanced", "Grace settings").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        assertEquals(0, toggles)
        val detailsBottom = compose.onNodeWithTag("app_details").fetchSemanticsNode().boundsInRoot.bottom
        val windowHeight = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.heightPixels
        assertTrue("The details sheet should be anchored to the bottom edge", detailsBottom > windowHeight * 0.91f)
        saveScreenshot("details-sample.png")
        compose.onNodeWithTag("edit_favorites").performScrollTo().performClick()
        awaitSurface("favorites_screen")
        compose.onNodeWithTag("favorites_screen").assertIsDisplayed()
        compose.onNodeWithTag("favorite:${apps[1].key}").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun detailsActionsAndExpandedAdvancedRowsAlignWithTheAppHeader() {
        showLauncher()
        compose.onNodeWithTag("app:${apps[1].key}").performTouchInput { longClick() }
        awaitSurface("app_details")
        val headerIcon = compose.onNodeWithTag("app_details_icon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val headerTitle = compose.onNodeWithTag("app_details_title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("advanced").performScrollTo().performClick()
        compose.onNodeWithTag("app_details_title", useUnmergedTree = true).performScrollTo()
        saveScreenshot("details-aligned.png")

        listOf(
            "edit_favorites", "App info", "Screen time", "Add to folder", "Uninstall",
            "advanced", "Rename app", "app_details_package", "grace_settings",
        ).forEach { tag ->
            // Keep the target visible on shorter screens; horizontal anchors do not scroll.
            compose.onNodeWithTag(tag).performScrollTo()
            val icon = compose.onNodeWithTag("$tag:icon", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("$tag:label", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals("$tag icon should share the header icon's center", headerIcon.center.x, icon.center.x, 1f)
            assertEquals("$tag label should share the app name's left edge", headerTitle.left, label.left, 1f)
        }
        val packageNode = compose.onNodeWithTag("app_details_package:label", useUnmergedTree = true)
        packageNode.performScrollTo()
        assertEquals(
            "The advanced package name should not introduce another text indent",
            headerTitle.left, packageNode.fetchSemanticsNode().boundsInRoot.left, 1f,
        )
    }

    @Test
    fun tappingDateShowsAgendaWithEventsAndNewEventAction() {
        var created = 0
        var opened: Long? = null
        val now = Instant.now()
        val holiday = LocalDate.now().plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant()
        showLauncher(
            events = listOf(
                ScheduleEvent(100, "Movie night", now.plusSeconds(1680), now.plusSeconds(7200), false, null, 0xFF53AABB.toInt()),
                ScheduleEvent(101, "Day off", holiday, holiday.plusSeconds(86400), true, null, 0xFFDC6676.toInt()),
                ScheduleEvent(102, "Reading", holiday.plusSeconds(86400), holiday.plusSeconds(172800), true, null, 0xFF20A675.toInt()),
                ScheduleEvent(103, "Design review", now.plusSeconds(518400), now.plusSeconds(522000), false, null, 0xFF8871C8.toInt()),
            ),
            actions = LauncherActions(newEvent = { created++ }, openEvent = { opened = it.id }),
        )
        compose.onNodeWithTag("home_date").performClick()
        awaitSurface("agenda_sheet")
        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
        compose.onNodeWithText("Your agenda").assertIsDisplayed()
        val dateLeft = compose.onNodeWithTag("agenda_date:${LocalDate.now()}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val eventTitleLeft = compose.onNode(
            hasText("Movie night") and hasAnyAncestor(hasTestTag("agenda_sheet")), useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot.left
        assertEquals("Agenda text keeps a small indent beneath its date", 36f * deviceDensity(), eventTitleLeft - dateLeft, 1f)
        assertEquals(
            "New event shares the event title column", eventTitleLeft,
            compose.onNodeWithText("New event", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f,
        )
        assertEquals(
            "Plus and calendar markers share one leading column",
            compose.onNodeWithTag("new_event:icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x,
            compose.onNodeWithTag("agenda_event_indicator:100", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x, 1f,
        )
        saveScreenshot("agenda-sample.png")
        compose.onNodeWithTag("agenda_event:101").performScrollTo()
        assertEquals(
            "All-day and timed events share the same text column", eventTitleLeft,
            compose.onNodeWithText("Day off", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f,
        )
        compose.onNodeWithTag("new_event").performScrollTo().performClick()
        assertEquals(1, created)
        compose.onNodeWithTag("agenda_event:100").performClick()
        assertEquals(100L, opened)
    }

    @Test
    fun permissionsAreRequestedOnlyAfterTheAgendaExplanation() {
        var requests = 0
        showLauncher(status = ScheduleStatus.PermissionRequired, onRequestCalendar = { requests++ })
        compose.onNodeWithTag("home_date").performClick()
        awaitSurface("agenda_sheet")
        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText("Connect calendar").performClick()
        assertEquals(1, requests)
    }

    @Test
    fun fullListsCanScrollToTheirLastAppWithoutOpeningShortcuts() {
        val manyApps = (1..24).map { index ->
            LauncherApp(ComponentName("test.app$index", "App.Activity"), "App $index", null)
        }
        showLauncher(displayedApps = manyApps, favoriteCount = 24)
        compose.onNodeWithTag("home_content").performScrollToIndex(24)
        compose.onNodeWithTag("app:${manyApps.last().key}").assertIsDisplayed()
        compose.onNodeWithTag("alphabet:A").performClick()
        compose.onNodeWithTag("app:${manyApps[2].key}").performTouchInput { swipeUp() }
        compose.onNodeWithTag("shortcut_popup").assertDoesNotExist()
        compose.onNodeWithTag("app_drawer").performScrollToIndex(24)
        compose.onNodeWithTag("app:${manyApps.last().key}").assertIsDisplayed()
    }

    private fun awaitSurface(tag: String) {
        // Modal windows attach asynchronously on a cold instrumentation launch.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun shortcutFixtureActions() = LauncherActions(shortcuts = {
        ShortcutResult(ShortcutStatus.Ready, listOf(
            LauncherShortcut("compose", it.packageName, "Compose", null),
            LauncherShortcut("inbox", it.packageName, "Inbox", null),
        ))
    })

    private fun deviceDensity() =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    /** Exercise the real rendered ripple, not just pressed/selected accessibility state. */
    private fun assertRoundedRipple(node: SemanticsNodeInteraction, screenshotName: String) {
        compose.waitForIdle()
        val before = node.captureToImage().asAndroidBitmap()
        val density = deviceDensity()
        val previousAutoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            // Close to the curve so even an early ripple would visibly leak without clipping.
            node.performTouchInput { down(Offset(width - 12f * density, 12f * density)) }
            compose.mainClock.advanceTimeBy(100)
            // Android's Material ripple draws on RenderThread, independent of Compose's clock.
            SystemClock.sleep(140)
            val pressed = node.captureToImage().asAndroidBitmap()
            val interiorX = (before.width - 12f * density).roundToInt().coerceIn(1, before.width - 2)
            val interiorY = (12f * density).roundToInt().coerceIn(1, before.height - 2)
            val cornerX = (before.width - 1f * density).roundToInt().coerceIn(1, before.width - 2)
            val cornerY = density.roundToInt().coerceIn(1, before.height - 2)
            assertTrue(
                "A real ripple should change interior pixels for $screenshotName",
                patchDifference(before, pressed, interiorX, interiorY) > 3,
            )
            assertTrue(
                "Ripple leaked outside the rounded corner for $screenshotName",
                patchDifference(before, pressed, cornerX, cornerY) <= 2,
            )
            saveScreenshot(screenshotName)
        } finally {
            node.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = previousAutoAdvance
            compose.waitForIdle()
        }
    }

    private fun patchDifference(before: Bitmap, after: Bitmap, x: Int, y: Int): Int {
        var largest = 0
        for (dx in -1..1) for (dy in -1..1) {
            val first = before.getPixel(x + dx, y + dy)
            val second = after.getPixel(x + dx, y + dy)
            for (shift in listOf(0, 8, 16)) {
                largest = maxOf(largest, abs(((first shr shift) and 255) - ((second shr shift) and 255)))
            }
        }
        return largest
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("ui-verification")!!
        directory.mkdirs()
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(directory, name)
        output.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Log.i("GraceUiVerification", "Screenshot: ${output.absolutePath}")
    }
}
