package com.niva.launcher.ui.overlays

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niva.launcher.R
import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.data.weather.WeatherStatus
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.ScheduleStatus
import com.niva.launcher.ui.components.LauncherIcon
import com.niva.launcher.ui.components.LauncherSymbol
import com.niva.launcher.ui.components.eventRemainingText
import com.niva.launcher.ui.weather.DailyWeather
import com.niva.launcher.ui.weather.HourlyWeather
import com.niva.launcher.ui.weather.WeatherSourceNote
import com.niva.launcher.ui.weather.weatherAgendaDays
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

// Keep a modest indent beneath each date, with one shared column for the plus
// and calendar-color marker and another for all agenda text.
private val AgendaItemInset = 4.dp
private val AgendaLeadingWidth = 24.dp
private val AgendaContentGap = 8.dp

@Composable
fun AgendaSheet(uiState: LauncherUiState, actions: LauncherActions, onRequestCalendar: () -> Unit) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = ZoneId.systemDefault()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val today = now.atZone(zone).toLocalDate()
    val weatherEnabled = uiState.settings.weatherEnabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val weather = uiState.weather.snapshot.takeIf { weatherEnabled }
    val forecastDays = uiState.settings.weatherForecastDays
    val forecasts = remember(weather?.daily, today, forecastDays) {
        weather?.daily.orEmpty().filter { it.date >= today && it.date < today.plusDays(forecastDays.toLong()) }.associateBy { it.date }
    }
    val events = uiState.events.takeIf { uiState.settings.calendarAgenda }.orEmpty()
    val groups = remember(events, forecasts, today, zone, forecastDays) {
        weatherAgendaDays(events, forecasts.keys.toList(), today, forecastDays, zone)
    }
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale) }
    val compactDateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val numericDateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    Column(Modifier.fillMaxWidth().height(panelWindowHeight() * 0.66f).padding(horizontal = 30.dp).testTag("agenda_sheet")) {
        PanelTitle(stringResource(R.string.your_agenda))
        if ((uiState.settings.calendarAgenda && uiState.scheduleStatus == ScheduleStatus.Loading && events.isEmpty()) ||
            (weatherEnabled && uiState.weather.status == WeatherStatus.Loading && weather == null)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("agenda_list"), contentPadding = PaddingValues(bottom = 20.dp)) {
            groups.forEach { (date, dayEvents) ->
                item(key = "day:$date") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val forecast = forecasts[date]
                        val current = weather?.current.takeIf { date == today }
                        val hasWeather = forecast != null || current != null
                        val compact = hasWeather && (maxWidth < 300.dp || configuration.fontScale >= 1.3f)
                        val rowDateFormat = when {
                            compact && maxWidth.value / configuration.fontScale < 150f -> numericDateFormat
                            compact -> compactDateFormat
                            else -> dateFormat
                        }
                        val difference = ChronoUnit.DAYS.between(today, date).toInt()
                        val relativeDay = if (difference == 0) stringResource(R.string.today) else stringResource(R.string.event_in_days, difference)
                        val spokenDate = "${date.format(dateFormat)}, $relativeDay"
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).testTag("agenda_day:$date"), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(date.format(rowDateFormat), modifier = Modifier.weight(1f, fill = false).testTag("agenda_date:$date")
                                    .semantics { contentDescription = spokenDate },
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!compact) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(relativeDay, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            if (hasWeather) {
                                Spacer(Modifier.width(10.dp))
                                DailyWeather(forecast, current, Modifier.testTag("agenda_weather:$date"))
                            }
                        }
                    }
                }
                if (date == today) {
                    if (weather != null) item(key = "today_weather") {
                        HourlyWeather(weather, now)
                        WeatherSourceNote(weather, now, actions.openBreezyWeather)
                    }
                    else if (weatherEnabled && uiState.weather.status != WeatherStatus.Loading) item(key = "weather_unavailable") {
                        WeatherConnectionNotice(uiState.weather.status, actions)
                    }
                    // Calendar access is independent of weather access: keep the forecast visible
                    // when the user has not connected a calendar, or its provider fails.
                    if (uiState.settings.calendarAgenda) {
                        when (uiState.scheduleStatus) {
                            ScheduleStatus.PermissionRequired -> item(key = "calendar_permission") {
                                Text(stringResource(R.string.calendar_permission_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onRequestCalendar) { Text(stringResource(R.string.connect_calendar)) }
                            }
                            ScheduleStatus.Error -> item(key = "calendar_error") {
                                Text(stringResource(R.string.calendar_error))
                                TextButton(onClick = actions.refreshAgenda) { Text(stringResource(R.string.retry)) }
                            }
                            else -> Unit
                        }
                        item(key = "new_event") {
                            NewAgendaEvent(actions.newEvent)
                        }
                    }
                }
                items(dayEvents, key = { "$date:${it.id}:${it.startsAt}" }) { event ->
                    AgendaEvent(event, now) { actions.openEvent(event) }
                }
                item(key = "space:$date") { Spacer(Modifier.height(20.dp)) }
            }
            if (uiState.settings.calendarAgenda && events.isEmpty() && uiState.scheduleStatus == ScheduleStatus.Ready) item {
                Text(stringResource(R.string.no_upcoming_events), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeatherConnectionNotice(status: WeatherStatus, actions: LauncherActions) {
    val (message, label, onClick) = when (status) {
        WeatherStatus.Disabled, WeatherStatus.Loading -> return
        WeatherStatus.NotInstalled -> Triple(R.string.weather_settings_not_installed, R.string.weather_settings_install, actions.installBreezyWeather)
        WeatherStatus.PermissionRequired -> Triple(R.string.weather_settings_permission, R.string.weather_settings_allow, actions.requestWeatherAccess)
        WeatherStatus.UnsupportedVersion -> Triple(R.string.weather_settings_incompatible, R.string.weather_settings_install, actions.installBreezyWeather)
        WeatherStatus.NoLocations -> Triple(R.string.weather_settings_no_locations, R.string.weather_settings_open, actions.openBreezyWeather)
        WeatherStatus.NoWeather, WeatherStatus.Ready -> Triple(R.string.weather_settings_no_data, R.string.weather_settings_open, actions.openBreezyWeather)
        WeatherStatus.Error -> Triple(R.string.weather_settings_unavailable, R.string.retry, actions.refreshWeather)
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    TextButton(onClick = onClick, modifier = Modifier.testTag("weather_connection_action")) { Text(stringResource(label)) }
}

@Composable
private fun NewAgendaEvent(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new_event")
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AgendaItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
            LauncherIcon(LauncherSymbol.Plus, modifier = Modifier.testTag("new_event:icon"), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(AgendaContentGap))
        Text(stringResource(R.string.new_event), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AgendaEvent(event: ScheduleEvent, now: Instant, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val timeFormat = remember(locale, clockPattern) { DateTimeFormatter.ofPattern(clockPattern, locale).withZone(ZoneId.systemDefault()) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (event.isAllDay) 48.dp else 66.dp).testTag("agenda_event:${event.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AgendaItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.width(5.dp).height(if (event.isAllDay) 22.dp else 40.dp)
                .testTag("agenda_event_indicator:${event.id}")
                .background(event.calendarColor?.let { Color(it).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
        Spacer(Modifier.width(AgendaContentGap))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 16.sp)
            if (!event.isAllDay) {
                Text("${timeFormat.format(event.startsAt)}–${timeFormat.format(event.endsAt)} · ${eventRemainingText(event, now)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
