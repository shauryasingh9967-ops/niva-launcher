package com.niva.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelUuid
import android.os.Process
import android.view.SurfaceControl
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.niva.launcher.platform.AppLaunchTransition
import com.niva.launcher.platform.GestureNavContract
import com.niva.launcher.platform.LauncherAppTransitions
import com.niva.launcher.platform.LauncherIconTarget
import com.niva.launcher.platform.selectHomeTarget
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class LauncherAppTransitionsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val component = ComponentName("test.app", "test.app.Main")
    private val scope = MainScope()
    private var transitions: LauncherAppTransitions? = null

    @After fun tearDown() {
        compose.runOnIdle { transitions?.close(); scope.cancel() }
    }

    private fun showHome() {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize())
        }
        compose.waitForIdle()
    }

    private fun icon(home: Boolean = true, folder: Boolean = false,
        app: ComponentName = component, bounds: Rect? = Rect(30, 200, 78, 248)) =
        LauncherIconTarget(listOf(app), home, folder, view, { bounds }, {
            Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        })

    @Test fun returnSelectsHomeIconOrFolderAndRejectsStaleTargets() {
        showHome()
        compose.runOnIdle {
            val drawer = icon(home = false)
            val folder = icon(folder = true)
            val alias = icon(app = ComponentName(component.packageName, "test.app.Alias"))
            val exact = icon()
            val unplaced = icon(bounds = null)
            assertSame(exact, selectHomeTarget(listOf(drawer, folder, alias, exact, unplaced), component))
            assertSame(alias, selectHomeTarget(listOf(drawer, folder, alias, unplaced), component))
            assertSame(folder, selectHomeTarget(listOf(drawer, folder, unplaced), component))
            assertNull(selectHomeTarget(listOf(drawer, unplaced), component))
            assertNull(selectHomeTarget(listOf(exact), ComponentName("different.app", "different.app.Main")))
        }
    }

    @Test fun launchBoundsUseScreenCoordinatesAndInvalidBoundsFallBack() {
        showHome()
        compose.runOnIdle {
            val window = IntArray(2).also(view::getLocationInWindow)
            val screen = IntArray(2).also(view::getLocationOnScreen)
            val bounds = Rect(30, 200, 78, 248)
            val expected = Rect(bounds).apply { offset(screen[0] - window[0], screen[1] - window[1]) }
            assertEquals(expected, AppLaunchTransition.fromIcon(view, bounds)?.sourceBounds)
            assertNull(AppLaunchTransition.fromIcon(view, Rect()))
            assertNull(AppLaunchTransition.fromIcon(View(view.context), bounds))
        }
    }

    @Test fun finishRestoresIconAndAnOldFinishCannotCloseANewGesture() {
        showHome()
        val replies = LinkedBlockingQueue<Message>()
        val messenger = Messenger(Handler(Looper.getMainLooper()) { replies.add(Message.obtain(it)); true })
        lateinit var target: LauncherIconTarget
        fun contract(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .putExtra(GestureNavContract.EXTRA_CONTRACT, Bundle().apply {
                putParcelable(Intent.EXTRA_COMPONENT_NAME, component)
                putParcelable(Intent.EXTRA_USER, Process.myUserHandle())
                putParcelable(GestureNavContract.EXTRA_REMOTE_CALLBACK, Message.obtain().apply {
                    replyTo = messenger; obj = ParcelUuid(UUID.randomUUID())
                })
            })
        fun finishMessage(): Message {
            compose.waitUntil(3000) { replies.any { BundleCompat.getParcelable(it.data,
                GestureNavContract.EXTRA_ICON_SURFACE, SurfaceControl::class.java)?.isValid == true } }
            return requireNotNull(BundleCompat.getParcelable(replies.last().data,
                GestureNavContract.EXTRA_ON_FINISH_CALLBACK, Message::class.java))
        }
        compose.runOnIdle {
            var context = view.context
            while (context !is Activity) context = (context as ContextWrapper).baseContext
            transitions = LauncherAppTransitions(context, scope)
            target = icon()
            transitions!!.register(target)
            transitions!!.onHomeIntent(contract())
        }
        val oldFinish = finishMessage()
        compose.runOnIdle {
            assertTrue(target.hidden)
            transitions!!.onHomeIntent(contract())
            replies.clear()
        }
        val newFinish = finishMessage()
        oldFinish.replyTo.send(oldFinish)
        compose.runOnIdle { assertTrue("A stale callback must not restore the current icon", target.hidden) }
        newFinish.replyTo.send(newFinish)
        compose.waitUntil(3000) { !target.hidden }
        compose.runOnIdle { transitions!!.close(); assertFalse(target.hidden) }
    }
}
