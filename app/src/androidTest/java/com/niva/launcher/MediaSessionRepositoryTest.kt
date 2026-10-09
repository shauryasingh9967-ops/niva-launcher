package com.niva.launcher

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.os.Build
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.media.MediaCommand
import com.niva.launcher.data.media.MediaSessionRepository
import com.niva.launcher.data.media.MediaSnapshot
import com.niva.launcher.data.media.MediaNotificationStore
import com.niva.launcher.data.media.mediaPlayerLaunchOptions
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real system MediaSessions; shell identity is scoped to tests, never the application. */
@RunWith(AndroidJUnit4::class)
class MediaSessionRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var repository: MediaSessionRepository
    private val sessions = mutableListOf<MediaSession>()
    private val notificationStore = MediaNotificationStore()
    private var allowed = true
    private val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

    @Before fun setUp() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL)
        main {
            notificationStore.connected(emptyList())
            repository = MediaSessionRepository(context, hasAccess = { allowed }, notifications = notificationStore.state)
            repository.setEnabled(true)
        }
    }

    @After fun tearDown() {
        try { main { repository.close(); sessions.forEach { it.release() } } }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(predicate: (MediaSnapshot) -> Boolean): MediaSnapshot = runBlocking {
        withTimeout(10_000) { repository.state.first(predicate) }
    }
    private fun playback(state: Int, supported: Long = actions) = PlaybackState.Builder()
        .setState(state, 0, if (state == PlaybackState.STATE_PLAYING) 1f else 0f).setActions(supported).build()
    private fun metadata(title: String, artwork: Bitmap? = null) = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Fixture artist")
        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork).build()
    private fun session(title: String, state: Int = PlaybackState.STATE_PLAYING, supported: Long = actions, notified: Boolean = true): MediaSession {
        lateinit var result: MediaSession
        main {
            result = MediaSession(context, "niva-test:$title").also {
                sessions += it
                it.setMetadata(metadata(title))
                it.setPlaybackState(playback(state, supported))
                it.isActive = true
            }
            if (notified) notificationStore.posted(notification(result))
            repository.refresh()
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun notification(player: MediaSession, id: Int = System.identityHashCode(player), media: Boolean = true): StatusBarNotification {
        val builder = Notification.Builder(context, "niva-media-fixture")
            .setSmallIcon(R.drawable.ms_music_note)
            .setContentTitle("Notification text must not become song metadata")
        if (media) builder.setStyle(Notification.MediaStyle().setMediaSession(player.sessionToken))
        return StatusBarNotification(context.packageName, context.packageName, id, null, Process.myUid(),
            0, 0, builder.build(), Process.myUserHandle(), System.currentTimeMillis())
    }

    @Test fun metadataArtworkAndTransportControlsFollowTheRealController() {
        val player = session("Niva media fixture")
        val next = AtomicInteger()
        val previous = AtomicInteger()
        main {
            player.setCallback(object : MediaSession.Callback() {
                override fun onPause() { player.setPlaybackState(playback(PlaybackState.STATE_PAUSED)) }
                override fun onPlay() { player.setPlaybackState(playback(PlaybackState.STATE_PLAYING)) }
                override fun onSkipToNext() { next.incrementAndGet(); player.setMetadata(metadata("Niva next fixture")) }
                override fun onSkipToPrevious() { previous.incrementAndGet(); player.setMetadata(metadata("Niva previous fixture")) }
            }, Handler(Looper.getMainLooper()))
            player.setMetadata(metadata("Niva media fixture", Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)))
        }
        val current = await { it.nowPlaying?.title == "Niva media fixture" && it.nowPlaying.artwork != null }.nowPlaying!!
        assertEquals("Fixture artist", current.artist)
        assertEquals(512, current.artwork!!.width)
        main { assertTrue(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
        await { it.nowPlaying?.sessionId == current.sessionId && !it.nowPlaying.playing }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
        await { it.nowPlaying?.sessionId == current.sessionId && it.nowPlaying.playing }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.Next)) }
        await { it.nowPlaying?.title == "Niva next fixture" }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.Previous)) }
        await { it.nowPlaying?.title == "Niva previous fixture" }
        assertEquals(1, next.get()); assertEquals(1, previous.get())
    }

    @Test fun disablingOrRevokingAccessClearsThePlayerAndRejectsStaleCommands() {
        session("Niva access fixture")
        val current = await { it.nowPlaying?.title == "Niva access fixture" }.nowPlaying!!
        main { repository.setEnabled(false) }
        assertNull(repository.state.value.nowPlaying)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.Next)); repository.setEnabled(true) }
        await { it.nowPlaying?.title == "Niva access fixture" }
        main { allowed = false; repository.refresh() }
        assertFalse(repository.state.value.hasAccess)
        assertNull(repository.state.value.nowPlaying)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)); allowed = true; repository.refresh() }
        val resumed = await { it.nowPlaying?.title == "Niva access fixture" }.nowPlaying!!
        assertNotEquals(current.sessionId, resumed.sessionId)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
    }

    @Test fun unsupportedActionsAndDestroyedSessionsDoNotRemainControllable() {
        val player = session("Niva limited fixture", supported = PlaybackState.ACTION_PAUSE)
        val current = await { it.nowPlaying?.title == "Niva limited fixture" }.nowPlaying!!
        assertTrue(current.canToggle); assertFalse(current.canPrevious); assertFalse(current.canNext)
        main {
            assertFalse(repository.command(current.sessionId, MediaCommand.Next))
            assertFalse(repository.command("obsolete-session", MediaCommand.TogglePlayback))
            player.release()
            sessions.remove(player)
        }
        await { it.nowPlaying?.sessionId != current.sessionId }
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
    }

    @Test fun playingNotificationWinsOverPausedNotification() {
        session("Niva paused fixture", PlaybackState.STATE_PAUSED)
        val playing = session("Niva active fixture")
        val current = await { it.nowPlaying?.title == "Niva active fixture" }.nowPlaying!!
        main { playing.setPlaybackState(playback(PlaybackState.STATE_STOPPED)) }
        await { it.nowPlaying?.sessionId != current.sessionId }
    }

    @Test fun pausedNotificationStaysUntilRemovedAndLateSessionCallbacksCannotReviveIt() {
        val player = session("Niva paused notification", PlaybackState.STATE_PAUSED)
        val current = await { it.nowPlaying?.title == "Niva paused notification" }.nowPlaying!!
        assertFalse(current.playing)
        main { notificationStore.removed(notification(player).key) }
        await { it.nowPlaying == null }
        main {
            player.setMetadata(metadata("Late metadata"))
            player.setPlaybackState(playback(PlaybackState.STATE_PLAYING))
            repository.refresh()
            assertNull(repository.state.value.nowPlaying)
            assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback))
            notificationStore.posted(notification(player))
        }
        val reposted = await { it.nowPlaying?.title == "Late metadata" }.nowPlaying!!
        assertNotEquals(current.sessionId, reposted.sessionId)
    }

    @Test fun evenPlayingSessionsWithoutNotificationsStayHidden() {
        val player = session("Niva unnotified", notified = false)
        assertNull(repository.state.value.nowPlaying)
        val visible = session("Niva visible paused", PlaybackState.STATE_PAUSED)
        await { it.nowPlaying?.title == "Niva visible paused" }
        main { player.setPlaybackState(playback(PlaybackState.STATE_PLAYING)); repository.refresh() }
        assertEquals("Niva visible paused", repository.state.value.nowPlaying?.title)
        // Both sessions belong to the same package: package-only matching is not sufficient.
        main { notificationStore.removed(notification(visible).key) }
        await { it.nowPlaying == null }
    }

    @Test fun stoppedOrInactiveSessionStillShowsWhileItsNotificationExists() {
        val player = session("Niva retained notification", PlaybackState.STATE_PAUSED)
        val current = await { it.nowPlaying?.title == "Niva retained notification" }.nowPlaying!!
        main {
            player.setPlaybackState(playback(PlaybackState.STATE_STOPPED, PlaybackState.ACTION_PLAY))
            player.isActive = false
            repository.refresh()
        }
        val retained = await { it.nowPlaying?.sessionId == current.sessionId && it.nowPlaying.canToggle && !it.nowPlaying.canNext }.nowPlaying!!
        assertFalse(retained.playing)
        main { notificationStore.removed(notification(player).key) }
        await { it.nowPlaying == null }
    }

    @Test fun reconnectRebuildsOnlyTheCurrentNotificationSnapshot() {
        val player = session("Niva reconnect", PlaybackState.STATE_PAUSED)
        await { it.nowPlaying?.title == "Niva reconnect" }
        main { notificationStore.disconnected() }
        await { it.nowPlaying == null }
        main { notificationStore.connected(listOf(notification(player))) }
        await { it.nowPlaying?.title == "Niva reconnect" }
        main {
            notificationStore.disconnected()
            notificationStore.posted(notification(player)) // Ignore late callbacks while disconnected.
            notificationStore.connected(emptyList())
            repository.refresh()
        }
        assertNull(repository.state.value.nowPlaying)
    }

    @Test fun duplicateNotificationsAreCountedAndAnOrdinaryReplacementRemovesTheMediaRef() {
        val player = session("Niva duplicates", PlaybackState.STATE_PAUSED)
        await { it.nowPlaying?.title == "Niva duplicates" }
        main {
            notificationStore.posted(notification(player, id = 42))
            notificationStore.removed(notification(player).key)
            repository.refresh()
        }
        assertEquals("Niva duplicates", repository.state.value.nowPlaying?.title)
        main { notificationStore.posted(notification(player, id = 42, media = false)) }
        await { it.nowPlaying == null }
    }

    @Test fun missingOrUnreadableArtworkDoesNotBlockSongInformation() {
        val player = session("Niva missing art")
        main {
            player.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Niva inaccessible art")
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "content://unavailable.niva.test/cover").build())
        }
        val current = await { it.nowPlaying?.title == "Niva inaccessible art" }.nowPlaying!!
        assertNull(current.artwork)
        assertTrue(current.canToggle)
    }

    @Test fun localDismissDoesNotControlPlaybackAndSurvivesProgressRefreshUntilStateChanges() {
        val player = session("Swipe fixture")
        val controls = AtomicInteger()
        main {
            player.setCallback(object : MediaSession.Callback() {
                override fun onPause() { controls.incrementAndGet() }
                override fun onStop() { controls.incrementAndGet() }
            }, Handler(Looper.getMainLooper()))
        }
        val current = await { it.nowPlaying?.title == "Swipe fixture" }.nowPlaying!!
        main {
            assertTrue(repository.dismiss(current.sessionId, current.revision))
            assertNull(repository.state.value.nowPlaying)
            player.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 12_345, 1f).setActions(actions).build())
            player.setMetadata(metadata("Swipe fixture", Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)))
            repository.refresh()
        }
        instrumentation.waitForIdleSync()
        main { assertNull(repository.state.value.nowPlaying); assertEquals(0, controls.get()) }
        assertEquals(PlaybackState.STATE_PLAYING, player.controller.playbackState!!.state)
        main { player.setPlaybackState(playback(PlaybackState.STATE_PAUSED)) }
        val restored = await { it.nowPlaying?.playing == false }.nowPlaying!!
        assertTrue(restored.revision > current.revision)
        assertEquals(current.sessionId, restored.sessionId)
        main { assertFalse(repository.dismiss(current.sessionId, current.revision)) }
    }

    @Test fun aNewTrackRevealsLocallyDismissedPlayerWithoutCancellingItsNotification() {
        val player = session("First track", PlaybackState.STATE_PAUSED)
        val current = await { it.nowPlaying?.title == "First track" }.nowPlaying!!
        main {
            repository.dismiss(current.sessionId, current.revision)
            assertEquals(1, notificationStore.state.value.notifications.size)
            player.setMetadata(metadata("Second track"))
        }
        val restored = await { it.nowPlaying?.title == "Second track" }.nowPlaying!!
        assertFalse(restored.playing)
        assertTrue(restored.revision > current.revision)
    }

    @Suppress("DEPRECATION")
    @Test fun playerPendingIntentUsesTheStrictestAvailableForegroundOptIn() {
        if (Build.VERSION.SDK_INT >= 34) {
            val expected = if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            assertEquals(expected, mediaPlayerLaunchOptions().pendingIntentBackgroundActivityStartMode)
        }
    }
}
