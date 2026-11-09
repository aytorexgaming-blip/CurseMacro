package com.yourname.cursemacro

object MacroStore {
    val steps = mutableListOf<TapStep>()

    @Volatile
    var isRunning = false

    @Volatile
    var loopCount = 0
}
