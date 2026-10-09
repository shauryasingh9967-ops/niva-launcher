package com.niva.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niva.launcher.data.ThemeMode
import com.niva.launcher.ui.theme.NivaLauncherTheme

/** Both activities observe the same persisted appearance settings. */
@Composable
internal fun LauncherAppTheme(viewModel: LauncherViewModel, content: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = when (state.settings.darkMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    NivaLauncherTheme(
        darkTheme = dark,
        amoledMode = state.settings.amoledMode,
        dynamicColor = state.settings.useDynamicColors,
        seedColor = Color(state.settings.themeColor),
        fontId = state.settings.appFontId,
        content = content,
    )
}
