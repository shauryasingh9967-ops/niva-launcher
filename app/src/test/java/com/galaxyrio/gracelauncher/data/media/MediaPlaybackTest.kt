package com.galaxyrio.gracelauncher.data.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaybackTest {
    @Test fun playingAndBufferingTakePriorityOverPaused() {
        listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_REWINDING).forEach {
            assertEquals(2, playbackPriority(it))
        }
        assertEquals(1, playbackPriority(PlaybackState.STATE_PAUSED))
    }

    @Test fun nonPlayingSessionsHaveLowestSelectionPriority() {
        listOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR, -1).forEach {
            assertEquals(0, playbackPriority(it))
        }
    }
}
