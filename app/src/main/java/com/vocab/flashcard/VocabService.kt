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

    private val showNextPopup = object : Runnable {
        override fun run() {
            val pair = repo.getRandomWord()
            if (pair != null) {
                val intent = Intent(this@VocabService, PopupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(PopupActivity.EXTRA_WORD, pair.first)
                    putExtra(PopupActivity.EXTRA_MEANING, pair.second)
                }
                startActivity(intent)
            }
            scheduleNext()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = VocabRepository(this)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        scheduleNext()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(showNextPopup)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNext() {
        val minMs = TimeUnit.MINUTES.toMillis(5)
        val maxMs = TimeUnit.MINUTES.toMillis(7)
        val delay = minMs + (Math.random() * (maxMs - minMs)).toLong()
        handler.postDelayed(showNextPopup, delay)
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
        const val CHANNEL_ID = "vocab_service"
        private const val NOTIFICATION_ID = 1
        @Volatile
        var isRunning = false
            private set
    }
}
