package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherAppOrder
import com.galaxyrio.gracelauncher.data.sectionForLabel
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.drawer.DrawerItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the device's real ICU data, rather than Android's local-unit-test stubs. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LauncherSortingTest {
    @Test
    fun supportedScriptsShareLatinSections() {
        mapOf(
            "相机" to "X", "微信" to "W", "支付寶" to "Z",
            "カメラ" to "K", "카메라" to "K", "Камера" to "K", "Κάμερα" to "K",
            "Éditeur" to "E", "İstanbul" to "I", "Ｃａｍｅｒａ" to "C",
            "1Password" to "#",
        ).forEach { (label, section) -> assertEquals(label, section, sectionForLabel(label)) }
    }

    @Test
    fun pinyinAndEnglishInterleaveInBothTheModelAndAppOrder() {
        val apps = listOf("支付宝", "WhatsApp", "相机", "Bing", "微信", "百度", "Alipay").mapIndexed { i, label ->
            LauncherApp(ComponentName("example.sorting", "App$i"), label, null)
        }
        val expected = listOf("Alipay", "百度", "Bing", "微信", "WhatsApp", "相机", "支付宝")
        assertEquals(expected, apps.sortedWith(LauncherAppOrder).map { it.label })
        assertEquals(expected, AppListModel(apps).items.filterIsInstance<DrawerItem.App>().map { it.app.label })
        assertEquals("A", apps.first().copy(label = "Alipay").section)
    }
}
