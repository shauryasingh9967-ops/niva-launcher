package com.galaxyrio.gracelauncher.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelUuid
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.roundToInt

/** An icon's lifetime follows its Compose node, not a previous click position. */
internal class LauncherIconTarget(
    val components: List<ComponentName>,
    val home: Boolean,
    val folder: Boolean,
    val view: View,
    val boundsInWindow: () -> Rect?,
    val capture: suspend () -> Bitmap?,
) {
    var hidden by mutableStateOf(false)

    fun screenBounds(): RectF? {
        if (!view.isAttachedToWindow) return null
        val bounds = boundsInWindow()?.takeUnless { it.isEmpty } ?: return null
        val window = IntArray(2).also(view::getLocationInWindow)
        val screen = IntArray(2).also(view::getLocationOnScreen)
        return RectF(bounds).apply { offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()) }
    }
}

internal class LauncherAppTransitions(private val activity: Activity, private val scope: CoroutineScope) {
    private val targets = linkedSetOf<LauncherIconTarget>()
    private var launchJob: Job? = null
    private val returning = if (Build.VERSION.SDK_INT >= 30) HomeReturnAnimation(activity, scope, targets) else null

    fun register(target: LauncherIconTarget) { targets.add(target) }
    fun unregister(target: LauncherIconTarget) { targets.remove(target); target.hidden = false }

    fun launch(view: View, bounds: Rect, start: (AppLaunchTransition?) -> Unit) {
        if (launchJob?.isActive == true) return
        if (Build.VERSION.SDK_INT >= 30) returning?.close()
        // Pick the clicked instance, not another copy of this app in a retained
        // drawer or a folder. Capture before a popup can dispose its icon layer.
        val target = targets.lastOrNull { it.view == view && it.boundsInWindow() == bounds }
        launchJob = scope.launch {
            val bitmap = try {
                withTimeoutOrNull(80) { target?.capture?.invoke() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            val transition = AppLaunchTransition.fromIcon(view, bounds, bitmap)
            if (Build.VERSION.SDK_INT >= 30) returning?.prepare(target, bitmap)
            Log.d(TAG, "Launch bounds=${transition?.sourceBounds} thumbnail=${bitmap != null}")
            start(transition)
        }
    }

    fun onHomeIntent(intent: Intent) {
        launchJob?.cancel()
        if (Build.VERSION.SDK_INT >= 30) returning?.onHomeIntent(intent)
    }

    fun close() {
        launchJob?.cancel()
        if (Build.VERSION.SDK_INT >= 30) returning?.close()
    }

    companion object { const val TAG = "GraceTransitions" }
}

/** Prefer a real HOME icon, then its HOME folder. Never use a dismissed popup. */
internal fun selectHomeTarget(targets: Collection<LauncherIconTarget>, component: ComponentName): LauncherIconTarget? =
    targets.asSequence().filter { it.home && it.screenBounds()?.isUsable() == true }
        .mapNotNull { target ->
            val exact = component in target.components
            val samePackage = target.components.any { it.packageName == component.packageName }
            if (!samePackage) null else target to (if (target.folder) 0 else 4) + (if (exact) 2 else 1)
        }.maxByOrNull { it.second }?.first

@RequiresApi(30)
private class HomeReturnAnimation(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val targets: Set<LauncherIconTarget>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val finishMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (token != null && message.obj == token) {
            Log.d(LauncherAppTransitions.TAG, "Quickstep finished")
            close()
        }
        true
    })
    private var token: ParcelUuid? = null
    private var contract: GestureNavContract? = null
    private var target: LauncherIconTarget? = null
    private var surfaceView: SurfaceView? = null
    private var bitmap: Bitmap? = null
    private var captureJob: Job? = null
    private var preDraw: ViewTreeObserver.OnPreDrawListener? = null
    private var lastSentBounds: RectF? = null
    private var preparedTarget: LauncherIconTarget? = null
    private var preparedBitmap: Bitmap? = null
    private val timeout = Runnable { close() }

    fun prepare(icon: LauncherIconTarget?, image: Bitmap?) {
        preparedTarget = icon?.takeIf { it.home }
        preparedBitmap = image.takeIf { preparedTarget != null }
    }

    fun onHomeIntent(intent: Intent) {
        close()
        val incoming = GestureNavContract.fromIntent(intent) ?: return
        // Only personal-profile HOME targets participate in this return animation.
        // A private/work app must not animate into a personal icon of the same package.
        if (incoming.user != Process.myUserHandle()) return
        contract = incoming
        token = ParcelUuid(UUID.randomUUID())
        Log.d(LauncherAppTransitions.TAG, "Quickstep contract: ${incoming.component}")
        // The existing HOME geometry is already usable while its window is
        // stopped. Reply immediately; Quickstep may already be running its spring.
        // Waiting for the first resumed draw lets the spring head to the default
        // bottom-center target for several frames before it can be redirected.
        start(incoming)
        if (contract !== incoming) return
        // HOME resets the drawer/overlays. Resolve after Compose has placed HOME
        // again, including any layout changes while the other app was running.
        preDraw = ViewTreeObserver.OnPreDrawListener {
            if (contract === incoming) {
                val placed = selectHomeTarget(targets, incoming.component)
                if (placed != null && placed !== target) start(incoming)
                else if (placed?.screenBounds() != lastSentBounds) sendPosition()
            }
            true
        }.also(activity.window.decorView.viewTreeObserver::addOnPreDrawListener)
        activity.window.decorView.invalidate()
        val scale = Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        handler.postDelayed(timeout, (2000L * scale.coerceIn(1f, 10f)).toLong())
    }

    private fun start(incoming: GestureNavContract) {
        if (contract !== incoming) return
        val icon = selectHomeTarget(targets, incoming.component) ?: return
        captureJob?.cancel()
        target?.hidden = false
        surfaceView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        surfaceView = null
        bitmap = null
        target = icon
        sendPosition() // Do not make the geometry wait for an image/surface.
        if (contract !== incoming) return
        val prepared = preparedBitmap.takeIf { preparedTarget === icon }
        if (prepared != null) {
            bitmap = prepared
            showSurface(icon)
            return
        }
        captureJob = scope.launch {
            val image = try { icon.capture() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (contract !== incoming || target !== icon || image == null) return@launch
            bitmap = image
            showSurface(icon)
        }
    }

    private fun showSurface(icon: LauncherIconTarget) {
        val bounds = icon.screenBounds() ?: return
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        val origin = IntArray(2).also(parent::getLocationOnScreen)
        surfaceView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val session = token
        val surface = SurfaceView(activity).apply {
            setZOrderOnTop(true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(object : SurfaceHolder.Callback2 {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (token != session || contract == null || target !== icon) return
                    // Hide the Compose icon only once its replacement is ready.
                    if (drawIcon(holder)) {
                        icon.hidden = true
                        sendPosition()
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    if (token == session && target === icon) drawIcon(holder)
                }
                override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
                    if (token == session && target === icon) drawIcon(holder)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
        surfaceView = surface
        parent.addView(surface, FrameLayout.LayoutParams(bounds.width().roundToInt(), bounds.height().roundToInt()).apply {
            leftMargin = (bounds.left - origin[0]).roundToInt()
            topMargin = (bounds.top - origin[1]).roundToInt()
        })
    }

    private fun drawIcon(holder: SurfaceHolder): Boolean {
        val image = bitmap ?: return false
        if (!holder.surface.isValid) return false
        return try {
            val canvas = holder.lockHardwareCanvas() ?: return false
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(image, null, Rect(0, 0, canvas.width, canvas.height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            true
        } catch (error: RuntimeException) {
            Log.w(LauncherAppTransitions.TAG, "Icon surface unavailable", error)
            false
        }
    }

    private fun sendPosition() {
        val current = contract ?: return
        val bounds = target?.screenBounds()?.takeIf { it.isUsable() } ?: return
        val surface = surfaceView?.surfaceControl?.takeIf { it.isValid }
        val finish = Message.obtain().apply { replyTo = finishMessenger; obj = token }
        if (!current.sendEndPosition(bounds, surface, finish)) { close(); return }
        lastSentBounds = bounds
        Log.d(LauncherAppTransitions.TAG, "Quickstep target=$bounds surface=${surface != null}")
    }

    fun close() {
        handler.removeCallbacks(timeout)
        preDraw?.let { activity.window.decorView.viewTreeObserver.removeOnPreDrawListener(it) }
        preDraw = null
        lastSentBounds = null
        captureJob?.cancel()
        captureJob = null
        contract = null
        token = null
        target?.hidden = false
        target = null
        val oldSurface = surfaceView
        surfaceView = null
        // Restore the Compose icon for one frame before removing its surface.
        oldSurface?.postOnAnimation { (oldSurface.parent as? ViewGroup)?.removeView(oldSurface) }
        bitmap = null
    }
}
