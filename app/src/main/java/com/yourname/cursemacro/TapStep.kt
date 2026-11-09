package com.yourname.cursemacro

data class TapStep(val x: Float, val y: Float, val delayMs: Long) {
    override fun toString(): String = "Tap ($x, $y) then wait ${delayMs}ms"
}
