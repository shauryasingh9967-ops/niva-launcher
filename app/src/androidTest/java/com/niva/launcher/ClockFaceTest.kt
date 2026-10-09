package com.niva.launcher

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.niva.launcher.data.ClockLayout
import com.niva.launcher.data.ClockStyle
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.home.HomeScreen
import com.niva.launcher.ui.theme.NivaLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockFaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopRendersSavedLayoutAndRemainsClickable() {
        var clicks = 0
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        state = state.copy(settings = state.settings.copy(clockStyle = ClockStyle(
            layout = ClockLayout.TwoLines,
            twoLines = ClockStyle.defaults(ClockLayout.TwoLines).copy(weight = 600, size = 60, letterSpacing = 2),
        )))
        compose.setContent {
            NivaLauncherTheme(dynamicColor = false) {
                HomeScreen(state, 24.dp, {}, {}, { _, _ -> }, {}, { clicks++ })
            }
        }
        fun clockText() = compose.onNodeWithTag("home_clock").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val stackedHeight = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.height
        assertEquals(2, clockText().lines().size)
        assertFalse(clockText().contains(':'))
        compose.onNodeWithTag("home_clock").performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            val single = ClockStyle().withFace(ClockStyle().face.copy(showColon = true))
            state = state.copy(settings = state.settings.copy(clockStyle = single))
        }
        assertEquals(1, clockText().lines().size)
        assertTrue(clockText().contains(':'))
        assertTrue(stackedHeight > compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.height)
    }
}
