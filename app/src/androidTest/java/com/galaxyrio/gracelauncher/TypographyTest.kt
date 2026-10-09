package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.theme.LauncherFontFamily
import com.galaxyrio.gracelauncher.ui.theme.Typography as LauncherTypography
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf("Calendar", "Chrome").map { label ->
        LauncherApp(ComponentName("test.${label.lowercase()}", "$label.Activity"), label, null)
    }

    @Test
    fun everyMaterialTypeScaleRoleUsesTheLauncherFamily() {
        val roles = with(LauncherTypography) {
            linkedMapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
                "displayLargeEmphasized" to displayLargeEmphasized, "displayMediumEmphasized" to displayMediumEmphasized, "displaySmallEmphasized" to displaySmallEmphasized,
                "headlineLargeEmphasized" to headlineLargeEmphasized, "headlineMediumEmphasized" to headlineMediumEmphasized, "headlineSmallEmphasized" to headlineSmallEmphasized,
                "titleLargeEmphasized" to titleLargeEmphasized, "titleMediumEmphasized" to titleMediumEmphasized, "titleSmallEmphasized" to titleSmallEmphasized,
                "bodyLargeEmphasized" to bodyLargeEmphasized, "bodyMediumEmphasized" to bodyMediumEmphasized, "bodySmallEmphasized" to bodySmallEmphasized,
                "labelLargeEmphasized" to labelLargeEmphasized, "labelMediumEmphasized" to labelMediumEmphasized, "labelSmallEmphasized" to labelSmallEmphasized,
            )
        }
        assertEquals(30, roles.size)
        roles.forEach { (name, style) -> assertEquals(name, LauncherFontFamily, style.fontFamily) }
    }

    @Test
    fun clockDateScheduleRailAndDrawerHeadingsKeepTheLauncherFamily() {
        showLauncher()
        assertTextFamily(compose.onNodeWithTag("home_clock", useUnmergedTree = true))
        assertTextFamily(compose.onNodeWithTag("home_date_text", useUnmergedTree = true))
        assertTextFamily(compose.onNodeWithText("Movie night", useUnmergedTree = true))
        assertTextFamily(compose.onNode(
            hasText("C") and hasAnyAncestor(hasTestTag("alphabet:C")), useUnmergedTree = true,
        ))
        assertTextFamily(compose.onNode(
            hasText("Calendar") and hasAnyAncestor(hasTestTag("app:${apps[0].key}")) and
                hasAnyAncestor(hasTestTag("home_content")),
            useUnmergedTree = true,
        ))
        saveScreenshot("typography-home.png")
        compose.onNodeWithTag("alphabet:C").performClick()
        assertTextFamily(compose.onNodeWithTag("section:C", useUnmergedTree = true))
        saveScreenshot("typography-drawer.png")
    }

    @Test
    fun fullScreenSettingsInheritFamilyIncludingSmallTextAndSegmentedItems() {
        showLauncher()
        compose.onNodeWithTag("launcher_fab").performTouchInput { longClick() }
        awaitSurface("settings_root")
        // The flexible app bar keeps both expanded and collapsed titles composed.
        val titles = compose.onAllNodesWithText(string(R.string.settings_title), useUnmergedTree = true)
        assertTrue(titles.fetchSemanticsNodes().isNotEmpty())
        repeat(titles.fetchSemanticsNodes().size) { assertTextFamily(titles[it]) }
        assertTextFamily(compose.onNodeWithText(string(R.string.settings_productivity_summary), useUnmergedTree = true))
        compose.onNodeWithTag("settings_category_themes").performClick()
        assertTextFamily(compose.onNodeWithText(string(R.string.settings_dynamic_colors_summary), useUnmergedTree = true))
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hide_status_bar"))
        val description = compose.onNodeWithText(string(R.string.settings_hide_status_bar_summary), useUnmergedTree = true)
        assertTextFamily(description)
        saveScreenshot("typography-settings.png")
    }

    @Test
    fun detailsAdvancedPackageAndRenameFieldInheritFamilyThroughNestedThemes() {
        showLauncher()
        compose.onNodeWithTag("app:${apps[0].key}").performTouchInput { longClick() }
        awaitSurface("app_details")
        assertTextFamily(compose.onNodeWithTag("app_details_title", useUnmergedTree = true))
        assertTextFamily(compose.onNodeWithTag("edit_favorites:label", useUnmergedTree = true))
        compose.onNodeWithTag("advanced").performScrollTo().performClick()
        val packageName = compose.onNodeWithTag("app_details_package", useUnmergedTree = true)
        packageName.performScrollTo()
        assertTextFamily(packageName)
        compose.onNodeWithTag(string(R.string.rename_app)).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertTextFamily(compose.onNode(hasSetTextAction(), useUnmergedTree = true))
        assertTextFamily(compose.onNodeWithText(string(R.string.save), useUnmergedTree = true))
        saveScreenshot("typography-rename.png")
    }

    @Test
    fun multilingualSampleUsesTheLauncherFamilyForEveryTextRun() {
        val samples = listOf(
            Triple("Latin", "en-US", "Grace launcher · 09:41"),
            Triple("Chinese", "zh-CN", "中文 · 今天的日程"),
            Triple("Japanese", "ja-JP", "日本語 · 今日の予定"),
            Triple("Korean", "ko-KR", "한국어 · 오늘의 일정"),
            Triple("Greek", "el-GR", "Ελληνικά · Καλημέρα"),
            Triple("Cyrillic", "ru-RU", "Русский · Доброе утро"),
            Triple("Arabic", "ar", "العربية · صباح الخير"),
            Triple("Hebrew", "he", "עברית · בוקר טוב"),
            Triple("Devanagari", "hi-IN", "हिन्दी · सुप्रभात"),
            Triple("Thai", "th-TH", "ไทย · สวัสดีตอนเช้า"),
        )
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Josefin Sans + Noto Sans", style = MaterialTheme.typography.headlineSmall)
                    Text("Grace 09:41 · 中文 · 日本語 · 한국어", style = MaterialTheme.typography.bodyMedium)
                    samples.forEach { (label, locale, sample) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                sample,
                                modifier = Modifier.testTag("language:$locale"),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 22.sp,
                                    lineHeight = 30.sp,
                                    localeList = LocaleList(Locale(locale)),
                                ),
                            )
                        }
                    }
                }
            }
        }
        samples.forEach { (_, locale, _) ->
            assertTextFamily(compose.onNodeWithTag("language:$locale", useUnmergedTree = true))
        }
        saveScreenshot("typography-languages.png")
    }

    private fun showLauncher() {
        val now = Instant.now()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(
                            apps = apps,
                            favoriteKeys = apps.mapTo(linkedSetOf(), LauncherApp::key),
                            isLoadingApps = false,
                            events = listOf(ScheduleEvent(
                                1, "Movie night", now.plusSeconds(1680), now.plusSeconds(7200), false, null, null,
                            )),
                            scheduleStatus = ScheduleStatus.Ready,
                            textMode = WallpaperTextMode.Light,
                        ),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                    )
                }
            }
        }
    }

    private fun assertTextFamily(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertTrue("Expected a rendered text layout", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertEquals("Unexpected family for ${layout.layoutInput.text}", LauncherFontFamily, layout.layoutInput.style.fontFamily)
        }
    }

    private fun awaitSurface(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("ui-verification")!!
        directory.mkdirs()
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val output = File(directory, name)
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("GraceUiVerification", "Screenshot: ${output.absolutePath}")
    }
}
