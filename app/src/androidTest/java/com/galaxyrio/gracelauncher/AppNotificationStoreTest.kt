package com.galaxyrio.gracelauncher

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.session.MediaSession
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.notifications.AppNotificationStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNotificationStoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = AppNotificationStore()
    private val cancelled = mutableListOf<String>()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun builder() = Notification.Builder(context, "notification-fixture")
        .setSmallIcon(R.drawable.ms_info).setContentTitle("Fixture title").setContentText("Short preview")
    @Suppress("DEPRECATION")
    private fun sbn(id: Int, notification: Notification = builder().build(), time: Long = id.toLong(), user: UserHandle = Process.myUserHandle()) =
        StatusBarNotification(context.packageName, context.packageName, id, null, Process.myUid(), 0, 0,
            notification, user, time)
    private fun notifications() = store.state.value.values.flatten()
    @Before fun connect() = main { store.connected(emptyList()) { cancelled += it; true } }

    @Test fun longTextAndNewerNotificationAreUsedWithoutMutatingSystemNotifications() = main {
        val body = "This is a long message. ".repeat(40)
        store.posted(sbn(1, builder().setStyle(Notification.BigTextStyle().bigText(body)).build()))
        assertEquals(body.trim(), notifications().single().text)
        store.posted(sbn(2))
        assertEquals(listOf(sbn(2).key, sbn(1).key), notifications().map { it.key })
        assertTrue(cancelled.isEmpty())
    }

    @Test fun messagingAndInboxStylesKeepTheirExpandedText() = main {
        val message = NotificationCompat.Builder(context, "notification-fixture").setSmallIcon(R.drawable.ms_info)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
                .addMessage("First message", 1, Person.Builder().setName("Alice").build())
                .addMessage("Second message", 2, Person.Builder().setName("Bob").build())).build()
        store.posted(sbn(1, message))
        assertEquals("Alice: First message\nBob: Second message", notifications().single().text)
        store.posted(sbn(1, builder().setStyle(Notification.InboxStyle().addLine("One").addLine("Two")).build()))
        assertEquals("One\nTwo", notifications().single().text)
    }

    @Test fun groupSummaryIsNotDuplicatedAndDismissingChildOnlyCancelsThatKey() = main {
        val summary = sbn(1, builder().setGroup("chat").setGroupSummary(true).build())
        val first = sbn(2, builder().setGroup("chat").build())
        val second = sbn(3, builder().setGroup("chat").build())
        listOf(summary, first, second).forEach(store::posted)
        assertEquals(setOf(first.key, second.key), notifications().map { it.key }.toSet())
        val item = notifications().first { it.key == first.key }
        assertTrue(store.dismiss(item.key, item.revision))
        assertEquals(listOf(first.key), cancelled)
        assertEquals(2, notifications().size) // Only the system removal callback commits dismissal.
        store.removed(first.key)
        assertEquals(second.key, notifications().single().key)
    }

    @Test fun ongoingAndUpdatedNotificationsCannotBeDismissedByAStaleGesture() = main {
        store.posted(sbn(1))
        val old = notifications().single()
        store.posted(sbn(1, builder().setContentText("Updated message").build(), time = 5))
        assertFalse(store.dismiss(old.key, old.revision))
        store.posted(sbn(1, builder().setOngoing(true).build()))
        val ongoing = notifications().single()
        assertFalse(ongoing.canDismiss)
        assertFalse(store.dismiss(ongoing.key, ongoing.revision))
        assertTrue(cancelled.isEmpty())
    }

    @Test fun mediaSecretAndOtherProfilesAreNotShownAsAppMessages() = main {
        val player = MediaSession(context, "notification-filter-test")
        try {
            store.posted(sbn(1, builder().setStyle(Notification.MediaStyle().setMediaSession(player.sessionToken)).build()))
            store.posted(sbn(2, builder().setVisibility(Notification.VISIBILITY_SECRET).build()))
            store.posted(sbn(3, user = UserHandle.getUserHandleForUid(1_000_000)))
            assertTrue(notifications().isEmpty())
        } finally { player.release() }
    }

    @Test fun disconnectClearsPrivateContentAndReconnectRebuildsOnlyCurrentNotifications() = main {
        store.posted(sbn(1))
        val old = notifications().single()
        store.disconnected()
        store.posted(sbn(2))
        assertTrue(notifications().isEmpty())
        assertFalse(store.dismiss(old.key, old.revision))
        store.connected(listOf(sbn(3))) { cancelled += it; true }
        assertEquals(sbn(3).key, notifications().single().key)
        store.ranking { false }
        assertTrue(notifications().isEmpty())
        store.ranking { true }
        assertEquals(1, notifications().size)
    }

    @Test fun missingOrStaleContentIntentDoesNotLaunchAnything() = main {
        store.posted(sbn(1))
        val item = notifications().single()
        assertFalse(item.canOpen)
        assertFalse(store.open(context, item.key, item.revision))
        val pending = PendingIntent.getActivity(context, 407, Intent(context, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        store.posted(sbn(1, builder().setContentIntent(pending).build()))
        assertTrue(notifications().single().canOpen)
        assertFalse(store.open(context, item.key, item.revision))
        pending.cancel()
        val updated = notifications().single()
        assertFalse(store.open(context, updated.key, updated.revision))
    }
}
