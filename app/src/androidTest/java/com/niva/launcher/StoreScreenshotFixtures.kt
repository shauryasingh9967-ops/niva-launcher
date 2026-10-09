package com.niva.launcher

import android.app.Instrumentation
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.niva.launcher.data.*
import com.niva.launcher.data.icons.IconPackRepository
import com.niva.launcher.data.icons.IconPackStatus
import com.niva.launcher.data.media.NowPlaying
import com.niva.launcher.data.notifications.AppNotification
import com.niva.launcher.data.weather.*
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.ScheduleStatus
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking

/** No fixture data is written to the user's calendar, notifications or launcher database. */
internal class StoreScreenshotFixtures(private val context: Context, private val instrumentation: Instrumentation) {
    private var player: MediaPlayer? = null

    fun launcherState(): LauncherUiState = runBlocking {
        val packs = IconPackRepository(context)
        val installed = packs.installedPacks()
        check(installed.any { it.packageName == Monocons }) { "Install Monocons ($Monocons) before capturing." }
        val apps = AppRepository(context, packs).loadApps(listOf(Monocons))
        val favorites = listOf(
            "com.google.android.dialer", "com.google.android.apps.messaging", "com.google.android.gm",
            "com.android.chrome", "com.android.camera2", "com.google.android.calendar",
        ).map { pkg -> apps.first { it.packageName == pkg }.key }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        fun event(id: Long, title: String, start: Instant, minutes: Long, color: Int) =
            ScheduleEvent(id, title, start, start.plusSeconds(minutes * 60), false, null, color)
        val events = listOf(
            event(1, "Design review", now.plusSeconds(30 * 60), 45, 0xFF81C7B4.toInt()),
            event(2, "Coffee with Alex", now.plusSeconds(2 * 60 * 60), 30, 0xFFB7B8F0.toInt()),
            event(3, "Walk along the Bund", today.plusDays(1).atTime(9, 0).atZone(zone).toInstant(), 60, 0xFFE9BD8C.toInt()),
            event(4, "Read a chapter", today.plusDays(1).atTime(19, 0).atZone(zone).toInstant(), 30, 0xFF81C7B4.toInt()),
        )
        val weather = sampleWeather(now)
        LauncherUiState(
            apps = apps, favoriteKeys = favorites.toSet(), favoriteOrder = favorites,
            hiddenAppKeys = apps.filter { it.packageName == "com.niva.composetemplate" }.map { it.key }.toSet(),
            isLoadingApps = false, isDefaultHome = true, hasShortcutAccess = true,
            textMode = WallpaperTextMode.Light, themedIcons = true,
            scheduleStatus = ScheduleStatus.Ready, events = events,
            weather = WeatherState(WeatherStatus.Ready, weather, listOf(weather.location)),
            iconPacks = installed, iconPackStatus = IconPackStatus.Ready,
            settings = LauncherSettings(
                showBatteryPercentage = false, useDynamicColors = true, darkMode = ThemeMode.Dark,
                iconPackPackages = listOf(Monocons), weatherEnabled = true, weatherForecastDays = 5,
                weatherLocationId = weather.location.id,
            ),
        )
    }

    private fun sampleWeather(now: Instant): WeatherSnapshot {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = now.atZone(zone).toLocalDate()
        fun temperature(value: Int) = WeatherTemperature(value.toDouble(), "c")
        return WeatherSnapshot(
            location = WeatherLocation("store-shanghai", "Shanghai", zone),
            current = WeatherCurrent(temperature(24), WeatherCondition.PartlyCloudy, true, "Partly cloudy"),
            hourly = (1..12).map { hour ->
                val at = now.plusSeconds(hour * 3600L)
                WeatherHour(at, temperature(listOf(25, 26, 26, 25, 24, 23, 22, 21, 21, 20, 20, 19)[hour - 1]),
                    if (hour <= 3) WeatherCondition.Clear else WeatherCondition.PartlyCloudy,
                    at.atZone(zone).hour in 6..18, null)
            },
            daily = listOf(WeatherCondition.PartlyCloudy, WeatherCondition.Clear, WeatherCondition.Rain,
                WeatherCondition.Cloudy, WeatherCondition.Clear).mapIndexed { day, condition ->
                WeatherDay(today.plusDays(day.toLong()), temperature(26 - day), temperature(19 - day / 2), condition, null)
            },
            updatedAt = now.minusSeconds(5 * 60), attribution = "Sample forecast",
        )
    }

    fun gmailShortcuts(gmail: LauncherApp) = ShortcutResult(ShortcutStatus.Ready, listOf(
        LauncherShortcut("compose", gmail.packageName, "Compose",
            requireNotNull(ContextCompat.getDrawable(context, R.drawable.ms_edit)).toBitmap(96, 96).asImageBitmap()),
        LauncherShortcut("inbox", gmail.packageName, "Inbox", gmail.icon),
    ))

    fun gmailNotifications(gmail: LauncherApp) = mapOf(gmail.packageName to listOf(
        AppNotification("store-gmail-1", gmail.packageName, "Alex Morgan",
            "Sunday plans · Brunch at 11? I've saved a table by the window.",
            System.currentTimeMillis() - 5 * 60_000, 1, true, true),
        AppNotification("store-gmail-2", gmail.packageName, "Design team",
            "Ready for review · The updated mockups are in our shared folder.",
            System.currentTimeMillis() - 20 * 60_000, 1, true, true),
    ))

    /** Plays the actual device file and uses its embedded title, artist and cover. */
    fun playDeviceSong(): NowPlaying {
        stopSong()
        // Read through the instrumentation shell: the launcher itself deliberately
        // has no media-library permission. The temporary copy never leaves cache.
        val query = instrumentation.uiAutomation.executeShellCommand(
            "content query --uri content://media/external/audio/media --projection _id",
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) } }
        val ids = Regex("_id=(\\d+)").findAll(query).map { it.groupValues[1] }.toList()
        check(ids.size == 1) { "Keep exactly one demo song on the capture device (found ${ids.size})." }
        val song = File.createTempFile("store-song-", ".audio", context.cacheDir)
        try {
            instrumentation.uiAutomation.executeShellCommand(
                "content read --uri content://media/external/audio/media/${ids.single()}",
            ).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input -> song.outputStream().use(input::copyTo) }
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(song.absolutePath)
                val artworkBytes = requireNotNull(retriever.embeddedPicture) { "The device song needs embedded cover art." }
                val artwork = requireNotNull(BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)).asImageBitmap()
                val title = requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                player = MediaPlayer().apply {
                    setDataSource(song.absolutePath)
                    isLooping = true
                    prepare()
                    start()
                }
                check(player!!.isPlaying)
                return NowPlaying("store-device-song", "Music", title, artist, artwork,
                    playing = true, canToggle = true, canPrevious = true, canNext = true)
            } finally { retriever.release() }
        } finally { song.delete() }
    }

    fun stopSong() { player?.release(); player = null }

    companion object { const val Monocons = "k4ustu3h.monocons.izzy" }
}
