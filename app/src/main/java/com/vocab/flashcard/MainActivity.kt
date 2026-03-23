package com.vocab.flashcard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vocab.flashcard.databinding.ActivityMainBinding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: VocabRepository

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadColpkg(it) }
    }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 用户返回后刷新状态 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = VocabRepository(this)

        requestPermissionsIfNeeded()
        binding.btnSelectColpkg.setOnClickListener { pickFile.launch("*/*") }
        binding.btnStart.setOnClickListener { startVocabService() }
        binding.btnStop.setOnClickListener { stopVocabService() }
        binding.btnViewLog.setOnClickListener { showLogs() }
        binding.btnCopyLog.setOnClickListener { copyLogs() }
        showPreviousCrashIfNeeded()
        updateUi()
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun loadColpkg(uri: Uri) {
        binding.btnSelectColpkg.isEnabled = false
        binding.tvStatus.text = getString(R.string.parsing)
        AppLog.info(TAG, "User selected file: $uri")
        Thread {
            var tempPath: String? = null
            try {
                val path = copyUriToTemp(uri) ?: run {
                    runOnUiThread { showError("无法读取文件") }
                    return@Thread
                }
                tempPath = path
                AppLog.info(TAG, "Temporary colpkg saved to: $path")
                val words = ColpkgParser.parseFlaggedWords(path, cacheDir)
                runOnUiThread {
                    if (words.isEmpty()) {
                        binding.tvStatus.text = getString(R.string.no_words)
                        Toast.makeText(this, R.string.no_words, Toast.LENGTH_LONG).show()
                        AppLog.info(TAG, "Import finished with zero flagged words")
                    } else {
                        repo.clearAndInsert(words)
                        binding.tvStatus.text = getString(R.string.parse_success, words.size)
                        Toast.makeText(this, getString(R.string.parse_success, words.size), Toast.LENGTH_SHORT).show()
                        AppLog.info(TAG, "Import finished successfully with ${words.size} words")
                    }
                    binding.btnSelectColpkg.isEnabled = true
                }
            } catch (e: Throwable) {
                AppLog.error(TAG, "Failed to import colpkg", e)
                runOnUiThread {
                    showError(e.message ?: "解析失败")
                    binding.btnSelectColpkg.isEnabled = true
                }
            } finally {
                tempPath?.let { File(it).delete() }
            }
        }.start()
    }

    private fun copyUriToTemp(uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { input ->
            val temp = File.createTempFile("colpkg", ".colpkg", cacheDir)
            temp.outputStream().use { output -> input.copyTo(output) }
            temp.absolutePath
        }
    }

    private fun showError(msg: String) {
        AppLog.error(TAG, msg)
        binding.tvStatus.text = getString(R.string.parse_error, msg)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun startVocabService() {
        if (repo.isEmpty()) {
            Toast.makeText(this, R.string.no_words, Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, VocabService::class.java))
        updateUi()
    }

    private fun stopVocabService() {
        stopService(Intent(this, VocabService::class.java))
        updateUi()
    }

    private fun updateUi() {
        val running = VocabService.isRunning
        binding.btnStart.isEnabled = !running && !repo.isEmpty()
        binding.btnStop.isEnabled = running
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun showPreviousCrashIfNeeded() {
        val crashLog = AppLog.readCrashLog()
        if (crashLog.isNotBlank()) {
            binding.tvStatus.text = getString(R.string.last_crash_hint)
        }
    }

    private fun showLogs() {
        val content = buildString {
            val crashLog = AppLog.readCrashLog()
            if (crashLog.isNotBlank()) {
                append("=== LAST CRASH ===\n")
                append(crashLog)
                if (!crashLog.endsWith('\n')) {
                    append('\n')
                }
                append('\n')
            }
            val appLog = AppLog.readAppLog()
            if (appLog.isNotBlank()) {
                append("=== APP LOG ===\n")
                append(appLog)
            }
        }.ifBlank { getString(R.string.no_logs) }

        AlertDialog.Builder(this)
            .setTitle(R.string.view_logs)
            .setMessage(content.takeLast(12000))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.clear_crash_log) { _, _ ->
                AppLog.clearCrashLog()
                Toast.makeText(this, R.string.crash_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun copyLogs() {
        val text = buildString {
            val crashLog = AppLog.readCrashLog()
            val appLog = AppLog.readAppLog()
            if (crashLog.isNotBlank()) {
                append("=== LAST CRASH ===\n")
                append(crashLog)
                append("\n\n")
            }
            append("=== APP LOG ===\n")
            append(appLog)
        }.ifBlank { getString(R.string.no_logs) }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("flashcard-log", text))
        Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
    }
}
