package com.gazecontrol.app

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Результат одного кадра: нормализованный вектор взгляда (0..1 по обеим осям,
 * где 0.5/0.5 примерно соответствует "смотрит в центр экрана" после калибровки)
 * плюс флаги моргания.
 */
data class GazeSample(
    val gazeX: Float,
    val gazeY: Float,
    val blinkLeft: Boolean,
    val blinkRight: Boolean,
    val faceFound: Boolean
)

class GazeEngine(
    context: Context,
    modelAssetPath: String = "face_landmarker.task",
    private val onSample: (GazeSample) -> Unit
) {
    private var landmarker: FaceLandmarker? = null

    // Индексы точек в 478-точечной модели MediaPipe FaceMesh
    private object Idx {
        const val LEFT_EYE_OUTER = 33
        const val LEFT_EYE_INNER = 133
        const val LEFT_EYE_TOP = 159
        const val LEFT_EYE_BOTTOM = 145
        const val LEFT_IRIS = 468

        const val RIGHT_EYE_INNER = 362
        const val RIGHT_EYE_OUTER = 263
        const val RIGHT_EYE_TOP = 386
        const val RIGHT_EYE_BOTTOM = 374
        const val RIGHT_IRIS = 473
    }

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(this::handleResult)
            .setErrorListener { /* кадр пропущен — не критично */ }
            .build()

        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker?.detectAsync(mpImage, timestampMs)
    }

    private fun handleResult(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) {
            onSample(GazeSample(0.5f, 0.5f, false, false, faceFound = false))
            return
        }
        val lm = faces[0]

        fun ratioFor(
            outerIdx: Int, innerIdx: Int, topIdx: Int, bottomIdx: Int, irisIdx: Int
        ): Pair<Float, Float> {
            val outer = lm[outerIdx]
            val inner = lm[innerIdx]
            val top = lm[topIdx]
            val bottom = lm[bottomIdx]
            val iris = lm[irisIdx]

            val minX = minOf(outer.x(), inner.x())
            val maxX = maxOf(outer.x(), inner.x())
            val minY = minOf(top.y(), bottom.y())
            val maxY = maxOf(top.y(), bottom.y())

            val spanX = (maxX - minX).takeIf { it > 1e-5f } ?: 1e-5f
            val spanY = (maxY - minY).takeIf { it > 1e-5f } ?: 1e-5f

            val rx = ((iris.x() - minX) / spanX).coerceIn(0f, 1f)
            val ry = ((iris.y() - minY) / spanY).coerceIn(0f, 1f)
            return rx to ry
        }

        val (lx, ly) = ratioFor(Idx.LEFT_EYE_OUTER, Idx.LEFT_EYE_INNER, Idx.LEFT_EYE_TOP, Idx.LEFT_EYE_BOTTOM, Idx.LEFT_IRIS)
        val (rx, ry) = ratioFor(Idx.RIGHT_EYE_OUTER, Idx.RIGHT_EYE_INNER, Idx.RIGHT_EYE_TOP, Idx.RIGHT_EYE_BOTTOM, Idx.RIGHT_IRIS)

        val gazeX = (lx + rx) / 2f
        val gazeY = (ly + ry) / 2f

        var blinkLeft = false
        var blinkRight = false
        val blendshapesOpt = result.faceBlendshapes()
        if (blendshapesOpt.isPresent && blendshapesOpt.get().isNotEmpty()) {
            val categories = blendshapesOpt.get()[0]
            for (c in categories) {
                when (c.categoryName()) {
                    "eyeBlinkLeft" -> blinkLeft = c.score() > 0.55f
                    "eyeBlinkRight" -> blinkRight = c.score() > 0.55f
                }
            }
        }

        onSample(GazeSample(gazeX, gazeY, blinkLeft, blinkRight, faceFound = true))
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
