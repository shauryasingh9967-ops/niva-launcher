package com.niva.launcher

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.FavoritesStore
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.ui.LauncherUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FavoritesStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "favorites-test-${UUID.randomUUID()}"
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val apps = listOf("Alpha", "Beta", "Clock", "Delta").map {
        LauncherApp(ComponentName("test.$it", "$it.Activity"), it, null)
    }
    private val keys = apps.map(LauncherApp::key)

    @After fun cleanUp() { context.deleteSharedPreferences(name) }

    @Test fun migratesUnorderedFavoritesWithoutDroppingUnavailableOrHiddenApps() {
        prefs.edit().putStringSet("favorite_components", setOf(keys[3], keys[0], keys[2], "missing/app")).commit()
        val expected = listOf(keys[0], keys[2], keys[3], "missing/app")
        assertEquals(expected, FavoritesStore(prefs).favoritesFor(apps))
        assertEquals(expected, FavoritesStore(prefs).favoritesFor(apps.reversed()))
    }

    @Test fun reorderedSubsetPreservesMembershipAndInvisibleSlotsAcrossReopen() {
        val original = listOf(keys[0], "hidden/app", keys[1], keys[2], "uninstalled/app")
        prefs.edit().putString("favorite_component_order", JSONArray(original).toString()).commit()
        val store = FavoritesStore(prefs)
        val updated = store.reorder(listOf(keys[2], keys[2], "not-favorite/app", keys[0], keys[1]), store.favoritesFor(apps))
        assertEquals(listOf(keys[2], "hidden/app", keys[0], keys[1], "uninstalled/app"), updated)
        assertEquals(original.toSet(), updated.toSet())
        assertEquals(updated, FavoritesStore(prefs).favoritesFor(apps.reversed()))
        assertEquals(updated.toSet(), prefs.getStringSet("favorite_components", emptySet()))
    }

    @Test fun additionsAppendAndRemovingEveryFavoriteDoesNotReseedOnRestart() {
        val store = FavoritesStore(prefs)
        var order = store.toggle(keys[2], listOf(keys[0], keys[1]))
        assertEquals(listOf(keys[0], keys[1], keys[2]), order)
        order = store.toggle(keys[1], order)
        order = store.toggle(keys[1], order)
        assertEquals(listOf(keys[0], keys[2], keys[1]), order)
        order.toList().forEach { order = store.toggle(it, order) }
        assertTrue(FavoritesStore(prefs).favoritesFor(apps).isEmpty())
    }

    @Test fun damagedOrderFallsBackToTheExistingSelection() {
        prefs.edit().putString("favorite_component_order", "broken").putStringSet("favorite_components", setOf(keys[2], keys[1])).commit()
        assertEquals(listOf(keys[1], keys[2]), FavoritesStore(prefs).favoritesFor(apps))
    }

    @Test fun homeUsesExplicitOrderRegardlessOfAppListVisibility() {
        val order = keys.reversed()
        val state = LauncherUiState(apps = apps, favoriteKeys = keys.toSet(), favoriteOrder = order, hiddenAppKeys = setOf(keys[1]))
        assertEquals(order, state.favoriteApps.map(LauncherApp::key))
        assertEquals(keys - keys[1], state.appListApps.map(LauncherApp::key))
        assertEquals(order, state.copy(hiddenAppKeys = emptySet()).favoriteApps.map(LauncherApp::key))
        assertEquals(order, state.copy(apps = apps.reversed(), hiddenAppKeys = emptySet()).favoriteApps.map(LauncherApp::key))
    }

    @Test fun changingOnlyOrderIsNotLostToSetEquality() {
        val flow = MutableStateFlow(LauncherUiState(apps = apps, favoriteKeys = keys.toSet(), favoriteOrder = keys))
        flow.value = flow.value.copy(favoriteOrder = keys.reversed())
        assertEquals(keys.reversed(), flow.value.favoriteApps.map(LauncherApp::key))
        assertEquals(keys.toSet(), flow.value.favoriteKeys)
    }
}
