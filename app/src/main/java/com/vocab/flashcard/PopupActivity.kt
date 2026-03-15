package com.vocab.flashcard

import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * 全屏弹窗 Activity
 * 显示单词与词义，30 秒后自动关闭，禁用返回键
 */
class PopupActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁用返回键：拦截 Back 键，禁止用户关闭弹窗
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                //  intentionally empty - 不调用 finish()
            }
        })

        // 全屏、保持亮屏、覆盖状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        setContentView(R.layout.activity_popup)

        val word = intent.getStringExtra(EXTRA_WORD) ?: ""
        val meaning = intent.getStringExtra(EXTRA_MEANING) ?: ""

        findViewById<TextView>(R.id.tvWord).text = word
        findViewById<TextView>(R.id.tvMeaning).text = meaning

        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)

        // 30 秒倒计时，每秒更新
        countDownTimer = object : CountDownTimer(30_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1_000).toInt()
                tvCountdown.text = "$seconds 秒后关闭"
            }

            override fun onFinish() {
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WORD = "word"
        const val EXTRA_MEANING = "meaning"
    }
}
