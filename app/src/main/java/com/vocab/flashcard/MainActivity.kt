package com.vocab.flashcard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 主界面 Activity
 * 功能：选择 .colpkg 文件、解析词库、启动/停止背单词服务
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnSelectColpkg: Button
    private lateinit var btnStartStop: Button

    private var vocabRepository: VocabRepository? = null
    private var isServiceRunning = false

    // 文件选择器：选择 .colpkg 文件
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            parseColpkg(uri)
        }
    }

    // 通知权限（Android 13+ 必需，否则前台服务通知无法显示）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            doStartVocabService()
        } else {
            Toast.makeText(this, "需要通知权限才能运行背单词服务", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnSelectColpkg = findViewById(R.id.btnSelectColpkg)
        btnStartStop = findViewById(R.id.btnStartStop)

        btnSelectColpkg.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        btnStartStop.setOnClickListener {
            if (isServiceRunning) {
                stopVocabService()
            } else {
                startVocabService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceRunning = VocabService.isRunning
        updateStartStopButton()
        // 若之前已解析过词库且词库非空，恢复「开始」按钮可用状态
        if (!btnStartStop.isEnabled && !VocabRepository(this).isEmpty()) {
            btnStartStop.isEnabled = true
        }
    }

    private fun parseColpkg(uri: android.net.Uri) {
        tvStatus.text = getString(R.string.parsing)
        btnSelectColpkg.isEnabled = false

        // 在后台线程解析，避免阻塞 UI
        Thread {
            try {
                val repo = VocabRepository(this)
                val count = ColpkgParser.parse(this, uri, repo)
                runOnUiThread {
                    vocabRepository = repo
                    tvStatus.text = getString(R.string.parse_success, count)
                    btnSelectColpkg.isEnabled = true
                    btnStartStop.isEnabled = count > 0
                    if (count == 0) {
                        Toast.makeText(this, R.string.no_words, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = getString(R.string.parse_error, e.message)
                    btnSelectColpkg.isEnabled = true
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startVocabService() {
        // Android 13+ 需要通知权限才能显示前台服务通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ->
                    doStartVocabService()
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            doStartVocabService()
        }
    }

    private fun doStartVocabService() {
        val intent = Intent(this, VocabService::class.java)
        startForegroundService(intent)
        isServiceRunning = true
        updateStartStopButton()
    }

    private fun stopVocabService() {
        val intent = Intent(this, VocabService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateStartStopButton()
    }

    private fun updateStartStopButton() {
        btnStartStop.text = if (isServiceRunning) getString(R.string.stop_vocab) else getString(R.string.start_vocab)
    }
}
