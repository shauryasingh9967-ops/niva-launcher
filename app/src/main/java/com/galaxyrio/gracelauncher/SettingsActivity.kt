package com.galaxyrio.gracelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.galaxyrio.gracelauncher.ui.LauncherAppTheme
import com.galaxyrio.gracelauncher.ui.LauncherRoute
import com.galaxyrio.gracelauncher.ui.LauncherViewModel

/** The only MAIN/LAUNCHER entry. HOME remains a separate system-owned task. */
class SettingsActivity : ComponentActivity() {
    private val launcherViewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherAppTheme(launcherViewModel) {
                LauncherRoute(launcherViewModel, settingsOnly = true, onCloseSettings = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcherViewModel.refreshApps()
        launcherViewModel.refreshSchedule()
    }
}
