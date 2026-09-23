package com.example.ui.camera

import com.example.data.model.DetectorStage

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
import java.nio.ByteBuffer
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
    private var closed = false

    @Synchronized
    override fun detect(image: ImageProxy): List<TrackedBoundingBox> {
        check(!closed) { "Detector has already been closed" }
        val rotation = image.imageInfo.rotationDegrees
        val source = runDetectorStage(DetectorStage.IMAGE_TO_BITMAP) { image.toBitmap() }
        var detectorBitmap = source
        try {
            if (rotation != 0) {
                val swapDimensions = rotation % 180 != 0
                detectorBitmap = Bitmap.createBitmap(
                    if (swapDimensions) source.height else source.width,
                    if (swapDimensions) source.width else source.height,
                    Bitmap.Config.ARGB_8888
                )
                drawRotated(source, detectorBitmap, rotation)
            }
            // MPImage.close() recycles the supplied bitmap. Never retain it as a reusable buffer,
            // and read its dimensions while the image is still open.
            val mpImage = runDetectorStage(DetectorStage.MP_IMAGE_CREATION) {
                BitmapImageBuilder(detectorBitmap).build()
            }
            return mpImage.use {
                val result = try {
                    runDetectorStage(DetectorStage.DETECTOR_DETECT) { detector.detect(mpImage) }
                } catch (error: DetectorStageException) {
                    if (error.hasPermanentRuntimeCause()) {
                        throw PermanentDetectorException(
                            "Object detector runtime is unavailable",
                            error,
                            error.stage
                        )
                    }
                    throw error
                }
                tracker.update(
                    result, detectorBitmap.width.toFloat(), detectorBitmap.height.toFloat(), scoreThreshold
                )
            }
        } finally {
            // Also cover conversion/build failures before ownership transfers to MPImage.
            if (!detectorBitmap.isRecycled) detectorBitmap.recycle()
            if (!source.isRecycled) source.recycle()
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
        if (closed) return
        closed = true
        detector.close()
    }

    companion object {
        /** Selected from the checked-in calibration set; see the model manifest for the sweep. */
        const val CALIBRATED_SCORE_THRESHOLD = 0.22f
        val MODEL_ASSET: String = BuildConfig.DETECTOR_MODEL_ASSET
        fun create(context: Context, scoreThreshold: Float = CALIBRATED_SCORE_THRESHOLD): EfficientDetLiteEngine {
            // Give each graph its own model content rather than an asset-backed native mapping.
            // TaskOptions copies this buffer into the graph's model resources.
            val modelBytes = context.applicationContext.assets.open(MODEL_ASSET).use { it.readBytes() }
            require(modelBytes.isNotEmpty())
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
                put(modelBytes)
                rewind()
            }
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(modelBuffer).build())
                .setScoreThreshold(scoreThreshold)
                .setMaxResults(5)
                .build()
            // Native graphs may outlive an Activity while a camera rebind is cancelled.
            // Keep their asset manager attached to the application lifetime.
            return EfficientDetLiteEngine(ObjectDetector.createFromOptions(context.applicationContext, options), scoreThreshold)
        }
    }
}

internal class GeometryTracker(private val clock: () -> Long = android.os.SystemClock::elapsedRealtime) {
    private data class Track(val id: Int, var box: RectF, var measured: RectF,
        var frames: Int, var streak: Int, var confirmed: Boolean, var seen: Long,
        var score: Float)
    private val tracks = mutableListOf<Track>()
    private var nextId = 200

    fun update(result: ObjectDetectorResult, width: Float, height: Float, threshold: Float): List<TrackedBoundingBox> =
        update(result.detections().map { detection ->
            val b = detection.boundingBox()
            DetectorOutput(RectF(b.left / width, b.top / height, b.right / width, b.bottom / height),
                detection.categories().firstOrNull()?.score() ?: 0f)
        }, threshold)

    internal fun update(detections: List<DetectorOutput>, threshold: Float): List<TrackedBoundingBox> {
        val now = clock()
        tracks.removeAll { now - it.seen > 500 }
        val kept = mutableListOf<DetectorOutput>()
        detections.filter { d -> d.score.isFinite() && d.score in threshold..1f &&
            listOf(d.box.left, d.box.top, d.box.right, d.box.bottom).all { it.isFinite() } && !d.box.isEmpty
        }.mapNotNull { d ->
            val r = RectF(d.box.left.coerceIn(0f, 1f), d.box.top.coerceIn(0f, 1f),
                d.box.right.coerceIn(0f, 1f), d.box.bottom.coerceIn(0f, 1f))
            if (r.isEmpty) null else d.copy(box = r)
        }.sortedWith(compareByDescending<DetectorOutput> { it.score }.thenBy { it.box.left }.thenBy { it.box.top })
            .forEach { d -> if (kept.none { iou(it.box, d.box) >= .5f }) kept += d }
        val used = mutableSetOf<Int>()
        val assigned = mutableSetOf<Int>()
        // Globally strongest overlap first, never assign either endpoint twice.
        val pairs = tracks.flatMap { t -> kept.mapIndexed { i, d -> Triple(t, i, iou(t.measured, d.box)) } }
            .filter { it.third >= .25f }
            .sortedWith(compareByDescending<Triple<Track, Int, Float>> { it.third }
                .thenBy { it.first.id }.thenBy { it.second })
        for ((t, index, _) in pairs) {
            if (t.id in used || index in assigned) continue
            used += t.id; assigned += index
            val d = kept[index]
            val alpha = (1.0 - kotlin.math.exp(-(now - t.seen).coerceAtLeast(0) / 150.0)).toFloat()
            t.box = RectF(t.box.left + alpha * (d.box.left - t.box.left),
                t.box.top + alpha * (d.box.top - t.box.top),
                t.box.right + alpha * (d.box.right - t.box.right),
                t.box.bottom + alpha * (d.box.bottom - t.box.bottom))
            t.measured = RectF(d.box); t.frames++; t.seen = now; t.score = d.score
            t.streak = if (d.score >= .5f) t.streak + 1 else 0
            t.confirmed = t.confirmed || t.streak >= 3
        }
        tracks.filter { it.id !in used }.forEach { it.streak = 0 }
        kept.forEachIndexed { i, d -> if (i !in assigned && d.score >= .5f) {
            tracks += Track(nextId++, RectF(d.box), RectF(d.box), 1, 1, false, now, d.score)
            used += tracks.last().id
        } }
        return tracks.filter { it.confirmed }.sortedBy { it.id }.map { t ->
            TrackedBoundingBox(t.id, RectF(t.box), "Target", t.score,
                trackingFrames = t.frames, isObserved = t.id in used)
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
