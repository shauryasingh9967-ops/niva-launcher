package com.galaxyrio.gracelauncher

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.platform.ClockLaunchResult
import com.galaxyrio.gracelauncher.platform.ClockLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockLauncherTest {
    private class RecordingContext(private val failure: Exception? = null) :
        ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val launches = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            launches += intent
            failure?.let { throw it }
        }
    }

    @Test fun alwaysAttemptsTheUnrestrictedStandardIntentFirst() {
        val context = RecordingContext()
        assertEquals(ClockLaunchResult.Opened, ClockLauncher.open(context))
        val intent = context.launches.single()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, intent.action)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertNull(intent.selector)
    }

    @Test fun missingHandlerIsReportedWithoutTryingAnotherAlarmIntent() {
        val context = RecordingContext(ActivityNotFoundException("No clock handler"))
        assertEquals(ClockLaunchResult.NoHandler, ClockLauncher.open(context))
        assertEquals(1, context.launches.size)
    }

    @Test fun permissionFailureIsNotMisreportedAsAMissingApp() {
        val context = RecordingContext(SecurityException("Clock activity is protected"))
        assertEquals(ClockLaunchResult.Failed, ClockLauncher.open(context))
        assertEquals(1, context.launches.size)
    }

    @Test fun selectedClockLaunchesThatExactLauncherActivity() {
        val context = RecordingContext()
        val component = ComponentName("selected.clock", "selected.clock.Main")
        assertEquals(ClockLaunchResult.Opened, ClockLauncher.open(context, component.flattenToString()))
        val intent = context.launches.single()
        assertEquals(component, intent.component)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        org.junit.Assert.assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test fun removedCustomAppDoesNotSilentlyLaunchTheDefaultClock() {
        val context = RecordingContext(ActivityNotFoundException("Uninstalled"))
        assertEquals(ClockLaunchResult.NoHandler, ClockLauncher.open(context, "removed/removed.Clock"))
        assertEquals(1, context.launches.size)
        assertEquals(Intent.ACTION_MAIN, context.launches.single().action)
    }

    @Test fun malformedSavedChoiceFailsSafely() {
        val context = RecordingContext()
        assertEquals(ClockLaunchResult.Failed, ClockLauncher.open(context, "not-a-component"))
        assertEquals(0, context.launches.size)
    }
}
