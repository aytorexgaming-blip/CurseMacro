package com.yourname.cursemacro

import android.graphics.Bitmap

object MacroStore {
    val steps = mutableListOf<TapStep>()

    @Volatile
    var isRunning = false

    @Volatile
    var loopCount = 0

    @Volatile
    var referenceBitmap: Bitmap? = null

    @Volatile
    var isLootWatching = false
}
