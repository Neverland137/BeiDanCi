package com.vocab.flashcard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class VocabService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: VocabRepository
    private lateinit var popupController: OverlayPopupController
    private var hasScheduledNextPopup = false

    private val showNextPopup = object : Runnable {
        override fun run() {
            hasScheduledNextPopup = false
            val pair = repo.getRandomWord()
            if (pair != null) {
                AppLog.info("VocabService", "Showing popup for word: ${pair.first}")
                popupController.show(pair.first, pair.second)
            }
            scheduleNext()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = VocabRepository(this)
        popupController = OverlayPopupController(this)
        isRunning = true
        AppLog.info("VocabService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (repo.isEmpty()) {
            AppLog.info("VocabService", "Stop self because repository is empty")
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "unknown" }
        AppLog.info("VocabService", "Service started, reason=$reason")
        scheduleNext()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(showNextPopup)
        hasScheduledNextPopup = false
        popupController.dismiss()
        isRunning = false
        AppLog.info("VocabService", "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNext() {
        if (hasScheduledNextPopup) {
            return
        }
        val minMs = TimeUnit.MINUTES.toMillis(5)
        val maxMs = TimeUnit.MINUTES.toMillis(7)
        val delay = minMs + (Math.random() * (maxMs - minMs)).toLong()
        handler.removeCallbacks(showNextPopup)
        handler.postDelayed(showNextPopup, delay)
        hasScheduledNextPopup = true
        AppLog.info("VocabService", "Next popup scheduled in ${delay / 1000} seconds")
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.vocab.flashcard.action.START"
        const val CHANNEL_ID = "vocab_service"
        const val EXTRA_REASON = "reason"
        private const val NOTIFICATION_ID = 1
        @Volatile
        var isRunning = false
            private set
    }
}
