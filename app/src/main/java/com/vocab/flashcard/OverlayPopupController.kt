package com.vocab.flashcard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayPopupController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var countDownTimer: CountDownTimer? = null

    fun show(word: String, meaning: String): Boolean {
        if (!Settings.canDrawOverlays(appContext)) {
            AppLog.error("OverlayPopup", "Overlay permission not granted, cannot show popup")
            return false
        }

        dismiss()

        return try {
            val view = LayoutInflater.from(appContext).inflate(R.layout.activity_popup, null, false).apply {
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
                setOnTouchListener { _, _ -> true }
            }

            view.findViewById<TextView>(R.id.tvWord).text = word
            view.findViewById<TextView>(R.id.tvMeaning).text = meaning
            val countdownView = view.findViewById<TextView>(R.id.tvCountdown)

            windowManager.addView(view, createLayoutParams())
            overlayView = view
            AppLog.info("OverlayPopup", "Overlay popup shown for word=$word")

            countDownTimer = object : CountDownTimer(30_000, 1_000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = (millisUntilFinished / 1_000).toInt()
                    countdownView.text = "$seconds 秒后关闭"
                }

                override fun onFinish() {
                    dismiss()
                }
            }.start()

            true
        } catch (t: Throwable) {
            AppLog.error("OverlayPopup", "Failed to show overlay popup", t)
            dismiss()
            false
        }
    }

    fun dismiss() {
        countDownTimer?.cancel()
        countDownTimer = null

        val view = overlayView ?: return
        overlayView = null
        runCatching {
            windowManager.removeView(view)
            AppLog.info("OverlayPopup", "Overlay popup dismissed")
        }.onFailure {
            AppLog.error("OverlayPopup", "Failed to remove overlay popup", it)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}
