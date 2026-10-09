package com.niva.launcher

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.niva.launcher.ui.theme.NivaLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherShapeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeAndSettingsDialogShapesUseMaterialDefaults() {
        var actualShapes: Shapes? = null
        var dialogShape: Shape? = null
        compose.setContent {
            NivaLauncherTheme(dynamicColor = false) {
                val shapes = MaterialTheme.shapes
                val dialog = AlertDialogDefaults.shape
                SideEffect { actualShapes = shapes; dialogShape = dialog }
            }
        }
        compose.runOnIdle {
            val defaults = Shapes()
            val actual = requireNotNull(actualShapes)
            assertEquals(defaults.extraSmall, actual.extraSmall)
            assertEquals(defaults.small, actual.small)
            assertEquals(defaults.medium, actual.medium)
            assertEquals(defaults.large, actual.large)
            assertEquals(defaults.extraLarge, actual.extraLarge)
            assertEquals(defaults.extraLarge, dialogShape)
        }
    }
}
