package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNotificationUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("fixture.chat", "ChatActivity"), "Messages", null)
    private val browser = LauncherApp(ComponentName("fixture.browser", "BrowserActivity"), "Chrome", null)
    private val message = AppNotification("message-1", app.packageName, "Two new messages",
        "My computer: This is a longer message preview. ".repeat(4).trim(), System.currentTimeMillis(), 1, true, true)
    private var notifications by mutableStateOf(listOf(message))
    private val dismissed = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var launchedApps = 0

    private fun show(drawer: Boolean = false, shortcutAccess: Boolean = true) {
        val shortcuts = ShortcutResult(if (shortcutAccess) ShortcutStatus.Ready else ShortcutStatus.DefaultLauncherRequired,
            if (shortcutAccess) listOf(LauncherShortcut("compose", app.packageName, "Compose", null),
                LauncherShortcut("inbox", app.packageName, "Inbox", null)) else emptyList())
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF25302F))) {
                    LauncherScreen(
                        LauncherUiState(apps = listOf(browser, app), favoriteKeys = setOf(browser.key, app.key),
                            isLoadingApps = false, hasShortcutAccess = shortcutAccess, textMode = WallpaperTextMode.Light,
                            notifications = mapOf(app.packageName to notifications)),
                        onDateClick = {}, onClockClick = {}, onToggleFavorite = {}, onLaunchApp = { launchedApps++ },
                        initialDrawerOpen = drawer,
                        actions = LauncherActions(
                            shortcuts = { shortcuts }, cachedShortcuts = { shortcuts },
                            dismissNotification = { key, revision ->
                                val current = notifications.firstOrNull { it.key == key && it.revision == revision }
                                if (current == null) false else {
                                    dismissed += key
                                    notifications = notifications.filterNot { it.key == key }
                                    true
                                }
                            },
                            openNotification = { key, _ -> opened += key; true },
                        ),
                    )
                }
            }
        }
    }

    @Test fun homePreviewArrowOpensNotificationAboveShortcutsAndLeftSwipeClearsOnlyIt() {
        show()
        compose.onNode(hasText("Two new messages") and hasAnyAncestor(hasTestTag("home_content")), useUnmergedTree = true).assertIsDisplayed()
        screenshot("notifications-home.png")
        compose.onNodeWithTag("notification_arrow:app:${app.key}").performClick()
        val notification = compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val shortcut = compose.onNodeWithTag("shortcut:compose").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(notification.bottom <= shortcut.top)
        screenshot("notifications-popup.png")
        compose.onNodeWithTag("notification:${message.key}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("notification:${message.key}").assertDoesNotExist()
        compose.onNodeWithTag("shortcut:compose").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(message.key), dismissed); assertEquals(0, launchedApps); assertTrue(opened.isEmpty()) }
    }

    @Test fun drawerRightSwipeOpensSamePanelAndRightDismissKeepsOtherMessages() {
        notifications = listOf(message, message.copy(key = "message-2", title = "Another message", revision = 2))
        show(drawer = true)
        compose.onNodeWithTag("app:${app.key}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed().performTouchInput { swipeRight() }
        compose.onNodeWithTag("notification:${message.key}").assertDoesNotExist()
        compose.onNodeWithTag("notification:message-2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(message.key), dismissed); assertEquals(0, launchedApps) }
    }

    @Test fun ongoingNotificationsAndCancelledDragsAreNotDismissedOrOpened() {
        notifications = listOf(message.copy(canDismiss = false))
        show()
        compose.onNodeWithTag("notification_arrow:app:${app.key}").performClick()
        screenshot("notifications-ongoing-before.png")
        compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed()
        compose.onNodeWithTag("notification:${message.key}").performTouchInput { swipeLeft() }
        screenshot("notifications-ongoing-after.png")
        compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed()
        compose.runOnIdle { notifications = listOf(message.copy(revision = 2)) }
        compose.onNodeWithTag("notification:${message.key}").performTouchInput {
            down(center); moveBy(Offset(-50f, 0f), 250); moveBy(Offset.Zero, 250); up()
        }
        compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dismissed.isEmpty()); assertTrue(opened.isEmpty()) }
    }

    @Test fun notificationWorksWithoutShortcutHostPermissionAndClickUsesItsIntent() {
        show(shortcutAccess = false)
        compose.onNodeWithTag("notification_arrow:app:${app.key}").performClick()
        compose.onNodeWithTag("notification:${message.key}").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("shortcut_popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(message.key), opened); assertEquals(0, launchedApps); assertTrue(dismissed.isEmpty()) }
    }

    @Test fun swipeBackgroundFollowsTheFingerAndANewRevisionResetsTheGesture() {
        show()
        compose.onNodeWithTag("notification_arrow:app:${app.key}").performClick()
        val row = compose.onNodeWithTag("notification:${message.key}")
        row.performTouchInput { down(center); moveBy(Offset(-width * 0.4f, 0f), 250) }
        screenshot("notifications-swipe.png")
        compose.runOnIdle { notifications = listOf(message.copy(title = "Updated notification", revision = 2)) }
        row.performTouchInput { up() }
        row.assertIsDisplayed()
        compose.onNodeWithText("Updated notification", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertTrue(dismissed.isEmpty()); assertTrue(opened.isEmpty()) }
    }

    @Test fun longNotificationCanScrollToShortcutsWithoutHorizontalDismissal() {
        notifications = listOf(message.copy(text = "A long message that must remain readable. ".repeat(160)))
        show()
        compose.onNodeWithTag("notification_arrow:app:${app.key}").performClick()
        compose.onNodeWithTag("shortcut_list").performScrollToNode(hasTestTag("shortcut:compose"))
        compose.onNodeWithTag("shortcut:compose").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dismissed.isEmpty()); assertTrue(opened.isEmpty()) }
    }

    @Test fun searchDoesNotInheritNotificationArrowsWithoutAShortcutHost() {
        show()
        compose.onNodeWithTag("notification_arrow:app:${app.key}").assertIsDisplayed()
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.onAllNodesWithTag("notification_arrow:app:${app.key}").assertCountEquals(0)
        compose.onNodeWithTag("app_search_query").performTextReplacement(app.label)
        compose.onNodeWithTag("app:${app.key}").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, launchedApps); assertTrue(opened.isEmpty()) }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification")).also { it.mkdirs() }
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
