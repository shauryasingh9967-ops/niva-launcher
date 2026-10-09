package com.galaxyrio.gracelauncher.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Stable across process restarts. Setup activities allocate IDs but only HOME listens. */
class HomeWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    var onProvidersUpdated: (() -> Unit)? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView =
        HomeWidgetHostView(context)

    override fun onProvidersChanged() { onProvidersUpdated?.invoke() }
    override fun onAppWidgetRemoved(appWidgetId: Int) { super.onAppWidgetRemoved(appWidgetId); onProvidersUpdated?.invoke() }

    /** Setup activities sharing this host must not reclaim each other's in-flight IDs. */
    fun trackPendingId(id: Int) { pendingIds += id }
    fun untrackPendingId(id: Int) { pendingIds -= id }
    fun isPendingId(id: Int): Boolean = id in pendingIds

    companion object {
        const val HOST_ID = 0x47524143
        private val pendingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    }
}

/** Observe the native widget's gestures without stealing its buttons or scrolling. */
class HomeWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onWidgetLongPress: (() -> Unit)? = null
    var interactionEnabled = true
    var hapticsEnabled = true
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var held = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        if (onWidgetLongPress == null || !isAttachedToWindow || !interactionEnabled) return@Runnable
        held = true
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        parent?.requestDisallowInterceptTouchEvent(true)
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onWidgetLongPress?.invoke()
    }

    init { isClickable = true }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) { removeCallbacks(longPress); held = false; return true }
        if (held) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) held = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downTime = event.downTime
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        held = false
        super.onDetachedFromWindow()
    }
}
