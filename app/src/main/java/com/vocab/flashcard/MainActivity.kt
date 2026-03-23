package com.vocab.flashcard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vocab.flashcard.databinding.ActivityMainBinding

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
        Thread {
            var tempPath: String? = null
            try {
                val path = copyUriToTemp(uri) ?: run {
                    runOnUiThread { showError("无法读取文件") }
                    return@Thread
                }
                tempPath = path
                Log.i(TAG, "Start parsing imported colpkg: $path")
                val words = ColpkgParser.parseFlaggedWords(path)
                runOnUiThread {
                    if (words.isEmpty()) {
                        binding.tvStatus.text = getString(R.string.no_words)
                        Toast.makeText(this, R.string.no_words, Toast.LENGTH_LONG).show()
                    } else {
                        repo.clearAndInsert(words)
                        binding.tvStatus.text = getString(R.string.parse_success, words.size)
                        Toast.makeText(this, getString(R.string.parse_success, words.size), Toast.LENGTH_SHORT).show()
                    }
                    binding.btnSelectColpkg.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import colpkg", e)
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
}
