package com.niva.launcher

import android.os.ParcelFileDescriptor
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.media.MediaAccess
import com.niva.launcher.data.notifications.AppNotification
import com.niva.launcher.data.notifications.appNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the existing user-approved listener; never grants access or touches real messages. */
@RunWith(AndroidJUnit4::class)
class SystemNotificationIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val tag = "niva-notification-fixture-${System.currentTimeMillis()}"
    private val title = "Niva_integration_fixture"
    private fun own(items: Map<String, List<AppNotification>>) = items["com.android.shell"].orEmpty().filter {
        it.title == title && it.key.contains(tag)
    }
    private fun post(body: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cmd notification post -t $title $tag $body",
        )).bufferedReader().use { it.readText() }
    }
    @After fun removeOnlyOurFixture() = instrumentation.runOnMainSync {
        own(appNotifications.state.value).forEach { appNotifications.dismiss(it.key, it.revision) }
    }
    @Test fun realListenerReceivesUpdatesAndCancelsOnlyTheRequestedSystemNotification() {
        assumeTrue("Enable Niva notification access on the test device first", MediaAccess.isGranted(context))
        NotificationListenerService.requestRebind(MediaAccess.component(context))
        post("First_synthetic_message")
        val first = runBlocking { withTimeout(12_000) {
            own(appNotifications.state.first { own(it).any { it.text == "First_synthetic_message" } }).single()
        } }
        assertTrue(first.canDismiss)
        post("Updated_synthetic_message")
        val updated = runBlocking { withTimeout(12_000) {
            own(appNotifications.state.first { own(it).any { it.text == "Updated_synthetic_message" } }).single()
        } }
        assertEquals(first.key, updated.key)
        assertNotEquals(first.revision, updated.revision)
        instrumentation.runOnMainSync {
            assertFalse(appNotifications.dismiss(first.key, first.revision))
            assertTrue(appNotifications.dismiss(updated.key, updated.revision))
        }
        runBlocking { withTimeout(12_000) { appNotifications.state.first { own(it).isEmpty() } } }
    }
}
