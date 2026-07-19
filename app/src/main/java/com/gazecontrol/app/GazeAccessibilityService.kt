package com.gazecontrol.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView

class GazeAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var cursorView: ImageView? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    private var lastStableX = -1f
    private var lastStableY = -1f
    private var dwellStartMs = 0L
    private var clickFiredForThisFixation = false

    private var lastBlinkActionMs = 0L
    private val BLINK_COOLDOWN_MS = 1200L
    private val STABLE_RADIUS_PX = 45f

    private val listener: (GazePoint) -> Unit = { point -> onGazePoint(point) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createCursorOverlay()
        GazeBus.subscribe(listener)
    }

    private fun createCursorOverlay() {
        val view = ImageView(this)
        view.setImageResource(R.drawable.cursor_ring)
        val size = 56

        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val params = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = -200
        params.y = -200

        windowManager.addView(view, params)
        cursorView = view
        cursorParams = params
    }

    private fun onGazePoint(point: GazePoint) {
        val view = cursorView ?: return
        val params = cursorParams ?: return

        if (!point.faceFound) {
            return
        }

        params.x = (point.screenX - view.width / 2f).toInt()
        params.y = (point.screenY - view.height / 2f).toInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            // view может быть ещё не измерен на первом кадре — игнорируем
        }

        handleDwell(point.screenX, point.screenY)
        handleBlinkGestures(point)
    }

    private fun handleDwell(x: Float, y: Float) {
        if (lastStableX < 0) {
            lastStableX = x; lastStableY = y
            dwellStartMs = System.currentTimeMillis()
            clickFiredForThisFixation = false
            return
        }

        val dx = x - lastStableX
        val dy = y - lastStableY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        if (dist > STABLE_RADIUS_PX) {
            // взгляд переместился — сброс фиксации
            lastStableX = x; lastStableY = y
            dwellStartMs = System.currentTimeMillis()
            clickFiredForThisFixation = false
            return
        }

        val elapsed = System.currentTimeMillis() - dwellStartMs
        if (!clickFiredForThisFixation && elapsed >= GazeBus.dwellMillis) {
            performClickAt(x, y)
            clickFiredForThisFixation = true
        }
    }

    private fun handleBlinkGestures(point: GazePoint) {
        val now = System.currentTimeMillis()
        if (now - lastBlinkActionMs < BLINK_COOLDOWN_MS) return

        // Моргание только левым глазом = назад, только правым = домой.
        // (Обычное моргание обоими глазами игнорируется, чтобы не было ложных срабатываний.)
        if (point.blinkLeft && !point.blinkRight) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastBlinkActionMs = now
        } else if (point.blinkRight && !point.blinkLeft) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            lastBlinkActionMs = now
        }
    }

    private fun performClickAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)

        cursorView?.let { flashCursor(it) }
    }

    private fun flashCursor(view: ImageView) {
        view.animate().scaleX(1.6f).scaleY(1.6f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Не требуется для работы курсора — оставлено пустым намеренно.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        GazeBus.unsubscribe(listener)
        cursorView?.let { windowManager.removeView(it) }
        cursorView = null
    }
}
