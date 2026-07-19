package com.gazecontrol.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class GazeTrackingService : LifecycleService() {

    private lateinit var cameraSource: CameraGazeSource
    private var mapping: GazeMapping? = null

    companion object {
        const val CHANNEL_ID = "gaze_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.gazecontrol.app.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        mapping = CalibrationManager.load(this)

        cameraSource = CameraGazeSource(this, this) { sample ->
            handleSample(sample)
        }
        cameraSource.start()
        GazeBus.trackingActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun handleSample(sample: GazeSample) {
        val map = mapping
        if (map == null || !sample.faceFound) {
            GazeBus.emit(GazePoint(0f, 0f, sample.blinkLeft, sample.blinkRight, faceFound = false))
            return
        }

        val (screenX, screenY) = map.map(sample.gazeX, sample.gazeY)

        val metrics: DisplayMetrics = resources.displayMetrics
        val clampedX = screenX.coerceIn(0f, metrics.widthPixels.toFloat())
        val clampedY = screenY.coerceIn(0f, metrics.heightPixels.toFloat())

        GazeBus.emit(GazePoint(clampedX, clampedY, sample.blinkLeft, sample.blinkRight, faceFound = true))
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Gaze tracking", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GazeControl активен")
            .setContentText("Отслеживание взгляда работает в фоне")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        GazeBus.trackingActive = false
        if (::cameraSource.isInitialized) cameraSource.stop()
    }
}
