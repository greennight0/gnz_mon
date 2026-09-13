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
    private val consecutiveFailureThreshold: Int = DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD,
    private val minimumInferenceIntervalMs: Long = 100L,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : ImageAnalysis.Analyzer {
    init {
        require(consecutiveFailureThreshold > 0)
    }

    private var lastInferenceStartedAt = Long.MIN_VALUE
    @Volatile private var detectorFailedPermanently = false
    internal var consecutiveFrameFailures: Int = 0
        private set

    override fun analyze(image: ImageProxy) {
        val started = clockMillis()
        try {
            if (detectorFailedPermanently) return
            if (lastInferenceStartedAt != Long.MIN_VALUE &&
                started - lastInferenceStartedAt < minimumInferenceIntervalMs
            ) return
            lastInferenceStartedAt = started
            val boxes = engine.detect(image)
            consecutiveFrameFailures = 0
            onObjectsTracked(boxes, (clockMillis() - started).toInt(), image)
        } catch (error: Exception) {
            // Bad image data applies only to this frame. Keep the analyzer alive and retain the
            // count for retry policy. An explicitly classified runtime/model failure or too many
            // consecutive bad frames pauses inference until CameraPreviewView replaces this analyzer.
            val reportedError = if (error is PermanentDetectorException) {
                detectorFailedPermanently = true
                error
            } else {
                consecutiveFrameFailures++
                if (consecutiveFrameFailures >= consecutiveFailureThreshold) {
                    detectorFailedPermanently = true
                    PermanentDetectorException(
                        "Object detector failed for $consecutiveFrameFailures consecutive frames",
                        error
                    )
                } else {
                    error
                }
            }
            Log.w(TAG, "Offline object detection failed", reportedError)
            onDetectionError(reportedError)
        } finally {
            image.close()
        }
    }

    fun close() = engine.close()

    private companion object {
        const val TAG = "ObjectDetectorAnalyzer"
        const val DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD = 3
    }
}

/** Signals that the detector engine, rather than one input frame, must be recreated. */
class PermanentDetectorException(message: String, cause: Throwable? = null) : Exception(message, cause)
