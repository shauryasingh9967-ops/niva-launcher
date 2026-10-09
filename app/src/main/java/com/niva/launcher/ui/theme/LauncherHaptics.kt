package com.niva.launcher.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/** The launcher can disable feedback, but never overrides the system's choice. */
@Composable
internal fun rememberLauncherHaptics(enabled: Boolean): HapticFeedback {
    val context = LocalContext.current
    val delegate = LocalHapticFeedback.current
    return remember(context, delegate, enabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled && Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0) {
                    delegate.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
}
