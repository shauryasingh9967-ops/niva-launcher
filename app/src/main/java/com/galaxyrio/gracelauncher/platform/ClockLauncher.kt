package com.galaxyrio.gracelauncher.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

internal enum class ClockLaunchResult { Opened, NoHandler, Failed }

internal object ClockLauncher {
    fun open(context: Context, appKey: String? = null): ClockLaunchResult = try {
        // Let Android honor its default app / resolver. A visibility-filtered
        // resolveActivity query must not prevent an otherwise valid launch.
        val intent = if (appKey == null) Intent(AlarmClock.ACTION_SHOW_ALARMS) else {
            val component = requireNotNull(ComponentName.unflattenFromString(appKey)) { "Invalid clock app component" }
            // Launch exactly the saved activity, just like its app-list entry.
            // Do not fall back silently if the user's chosen app was removed.
            Intent.makeMainActivity(component)
        }
        context.startActivity(intent)
        ClockLaunchResult.Opened
    } catch (error: ActivityNotFoundException) {
        Log.i("ClockLauncher", "No activity handles the clock action (custom=${appKey != null})", error)
        ClockLaunchResult.NoHandler
    } catch (error: Exception) {
        // Permission/OEM failures are not evidence that no clock is installed.
        Log.e("ClockLauncher", "Unable to open the clock (custom=${appKey != null})", error)
        ClockLaunchResult.Failed
    }
}
