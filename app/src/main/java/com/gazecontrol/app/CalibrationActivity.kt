package com.gazecontrol.app

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.gazecontrol.app.databinding.ActivityCalibrationBinding

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraSource: CameraGazeSource

    private val collectedPoints = mutableListOf<CalibrationPoint>()
    private var currentTargetIndex = 0
    private var screenW = 0f
    private var screenH = 0f

    // Относительные позиции точек калибровки (сетка 3x3), с отступами от края
    private val relativePositions = listOf(
        0.08f to 0.10f, 0.5f to 0.10f, 0.92f to 0.10f,
        0.08f to 0.5f, 0.5f to 0.5f, 0.92f to 0.5f,
        0.08f to 0.92f, 0.5f to 0.92f, 0.92f to 0.92f
    )

    private val gazeBuffer = mutableListOf<Pair<Float, Float>>()
    private var collecting = false
    private var stableStartMs = 0L
    private val DWELL_MS = 1200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.calibrationRoot.post {
            screenW = binding.calibrationRoot.width.toFloat()
            screenH = binding.calibrationRoot.height.toFloat()
            showTarget(currentTargetIndex)
        }

        cameraSource = CameraGazeSource(this, this) { sample ->
            runOnUiThread { onGazeSample(sample) }
        }
        cameraSource.start()
    }

    private fun showTarget(index: Int) {
        if (index >= relativePositions.size) {
            finishCalibration()
            return
        }
        val (rx, ry) = relativePositions[index]
        val dot = binding.calibrationDot
        val targetX = rx * screenW - dot.width / 2f
        val targetY = ry * screenH - dot.height / 2f
        dot.animate().x(targetX).y(targetY).setDuration(400).start()
        collecting = false
        gazeBuffer.clear()

        dot.postDelayed({
            collecting = true
            stableStartMs = System.currentTimeMillis()
        }, 500)
    }

    private fun onGazeSample(sample: GazeSample) {
        if (!collecting || !sample.faceFound) return
        gazeBuffer.add(sample.gazeX to sample.gazeY)

        val elapsed = System.currentTimeMillis() - stableStartMs
        if (elapsed >= DWELL_MS) {
            val avgX = gazeBuffer.map { it.first }.average().toFloat()
            val avgY = gazeBuffer.map { it.second }.average().toFloat()
            val (rx, ry) = relativePositions[currentTargetIndex]

            collectedPoints.add(
                CalibrationPoint(
                    gazeX = avgX,
                    gazeY = avgY,
                    screenX = rx * screenW,
                    screenY = ry * screenH
                )
            )
            collecting = false
            currentTargetIndex++
            showTarget(currentTargetIndex)
        }
    }

    private fun finishCalibration() {
        cameraSource.stop()
        if (collectedPoints.size >= 6) {
            val mapping = CalibrationManager.computeMapping(collectedPoints)
            CalibrationManager.save(this, mapping)
        }
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource.stop()
    }
}
