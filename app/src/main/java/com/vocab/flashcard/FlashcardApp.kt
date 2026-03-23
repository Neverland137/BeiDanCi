package com.vocab.flashcard

import android.app.Application
import kotlin.system.exitProcess

class FlashcardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        installCrashHandler()
        AppLog.info("FlashcardApp", "Application started")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.crash("Uncaught:${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable) ?: exitProcess(10)
        }
    }
}
