package com.gazecontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Size

/**
 * Захватывает кадры с фронтальной камеры и прогоняет их через GazeEngine.
 * Используется и в CalibrationActivity, и в фоновом GazeTrackingService (LifecycleService).
 */
class CameraGazeSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onSample: (GazeSample) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var gazeEngine: GazeEngine? = null
    private val executor = ContextCompat.getMainExecutor(context)

    fun start() {
        gazeEngine = GazeEngine(context, onSample = onSample)

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { imageProxy ->
                handleFrame(imageProxy)
            }

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            } catch (e: Exception) {
                Log.e("CameraGazeSource", "bind failed", e)
            }
        }, executor)
    }

    private fun handleFrame(imageProxy: ImageProxy) {
        try {
            val bitmap: Bitmap = imageProxy.toBitmap()
            val rotated = if (imageProxy.imageInfo.rotationDegrees != 0) {
                rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
            } else bitmap
            gazeEngine?.processFrame(rotated, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("CameraGazeSource", "frame processing failed", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun stop() {
        cameraProvider?.unbindAll()
        gazeEngine?.close()
        gazeEngine = null
    }
}
