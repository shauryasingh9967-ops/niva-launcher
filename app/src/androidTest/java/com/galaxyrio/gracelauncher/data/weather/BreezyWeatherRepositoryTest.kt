package com.galaxyrio.gracelauncher.data.weather

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreezyWeatherRepositoryTest {
    /**
     * Optional integration test against the user's installed app; never changes its locations/settings.
     * Grant READ_PROVIDER before the suite and restore afterward in the test runner: revoking this
     * runtime permission from inside instrumentation would kill the target process.
     */
    @Test fun installedBreezyProviderHasAReadableSupportedContract() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = BreezyWeatherRepository(context)
        assumeTrue("Install Breezy Weather 6.1+ for the integration test", repository.installedPackage() != null)
        assumeTrue(
            "Grant Breezy's READ_PROVIDER permission before this integration test",
            context.checkSelfPermission(BreezyWeatherRepository.READ_PERMISSION) == PackageManager.PERMISSION_GRANTED,
        )
        val state = repository.load()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("breezy_status", state.status.name)
            putInt("breezy_location_count", state.locations.size)
            putInt("breezy_hourly_count", state.snapshot?.hourly?.size ?: 0)
            putInt("breezy_daily_count", state.snapshot?.daily?.size ?: 0)
            putString(
                "stream",
                "\nBreezy provider: status=${state.status}, locations=${state.locations.size}, " +
                    "hourly=${state.snapshot?.hourly?.size ?: 0}, daily=${state.snapshot?.daily?.size ?: 0}\n",
            )
        })
        assertTrue(
            "Real provider must return weather or an explicit empty-data state, but was ${state.status}",
            state.status in setOf(WeatherStatus.Ready, WeatherStatus.NoWeather, WeatherStatus.NoLocations),
        )
        if (state.status == WeatherStatus.Ready) {
            val snapshot = requireNotNull(state.snapshot)
            assertTrue(state.locations.isNotEmpty())
            assertTrue(state.locations.any { it.id == state.selectedLocationId })
            assertTrue(snapshot.location.name.isNotBlank())
            assertTrue(snapshot.attribution.isNotBlank())
            assertTrue(snapshot.current != null || snapshot.hourly.isNotEmpty() || snapshot.daily.isNotEmpty())
            assertEquals(snapshot.daily.sortedBy { it.date }, snapshot.daily)
            assertEquals(snapshot.hourly.sortedBy { it.at }, snapshot.hourly)
            listOfNotNull(snapshot.current?.temperature).plus(snapshot.hourly.mapNotNull { it.temperature })
                .forEach { assertTrue(it.value.isFinite()); assertTrue(it.unit in setOf("c", "f", "k")) }
        }
    }
}
