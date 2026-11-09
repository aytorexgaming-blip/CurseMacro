package com.yourname.cursemacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class MacroAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentStepIndex = 0

    private val stepRunnable = object : Runnable {
        override fun run() {
            if (!MacroStore.isRunning || MacroStore.steps.isEmpty()) return

            val step = MacroStore.steps[currentStepIndex]
            tapAt(step.x, step.y)

            currentStepIndex++
            if (currentStepIndex >= MacroStore.steps.size) {
                currentStepIndex = 0
                MacroStore.loopCount++
            }

            handler.postDelayed(this, step.delayMs)
        }
    }

    fun startLoop() {
        if (MacroStore.steps.isEmpty()) return
        currentStepIndex = 0
        MacroStore.isRunning = true
        handler.removeCallbacks(stepRunnable)
        handler.post(stepRunnable)
    }

    fun stopLoop() {
        MacroStore.isRunning = false
        handler.removeCallbacks(stepRunnable)
    }

    fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoop()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        var instance: MacroAccessibilityService? = null
    }
}
