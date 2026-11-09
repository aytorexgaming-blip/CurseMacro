package com.yourname.cursemacro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class LootWatchService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var lastProcessTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopWatching()
            }
        }, handler)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CurseMacroLootWatch",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastProcessTime < 400) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastProcessTime = now

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) checkForLoot(bitmap)
            } finally {
                image.close()
            }
        }, handler)

        MacroStore.isLootWatching = true
        return START_STICKY
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (bitmap.width != screenWidth) {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } else {
            bitmap
        }
    }

    private fun checkForLoot(screenBitmap: Bitmap) {
        val reference = MacroStore.referenceBitmap ?: return

        val scale = 0.25f
        val smallScreen = Bitmap.createScaledBitmap(
            screenBitmap,
            (screenBitmap.width * scale).toInt().coerceAtLeast(1),
            (screenBitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val smallRef = Bitmap.createScaledBitmap(
            reference,
            (reference.width * scale).toInt().coerceAtLeast(1),
            (reference.height * scale).toInt().coerceAtLeast(1),
            true
        )

        val match = TemplateMatcher.findBestMatch(smallScreen, smallRef)
        if (match != null && match.score < MATCH_THRESHOLD) {
            val realX = (match.x / scale) + (reference.width / 2f)
            val realY = (match.y / scale) + (reference.height / 2f)
            MacroAccessibilityService.instance?.tapAt(realX, realY)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "loot_watch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(channelId, "Loot Watch", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Curse Macro - watching for loot")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun stopWatching() {
        MacroStore.isLootWatching = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatching()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val NOTIFICATION_ID = 1001
        private const val MATCH_THRESHOLD = 30.0
    }
}

object TemplateMatcher {

    data class Match(val x: Int, val y: Int, val score: Double)

    fun findBestMatch(screen: Bitmap, template: Bitmap): Match? {
        val sw = screen.width
        val sh = screen.height
        val tw = template.width
        val th = template.height
        if (tw >= sw || th >= sh || tw == 0 || th == 0) return null

        val screenPixels = IntArray(sw * sh)
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh)
        val templatePixels = IntArray(tw * th)
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, th)

        var bestScore = Double.MAX_VALUE
        var bestX = 0
        var bestY = 0
        val step = 2

        var y = 0
        while (y <= sh - th) {
            var x = 0
            while (x <= sw - tw) {
                val score = compareAt(screenPixels, sw, x, y, templatePixels, tw, th)
                if (score < bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
                x += step
            }
            y += step
        }
        return Match(bestX, bestY, bestScore)
    }

    private fun compareAt(
        screenPixels: IntArray, screenWidth: Int, startX: Int, startY: Int,
        templatePixels: IntArray, tw: Int, th: Int
    ): Double {
        var total = 0.0
        var count = 0
        var ty = 0
        while (ty < th) {
            var tx = 0
            while (tx < tw) {
                val screenPixel = screenPixels[(startY + ty) * screenWidth + (startX + tx)]
                val templatePixel = templatePixels[ty * tw + tx]
                total += colorDiff(screenPixel, templatePixel)
                count++
                tx += 3
            }
            ty += 3
        }
        return if (count == 0) Double.MAX_VALUE else total / count
    }

    private fun colorDiff(a: Int, b: Int): Double {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 3.0
    }
}
