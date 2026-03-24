package com.vocab.flashcard

import android.content.Context
import android.content.Intent
import android.os.Build

object ReviewStarter {

    private const val TAG = "ReviewStarter"

    fun ensureRunning(context: Context, reason: String): Boolean {
        val appContext = context.applicationContext
        val repo = VocabRepository(appContext)
        if (repo.isEmpty()) {
            AppLog.info(TAG, "Skip auto-start for $reason because repository is empty")
            return false
        }
        if (VocabService.isRunning) {
            AppLog.info(TAG, "Service already running, skip duplicate start for $reason")
            return true
        }

        val intent = Intent(appContext, VocabService::class.java).apply {
            action = VocabService.ACTION_START
            putExtra(VocabService.EXTRA_REASON, reason)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        AppLog.info(TAG, "Requested review service start for $reason")
        return true
    }
}
