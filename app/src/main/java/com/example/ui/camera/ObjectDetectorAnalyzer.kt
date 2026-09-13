package com.example.ui.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.TrackedBoundingBox

/** CameraX adapter only; detector lifecycle and runtime details live behind [ObjectDetectorEngine]. */
class ObjectDetectorAnalyzer(
    private val engine: ObjectDetectorEngine,
    private val onObjectsTracked: (List<TrackedBoundingBox>, Int, ImageProxy) -> Unit,
    private val onDetectionError: (Exception) -> Unit = {},
    private val minimumInferenceIntervalMs: Long = 100L,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : ImageAnalysis.Analyzer {
    private var lastInferenceStartedAt = Long.MIN_VALUE

    override fun analyze(image: ImageProxy) {
        val started = clockMillis()
        try {
            if (lastInferenceStartedAt != Long.MIN_VALUE &&
                started - lastInferenceStartedAt < minimumInferenceIntervalMs
            ) return
            lastInferenceStartedAt = started
            onObjectsTracked(engine.detect(image), (clockMillis() - started).toInt(), image)
        } catch (error: Exception) {
            // A bad frame/runtime must not kill CameraX's analysis executor.
            Log.w(TAG, "Offline object detection failed", error)
            onDetectionError(error)
            onObjectsTracked(emptyList(), (clockMillis() - started).toInt(), image)
        } finally {
            image.close()
        }
    }

    fun close() = engine.close()

    private companion object { const val TAG = "ObjectDetectorAnalyzer" }
}
