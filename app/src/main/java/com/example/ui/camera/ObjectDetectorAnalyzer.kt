package com.example.ui.camera

import com.example.data.model.DetectorStage

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.TrackedBoundingBox

/** CameraX adapter only; detector lifecycle and runtime details live behind [ObjectDetectorEngine]. */
class ObjectDetectorAnalyzer(
    private val engine: ObjectDetectorEngine,
    private val onObjectsTracked: (List<TrackedBoundingBox>, Int, ImageProxy) -> Unit,
    private val onDetectionError: (Exception) -> Unit = {},
    private val onDetectionTelemetry: (Exception, Int, ImageProxy) -> Unit = { _, _, _ -> },
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
            consecutiveFrameFailures++
            val reportedError = if (error is PermanentDetectorException) {
                detectorFailedPermanently = true
                error
            } else {
                if (consecutiveFrameFailures >= consecutiveFailureThreshold) {
                    detectorFailedPermanently = true
                    PermanentDetectorException(
                        "Object detector failed for $consecutiveFrameFailures consecutive frames",
                        error,
                        error.detectorStage
                    )
                } else {
                    error
                }
            }
            Log.w(TAG, "Offline object detection failed", reportedError)
            onDetectionTelemetry(reportedError, consecutiveFrameFailures, image)
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

/** Associates a failure with a pipeline stage without retaining any frame contents. */
open class DetectorStageException(
    val stage: DetectorStage,
    message: String,
    cause: Throwable
) : Exception(message, cause)

internal inline fun <T> runDetectorStage(stage: DetectorStage, operation: () -> T): T = try {
    operation()
} catch (error: DetectorStageException) {
    throw error
} catch (error: Exception) {
    throw DetectorStageException(stage, "Detector stage $stage failed", error)
} catch (error: LinkageError) {
    // LinkageError is the only Error deliberately converted: OOM and other VM errors propagate.
    throw PermanentDetectorException("Incompatible detector runtime or ABI", error, stage)
}

/** Prefer throwable types supplied by MediaPipe/JNI over brittle localized message matching. */
internal fun Throwable.hasPermanentRuntimeCause(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause is LinkageError || cause is IllegalStateException ||
            (cause.javaClass.name.startsWith("com.google.mediapipe.") && cause !is IllegalArgumentException)
    }

internal val Throwable.detectorStage: DetectorStage
    get() = generateSequence(this) { it.cause }
        .filterIsInstance<DetectorStageException>().firstOrNull()?.stage ?: DetectorStage.UNKNOWN

class PermanentDetectorException(
    message: String,
    cause: Throwable? = null,
    val stage: DetectorStage = cause?.detectorStage ?: DetectorStage.UNKNOWN
) : Exception(message, cause)
