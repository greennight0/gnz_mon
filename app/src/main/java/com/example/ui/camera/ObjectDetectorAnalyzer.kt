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
    private val userWarningConsecutiveFailureThreshold: Int =
        DEFAULT_USER_WARNING_CONSECUTIVE_FAILURE_THRESHOLD,
    private val failureRateWindowMs: Long = DEFAULT_FAILURE_RATE_WINDOW_MS,
    private val minimumFailureRateSampleSize: Int = DEFAULT_MINIMUM_FAILURE_RATE_SAMPLE_SIZE,
    private val userWarningFailureRate: Double = DEFAULT_USER_WARNING_FAILURE_RATE,
    private val minimumInferenceIntervalMs: Long = 100L,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : ImageAnalysis.Analyzer {
    init {
        require(consecutiveFailureThreshold > 0)
        require(userWarningConsecutiveFailureThreshold in 1..consecutiveFailureThreshold)
        require(failureRateWindowMs > 0)
        require(minimumFailureRateSampleSize > 0)
        require(userWarningFailureRate in 0.0..1.0)
    }

    private var lastInferenceStartedAt = Long.MIN_VALUE
    @Volatile private var detectorFailedPermanently = false
    private val recentFrameResults = ArrayDeque<FrameResult>()
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
            recordFrameResult(started, failed = false)
            onObjectsTracked(boxes, (clockMillis() - started).toInt(), image)
        } catch (error: Exception) {
            // Bad image data applies only to this frame. Keep the analyzer alive and retain the
            // count for retry policy. An explicitly classified runtime/model failure or too many
            // consecutive bad frames pauses inference until CameraPreviewView replaces this analyzer.
            consecutiveFrameFailures++
            recordFrameResult(started, failed = true)
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
            // Telemetry is frame-scoped and receives every failure. The UI callback is deliberately
            // quieter so isolated bad frames do not flash a warning to the user.
            onDetectionTelemetry(reportedError, consecutiveFrameFailures, image)
            if (reportedError is PermanentDetectorException || shouldWarnUser()) {
                onDetectionError(reportedError)
            }
        } finally {
            image.close()
        }
    }

    fun close() = engine.close()

    private fun recordFrameResult(timestamp: Long, failed: Boolean) {
        recentFrameResults.addLast(FrameResult(timestamp, failed))
        val oldestAllowed = timestamp - failureRateWindowMs
        while (recentFrameResults.firstOrNull()?.timestamp?.let { it < oldestAllowed } == true) {
            recentFrameResults.removeFirst()
        }
    }

    private fun shouldWarnUser(): Boolean {
        if (consecutiveFrameFailures >= userWarningConsecutiveFailureThreshold) return true
        if (recentFrameResults.size < minimumFailureRateSampleSize) return false
        return recentFrameResults.count { it.failed }.toDouble() / recentFrameResults.size >=
            userWarningFailureRate
    }

    private data class FrameResult(val timestamp: Long, val failed: Boolean)

    private companion object {
        const val TAG = "ObjectDetectorAnalyzer"
        const val DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD = 3
        const val DEFAULT_USER_WARNING_CONSECUTIVE_FAILURE_THRESHOLD = 2
        const val DEFAULT_FAILURE_RATE_WINDOW_MS = 5_000L
        const val DEFAULT_MINIMUM_FAILURE_RATE_SAMPLE_SIZE = 5
        const val DEFAULT_USER_WARNING_FAILURE_RATE = 0.6
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
