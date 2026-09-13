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
    @Volatile private var detectorFailedPermanently = false

    override fun analyze(image: ImageProxy) {
        val started = clockMillis()
        try {
            if (detectorFailedPermanently) return
            if (lastInferenceStartedAt != Long.MIN_VALUE &&
                started - lastInferenceStartedAt < minimumInferenceIntervalMs
            ) return
            lastInferenceStartedAt = started
            onObjectsTracked(engine.detect(image), (clockMillis() - started).toInt(), image)
        } catch (error: Exception) {
            // A bad frame must not kill CameraX's executor. An engine failure, however, cannot
            // recover by processing more frames; leave it paused until CameraPreviewView replaces
            // this analyzer after an explicit retry.
            if (error is PermanentDetectorException) detectorFailedPermanently = true
            Log.w(TAG, "Offline object detection failed", error)
            onDetectionError(error)
        } finally {
            image.close()
        }
    }

    fun close() = engine.close()

    private companion object { const val TAG = "ObjectDetectorAnalyzer" }
}

/** Signals that the detector engine, rather than one input frame, must be recreated. */
class PermanentDetectorException(message: String, cause: Throwable? = null) : Exception(message, cause)
