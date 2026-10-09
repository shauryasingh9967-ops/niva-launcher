package com.niva.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelUuid
import android.os.Process
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.niva.launcher.platform.GestureNavContract
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class GestureNavContractTest {
    private val replies = LinkedBlockingQueue<Message>()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) {
        replies.add(Message.obtain(it))
        true
    })
    private val component = ComponentName("test.app", "test.app.Main")
    private val gestureToken = ParcelUuid(UUID.randomUUID())

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .putExtra(GestureNavContract.EXTRA_CONTRACT, Bundle().apply {
            putParcelable(Intent.EXTRA_COMPONENT_NAME, component)
            putParcelable(Intent.EXTRA_USER, Process.myUserHandle())
            putParcelable(GestureNavContract.EXTRA_REMOTE_CALLBACK, Message.obtain().apply {
                replyTo = messenger
                obj = gestureToken
                what = 42
            })
        })

    @Test fun replyPreservesQuickstepTokenAndRoutesFinishCallback() {
        val intent = homeIntent()
        val contract = requireNotNull(GestureNavContract.fromIntent(intent))
        assertEquals(component, contract.component)
        assertEquals(Process.myUserHandle(), contract.user)
        assertFalse(intent.hasExtra(GestureNavContract.EXTRA_CONTRACT))
        assertNull(GestureNavContract.fromIntent(intent))

        val finishToken = ParcelUuid(UUID.randomUUID())
        val finish = Message.obtain().apply { replyTo = messenger; obj = finishToken }
        val position = RectF(15f, 120f, 63f, 168f)
        assertTrue(contract.sendEndPosition(position, null, finish))
        val reply = requireNotNull(replies.poll(2, TimeUnit.SECONDS))
        assertEquals(gestureToken, reply.obj)
        assertEquals(42, reply.what)
        assertEquals(position, BundleCompat.getParcelable(reply.data,
            GestureNavContract.EXTRA_ICON_POSITION, RectF::class.java))
        val receivedFinish = requireNotNull(BundleCompat.getParcelable(reply.data,
            GestureNavContract.EXTRA_ON_FINISH_CALLBACK, Message::class.java))
        receivedFinish.replyTo.send(receivedFinish)
        assertEquals(finishToken, requireNotNull(replies.poll(2, TimeUnit.SECONDS)).obj)
    }

    @Test fun incompleteOrNonHomeContractsAreIgnored() {
        assertNull(GestureNavContract.fromIntent(homeIntent().setAction(Intent.ACTION_VIEW)))
        assertNull(GestureNavContract.fromIntent(homeIntent().apply { removeCategory(Intent.CATEGORY_HOME) }))
        for (key in listOf(Intent.EXTRA_COMPONENT_NAME, Intent.EXTRA_USER, GestureNavContract.EXTRA_REMOTE_CALLBACK)) {
            val intent = homeIntent()
            intent.getBundleExtra(GestureNavContract.EXTRA_CONTRACT)!!.remove(key)
            assertNull(GestureNavContract.fromIntent(intent))
            assertFalse(intent.hasExtra(GestureNavContract.EXTRA_CONTRACT))
        }
        val noMessenger = homeIntent().apply {
            getBundleExtra(GestureNavContract.EXTRA_CONTRACT)!!.putParcelable(
                GestureNavContract.EXTRA_REMOTE_CALLBACK, Message.obtain())
        }
        assertNull(GestureNavContract.fromIntent(noMessenger))
    }

    @Test fun invalidGeometryIsNeverSentToQuickstep() {
        val contract = requireNotNull(GestureNavContract.fromIntent(homeIntent()))
        for (bounds in listOf(RectF(), RectF(4f, 0f, 2f, 10f),
            RectF(Float.NaN, 0f, 5f, 10f), RectF(0f, 0f, Float.POSITIVE_INFINITY, 10f))) {
            assertFalse(contract.sendEndPosition(bounds, null, Message.obtain()))
        }
        assertTrue(replies.isEmpty())
    }
}
