package com.niva.launcher

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.ClockLayout
import com.niva.launcher.data.ClockStyle
import com.niva.launcher.data.HomeLayout
import com.niva.launcher.data.media.MediaSnapshot
import com.niva.launcher.platform.HomeWidgetHost
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherScreen
import com.niva.launcher.ui.drawer.AppListModel
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.FileInputStream
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in captures of production UI, installed Monocons icons and local demonstration data. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun captureEnglishStoreListing() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("storeScreenshots") == "true")
        check(context.resources.configuration.locales[0].language == "en") {
            "Use an English emulator with Monocons installed."
        }
        val selected = arguments.getString("storeScreenshotScenes")?.split(',')?.toSet()
        val fixture = StoreScreenshotFixtures(context, instrumentation)
        val base = fixture.launcherState()
        val gmail = base.apps.single { it.packageName == "com.google.android.gm" }
        val shortcuts = fixture.gmailShortcuts(gmail)
        var state by mutableStateOf(base)
        val actions = LauncherActions(
            shortcuts = { shortcuts }, cachedShortcuts = { shortcuts },
            updateSettings = { change -> state = state.copy(settings = change(state.settings)) },
            themedIcons = { state = state.copy(themedIcons = it) },
            textMode = { state = state.copy(textMode = it) },
        )
        enableWallpaperColors()
        var widgetId: Int? = null
        val widgetHost = HomeWidgetHost(context)
        try {
            StoreScenes.filter { selected == null || it.name.take(2) in selected }.forEach { current ->
                applyWallpaper(current)
                var next = base
                when (current.name.take(2)) {
                    "03" -> next = base.copy(
                        media = MediaSnapshot(true, fixture.playDeviceSong()),
                        settings = base.settings.copy(homeLayout = HomeLayout(topOffsetDp = -75f)),
                    )
                    "05" -> next = base.copy(notifications = fixture.gmailNotifications(gmail),
                        shortcutApps = shortcuts.shortcuts.map { shortcut ->
                            shortcut.asApp(gmail).copy(
                                monochromeIcon = if (shortcut.id == "compose") shortcut.icon else gmail.monochromeIcon,
                                monochromeScale = if (shortcut.id == "compose") 0.6f else gmail.monochromeScale,
                            )
                        })
                    "06" -> {
                        val manager = AppWidgetManager.getInstance(context)
                        val provider = manager.installedProviders.first {
                            it.provider.packageName == "com.android.chrome" &&
                                it.provider.className.endsWith("QuickActionSearchWidgetProviderSearch")
                        }
                        val id = widgetHost.allocateAppWidgetId().also { widgetId = it }
                        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
                        try {
                            check(manager.bindAppWidgetIdIfAllowed(id, provider.provider)) { "Could not bind the Chrome search widget." }
                        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
                        next = base.copy(settings = base.settings.copy(homeLayout = HomeLayout(
                            topOffsetDp = -165f, widgetId = id, widgetProvider = provider.provider.flattenToString(),
                            widgetLabel = provider.loadLabel(context.packageManager), widgetHeightDp = 112,
                        )))
                    }
                    "08" -> next = base.copy(settings = base.settings.copy(clockStyle = ClockStyle(layout = ClockLayout.Sacramento)))
                }
                // Applying Material You overlays can recreate the activity. Attach the
                // real screens to the current activity only after those overlays settle.
                compose.runOnUiThread {
                    state = next
                    compose.activity.enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    )
                    compose.activity.window.apply {
                        addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    }
                    compose.activity.setContent {
                      key(current.name) {
                        NivaLauncherTheme(darkTheme = true, dynamicColor = true) {
                            if (current.page != null) LauncherSettingsScreen(
                                state, actions, onBack = {}, initialPage = current.page,
                                initialIconDesignerApp = gmail.takeIf { current.page == "IconDesigner" },
                            ) else LauncherScreen(state, onDateClick = {}, onClockClick = {},
                                onLaunchApp = {}, onToggleFavorite = {}, actions = actions)
                        }
                      }
                    }
                    WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                        if (current.page != null) show(WindowInsetsCompat.Type.statusBars())
                    }
                }
                when (current.name.take(2)) {
                    "01" -> {
                        compose.onNodeWithTag("home_clock").assertIsDisplayed()
                        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertIsDisplayed()
                    }
                    "02" -> {
                        compose.onNodeWithTag("home_date").performClick()
                        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
                        compose.onNodeWithTag("weather_location").assertIsDisplayed()
                        compose.onNodeWithTag("weather_location").assertTextContains("Shanghai", substring = true)
                        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
                        compose.onNodeWithTag("agenda_event:1").assertIsDisplayed()
                        compose.onNodeWithTag("agenda_event:2").assertIsDisplayed()
                    }
                    "03" -> compose.onNodeWithTag("home_media_artwork").assertIsDisplayed()
                    "04" -> {
                        val letters = AppListModel(base.appListApps).letters
                        compose.onNodeWithTag("alphabet_rail").performTouchInput {
                            down(Offset(centerX, height * (letters.indexOf("C") + 1.5f) / (letters.size + 1)))
                            moveBy(Offset(-35f, 0f), delayMillis = 250)
                        }
                        compose.onNodeWithTag("section:C").assertIsDisplayed()
                        compose.onNodeWithTag("alphabet_indicator", useUnmergedTree = true).assertIsDisplayed()
                    }
                    "05" -> {
                        compose.onNodeWithTag("app:${gmail.key}").performTouchInput { swipeRight() }
                        compose.onNodeWithTag("notification:store-gmail-1").assertIsDisplayed()
                        compose.onNodeWithTag("shortcut:compose").assertIsDisplayed()
                    }
                    "06" -> {
                        compose.onNodeWithTag("home_clock").assertIsDisplayed()
                        SystemClock.sleep(1_000)
                        compose.waitUntil(10_000) { widgetTexts().none { it.contains("loading", ignoreCase = true) } }
                        compose.onNodeWithText(context.getString(R.string.widget_unavailable)).assertDoesNotExist()
                    }
                    "07" -> compose.onNodeWithTag("settings_themes").assertIsDisplayed()
                    "08" -> {
                        compose.onNodeWithTag("clock_layout:Sacramento").performScrollTo().assertIsSelected()
                        compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true).assertIsDisplayed()
                    }
                    "09" -> compose.onNodeWithTag("icon_pack_selected:${StoreScreenshotFixtures.Monocons}").assertIsDisplayed()
                    "10" -> {
                        compose.waitUntil(10_000) {
                            compose.onAllNodesWithTag("icon_designer_selected_app").fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.onNodeWithTag("icon_designer_selected_app").assertIsDisplayed()
                    }
                }
                save(current.name)
                if (current.name.startsWith("04")) compose.onNodeWithTag("alphabet_rail").performTouchInput { cancel() }
                fixture.stopSong()
            }
        } finally {
            fixture.stopSong()
            widgetId?.let(widgetHost::deleteAppWidgetId)
        }
    }

    private fun widgetTexts(): List<String> {
        val texts = mutableListOf<String>()
        compose.runOnUiThread {
            fun visit(view: View) {
                if (view is TextView) texts += view.text.toString()
                if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
            }
            visit(compose.activity.window.decorView)
        }
        return texts
    }

    private fun enableWallpaperColors() {
        val setting = "theme_customization_overlay_packages"
        val theme = JSONObject(Settings.Secure.getString(context.contentResolver, setting) ?: "{}")
        listOf("system_palette", "accent_color", "color_index").forEach {
            theme.remove("android.theme.customization.$it")
        }
        theme.put("android.theme.customization.color_source", "home_wallpaper")
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try { Settings.Secure.putString(context.contentResolver, setting, theme.toString()) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun applyWallpaper(scene: StoreScene) {
        val manager = WallpaperManager.getInstance(context)
        val oldAccent = context.getColor(android.R.color.system_accent1_200)
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, 1080f, 2400f, Paint().apply {
            shader = LinearGradient(0f, 0f, 1080f, 2400f,
                scene.colors.map(android.graphics.Color::parseColor).toIntArray(), null, Shader.TileMode.CLAMP)
        })
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.SET_WALLPAPER")
        try { manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity(); bitmap.recycle() }
        // Wait for SystemUI to generate and install the Material You color overlays.
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (context.getColor(android.R.color.system_accent1_200) == oldAccent && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200)
        }
        SystemClock.sleep(700)
        println("Store screenshot ${scene.name}: dynamic accent ${context.getColor(android.R.color.system_accent1_200).toUInt().toString(16)}")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val directory = "/sdcard/Download/niva-launcher-store-screenshots"
        listOf("mkdir -p $directory", "screencap -p $directory/$name").forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                val output = FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
                check(output.isBlank()) { output }
            }
        }
    }
}

private data class StoreScene(val name: String, val colors: List<String>, val page: String? = null)

private val StoreScenes = listOf(
    StoreScene("01-home.png", listOf("#102F36", "#285A59", "#72A69D")),
    StoreScene("02-agenda.png", listOf("#172743", "#354B77", "#9DAED1")),
    StoreScene("03-music.png", listOf("#301C2A", "#6D3A48", "#C89280")),
    StoreScene("04-app-list-c.png", listOf("#101C3B", "#284985", "#6A98C7")),
    StoreScene("05-gmail-shortcuts.png", listOf("#192A26", "#3C5B43", "#9AA777")),
    StoreScene("06-home-widget.png", listOf("#32251F", "#6F4B35", "#CCA275")),
    StoreScene("07-themes.png", listOf("#29223E", "#5B4B83", "#B7A5D5"), "Themes"),
    StoreScene("08-clock-style.png", listOf("#392538", "#805569", "#D0A0AD"), "ClockStyle"),
    StoreScene("09-icon-packs.png", listOf("#132D41", "#295D71", "#7DB4B7"), "IconPacks"),
    StoreScene("10-icon-designer.png", listOf("#302039", "#724668", "#C28AB1"), "IconDesigner"),
)
