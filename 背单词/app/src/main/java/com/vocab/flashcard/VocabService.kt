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

/**
 * 前台服务：每 5–7 分钟随机弹出一个单词
 * 使用 Handler.postDelayed 实现随机间隔，弹窗后重新调度
 */
class VocabService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var scheduleRunnable: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "vocab_service"
        private const val NOTIFICATION_ID = 1001

        /** 5–7 分钟（秒） */
        private const val MIN_INTERVAL_SEC = 300
        private const val MAX_INTERVAL_SEC = 420

        @Volatile
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification())
        scheduleNextPopup()
        return START_STICKY
    }

    override fun onDestroy() {
        scheduleRunnable?.let { handler.removeCallbacks(it) }
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * 调度下一次弹窗：随机 5–7 分钟后执行
     */
    private fun scheduleNextPopup() {
        val delayMs = (MIN_INTERVAL_SEC..MAX_INTERVAL_SEC).random() * 1000L

        scheduleRunnable = Runnable {
            showWordPopup()
            scheduleNextPopup()
        }
        handler.postDelayed(scheduleRunnable!!, delayMs)
    }

    /**
     * 从本地词库随机取一个单词，启动 PopupActivity
     */
    private fun showWordPopup() {
        val repo = VocabRepository(this)
        val pair = repo.getRandomWord() ?: return

        val intent = Intent(this, PopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(PopupActivity.EXTRA_WORD, pair.first)
            putExtra(PopupActivity.EXTRA_MEANING, pair.second)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
