package com.example.ui.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.TrackedBoundingBox

/** CameraX adapter only; detector lifecycle and runtime details live behind [ObjectDetectorEngine]. */
class ObjectDetectorAnalyzer(
    private val engine: ObjectDetectorEngine,
    private val onObjectsTracked: (List<TrackedBoundingBox>, Int, ImageProxy) -> Unit,
    private val onDetectionError: (Exception) -> Unit = {}
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val started = System.currentTimeMillis()
        try {
            onObjectsTracked(engine.detect(image), (System.currentTimeMillis() - started).toInt(), image)
        } catch (error: Exception) {
            // A bad frame/runtime must not kill CameraX's analysis executor.
            Log.w(TAG, "Offline object detection failed", error)
            onDetectionError(error)
            onObjectsTracked(emptyList(), (System.currentTimeMillis() - started).toInt(), image)
        } finally {
            image.close()
        }
    }

    fun close() = engine.close()

    private companion object { const val TAG = "ObjectDetectorAnalyzer" }
}
