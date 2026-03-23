package com.vocab.flashcard

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {

    private const val LOG_FILE_NAME = "app.log"
    private const val CRASH_FILE_NAME = "last_crash.log"
    private const val MAX_LOG_SIZE = 128 * 1024L

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun info(tag: String, message: String) {
        append(logFile(), "INFO", tag, message, null)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        append(logFile(), "ERROR", tag, message, throwable)
    }

    fun crash(tag: String, throwable: Throwable) {
        append(crashFile(), "FATAL", tag, throwable.message ?: "Uncaught exception", throwable)
    }

    fun readAppLog(): String = readFile(logFile())

    fun readCrashLog(): String = readFile(crashFile())

    fun clearCrashLog() {
        crashFile().delete()
    }

    private fun append(file: File, level: String, tag: String, message: String, throwable: Throwable?) {
        synchronized(lock) {
            ensureParent(file)
            trimIfNeeded(file)
            file.appendText(buildString {
                append(timestamp())
                append(" ")
                append(level)
                append("/")
                append(tag)
                append(": ")
                append(message)
                append('\n')
                if (throwable != null) {
                    append(throwable.stackTraceToString())
                    if (!endsWith('\n')) {
                        append('\n')
                    }
                }
            })
        }
    }

    private fun readFile(file: File): String {
        return if (file.exists()) file.readText() else ""
    }

    private fun logFile(): File = File(baseDir(), LOG_FILE_NAME)

    private fun crashFile(): File = File(baseDir(), CRASH_FILE_NAME)

    private fun baseDir(): File {
        val context = appContext
        return context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "flashcard-logs").apply {
            mkdirs()
        }
    }

    private fun ensureParent(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_SIZE) {
            return
        }
        val text = file.readText()
        val keep = text.takeLast((MAX_LOG_SIZE / 2).toInt())
        file.writeText(keep)
    }

    private fun timestamp(): String = timestampFormat.format(Date())
}
