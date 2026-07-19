package com.gazecontrol.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gazecontrol.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val CAMERA_PERMISSION_REQUEST = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnCalibrate.setOnClickListener {
            ensureCameraPermission {
                startActivity(Intent(this, CalibrationActivity::class.java))
            }
        }

        binding.btnStart.setOnClickListener {
            if (GazeBus.trackingActive) {
                stopService(Intent(this, GazeTrackingService::class.java))
            } else {
                startTracking()
            }
        }

        binding.dwellSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                GazeBus.dwellMillis = progress.coerceAtLeast(200).toLong()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessOn = isAccessibilityServiceEnabled()
        val calibrated = CalibrationManager.hasCalibration(this)
        val tracking = GazeBus.trackingActive

        binding.statusText.text = buildString {
            append(if (accessOn) "✓ Спец. возможности включены\n" else "✗ Спец. возможности выключены\n")
            append(if (calibrated) "✓ Калибровка выполнена\n" else "✗ Нужна калибровка\n")
            append(if (tracking) "● Отслеживание запущено" else "○ Отслеживание остановлено")
        }
        binding.btnStart.text = if (tracking) getString(R.string.btn_stop) else getString(R.string.btn_start)
    }

    private fun startTracking() {
        ensureCameraPermission {
            if (!isAccessibilityServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@ensureCameraPermission
            }
            if (!CalibrationManager.hasCalibration(this)) {
                startActivity(Intent(this, CalibrationActivity::class.java))
                return@ensureCameraPermission
            }
            ContextCompat.startForegroundService(this, Intent(this, GazeTrackingService::class.java))
            updateStatus()
        }
    }

    private fun ensureCameraPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            updateStatus()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, GazeAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
