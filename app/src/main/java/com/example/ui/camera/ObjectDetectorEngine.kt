package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.example.BuildConfig
import com.example.data.model.TrackedBoundingBox
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

fun interface ObjectDetectorEngine : Closeable {
    fun detect(image: ImageProxy): List<TrackedBoundingBox>
    override fun close() = Unit
}

internal data class DetectorOutput(val box: RectF, val score: Float)

/** Utilities kept runtime-independent so tensor layout and rotation remain directly testable. */
internal object EfficientDetOutputDecoder {
    fun decode(boxes: FloatArray, scores: FloatArray, threshold: Float): List<DetectorOutput> {
        require(boxes.size % 4 == 0)
        return scores.take(boxes.size / 4).mapIndexedNotNull { index, score ->
            if (score < threshold) return@mapIndexedNotNull null
            val offset = index * 4 // EfficientDet: ymin, xmin, ymax, xmax.
            DetectorOutput(RectF(boxes[offset + 1], boxes[offset], boxes[offset + 3], boxes[offset + 2]), score)
        }
    }

    fun rotate(rect: RectF, degrees: Int): RectF = when ((degrees % 360 + 360) % 360) {
        0 -> RectF(rect)
        90 -> RectF(1f - rect.bottom, rect.left, 1f - rect.top, rect.right)
        180 -> RectF(1f - rect.right, 1f - rect.bottom, 1f - rect.left, 1f - rect.top)
        270 -> RectF(rect.top, 1f - rect.right, rect.bottom, 1f - rect.left)
        else -> error("Only right-angle rotation is supported")
    }
}

class EfficientDetLiteEngine private constructor(
    private val detector: ObjectDetector,
    private val scoreThreshold: Float
) : ObjectDetectorEngine {
    private val tracker = GeometryTracker()
    private var rotatedBuffer: Bitmap? = null

    @Synchronized
    override fun detect(image: ImageProxy): List<TrackedBoundingBox> {
        val rotation = image.imageInfo.rotationDegrees
        val source = image.toBitmap()
        var detectorBitmap = source
        try {
            if (rotation != 0) {
                detectorBitmap = obtainRotatedBuffer(source, rotation)
                drawRotated(source, detectorBitmap, rotation)
            }
            // BitmapImageBuilder does not own detectorBitmap. Both the MediaPipe image and any
            // per-frame source bitmap remain valid until synchronous detect() has returned.
            val result = BitmapImageBuilder(detectorBitmap).build().use { mpImage ->
                try {
                    detector.detect(mpImage)
                } catch (error: Exception) {
                    throw PermanentDetectorException("Object detector runtime failed", error)
                } catch (error: LinkageError) {
                    // An ABI/JNI mismatch is permanent for this process. Do not catch Error as a
                    // whole: OutOfMemoryError must not be converted into a recoverable frame error.
                    throw PermanentDetectorException("Incompatible detector runtime or ABI", error)
                }
            }
            return tracker.update(
                result, detectorBitmap.width.toFloat(), detectorBitmap.height.toFloat(), scoreThreshold
            )
        } finally {
            // ImageProxy.toBitmap() transfers a new bitmap to this method. The rotated buffer is
            // engine-owned and reused; only the per-frame source is released here.
            source.recycle()
        }
    }

    private fun obtainRotatedBuffer(source: Bitmap, rotation: Int): Bitmap {
        val swapDimensions = rotation % 180 != 0
        val width = if (swapDimensions) source.height else source.width
        val height = if (swapDimensions) source.width else source.height
        return rotatedBuffer?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { replacement ->
                rotatedBuffer?.recycle()
                rotatedBuffer = replacement
            }
    }

    private fun drawRotated(source: Bitmap, destination: Bitmap, rotation: Int) {
        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            when ((rotation % 360 + 360) % 360) {
                90 -> postTranslate(source.height.toFloat(), 0f)
                180 -> postTranslate(source.width.toFloat(), source.height.toFloat())
                270 -> postTranslate(0f, source.width.toFloat())
            }
        }
        Canvas(destination).apply {
            drawColor(Color.TRANSPARENT)
            drawBitmap(source, matrix, null)
        }
    }

    @Synchronized
    override fun close() {
        rotatedBuffer?.recycle()
        rotatedBuffer = null
        detector.close()
    }

    companion object {
        val MODEL_ASSET: String = BuildConfig.DETECTOR_MODEL_ASSET
        fun create(context: Context, scoreThreshold: Float = 0.35f): EfficientDetLiteEngine {
            // Opening first gives a deterministic, controlled error for absent/corrupt packaging.
            context.assets.open(MODEL_ASSET).use { require(it.read() >= 0) }
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setScoreThreshold(scoreThreshold)
                .setMaxResults(5)
                .build()
            return EfficientDetLiteEngine(ObjectDetector.createFromOptions(context, options), scoreThreshold)
        }
    }
}

internal class GeometryTracker {
    private data class Track(val id: Int, var box: RectF, var frames: Int, var seen: Long)
    private val tracks = mutableListOf<Track>()
    private var nextId = 200

    fun update(result: ObjectDetectorResult, width: Float, height: Float, threshold: Float): List<TrackedBoundingBox> {
        val detections = result.detections().map { detection ->
            val b = detection.boundingBox()
            DetectorOutput(
                box = RectF(b.left / width, b.top / height, b.right / width, b.bottom / height),
                score = detection.categories().firstOrNull()?.score() ?: 0f
            )
        }
        return update(detections, threshold)
    }

    internal fun update(detections: List<DetectorOutput>, threshold: Float): List<TrackedBoundingBox> {
        val now = System.currentTimeMillis()
        tracks.removeAll { now - it.seen > 1500 }
        val used = mutableSetOf<Int>()
        return detections.mapNotNull { detection ->
            val score = detection.score
            if (score < threshold) return@mapNotNull null
            val measured = detection.box
            val track = tracks.filter { it.id !in used }.maxByOrNull { iou(it.box, measured) }
                ?.takeIf { iou(it.box, measured) >= .25f }
                ?: Track(nextId++, measured, 0, now).also(tracks::add)
            track.box = measured; track.frames++; track.seen = now; used += track.id
            TrackedBoundingBox(
                id = track.id,
                normalizedRect = RectF(measured),
                label = "Detected object",
                confidence = score,
                trackingFrames = track.frames
            )
        }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val area = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val union = a.width() * a.height() + b.width() * b.height() - area
        return if (union > 0) area / union else 0f
    }
}

/** Explicit fallback: keep analysis alive and report no boxes when initialization is unavailable. */
internal fun createDetectorEngine(context: Context, onError: (Exception) -> Unit): ObjectDetectorEngine =
    createDetectorEngine(onError) { EfficientDetLiteEngine.create(context) }

internal fun createDetectorEngine(
    onError: (Exception) -> Unit,
    factory: () -> ObjectDetectorEngine
): ObjectDetectorEngine =
    try { factory() }
    catch (error: Exception) {
        onError(RecoverableDetectorInitializationException("Detector initialization failed", error))
        DisabledObjectDetectorEngine
    }
    catch (error: LinkageError) {
        // Deliberately do not catch Error broadly: in particular, OOM must retain platform
        // semantics. Linkage failures are classified so Crashlytics can distinguish an ABI or
        // incompatible MediaPipe runtime from corrupt/missing model data.
        onError(IncompatibleDetectorRuntimeException("Incompatible detector runtime or ABI", error))
        DisabledObjectDetectorEngine
    }

class RecoverableDetectorInitializationException(message: String, cause: Throwable) : Exception(message, cause)
class IncompatibleDetectorRuntimeException(message: String, cause: LinkageError) : Exception(message, cause)

internal object DisabledObjectDetectorEngine : ObjectDetectorEngine {
    override fun detect(image: ImageProxy) = emptyList<TrackedBoundingBox>()
}
