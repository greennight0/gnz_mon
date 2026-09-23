package com.example.ui.camera

import com.example.data.model.DetectorStage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Size
import android.util.Log
import android.os.Build
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.TrackedBoundingBox
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineDispatcher
import com.example.BuildConfig

internal const val FIRST_ANALYSIS_FRAME_TIMEOUT_MS = 5_000L

/** A cancelled withContext must not discard a native graph created before dispatching back. */
internal suspend fun createOwnedDetectorEngine(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    factory: () -> ObjectDetectorEngine
): ObjectDetectorEngine {
    var created: ObjectDetectorEngine? = null
    try {
        return withContext(dispatcher) { factory().also { created = it } }
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable + dispatcher) { created?.close() }
        throw cancelled
    }
}

internal data class CameraUseCaseBindResult<T>(
    val camera: T,
    val analysisBound: Boolean,
    val detectorError: CameraAnalysisInitializationException? = null
)

/** Keeps preview fallback available without representing it as a working detector pipeline. */
internal fun <T> bindCameraUseCases(
    bindWithAnalysis: () -> T,
    bindPreviewFallback: () -> T
): CameraUseCaseBindResult<T> = try {
    CameraUseCaseBindResult(bindWithAnalysis(), analysisBound = true)
} catch (analysisError: Exception) {
    CameraUseCaseBindResult(
        camera = bindPreviewFallback(),
        analysisBound = false,
        detectorError = CameraAnalysisInitializationException(
            "CameraX could not bind the ImageAnalysis use case",
            analysisError
        )
    )
}

/** One-shot guard for devices which bind ImageAnalysis but never deliver an analyzer frame. */
internal class FirstAnalysisFrameWatchdog(
    private val timeoutMillis: Long = FIRST_ANALYSIS_FRAME_TIMEOUT_MS,
    private val onTimeout: (CameraAnalysisInitializationException) -> Unit
) {
    private val completed = AtomicBoolean(false)
    private var timeoutJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (completed.get()) return
        timeoutJob = scope.launch {
            delay(timeoutMillis)
            if (completed.compareAndSet(false, true)) {
                onTimeout(CameraAnalysisInitializationException(
                    "ImageAnalysis did not deliver its first frame within ${timeoutMillis}ms"
                ))
            }
        }
    }

    fun onFirstFrame() {
        if (completed.compareAndSet(false, true)) timeoutJob?.cancel()
    }
}

enum class TargetImageSource { PREVIEW_VIEW, IMAGE_CAPTURE }

/** Non-sensitive diagnostics: dimensions/layout only, never planes, pixels, or image bytes. */
internal data class DetectorTelemetry(
    val stage: DetectorStage,
    val consecutiveFailures: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameFormat: Int,
    val frameRotationDegrees: Int
)

internal fun detectorTelemetry(error: Throwable, failures: Int, image: ImageProxy) = DetectorTelemetry(
    error.detectorStage, failures, image.width, image.height, image.format,
    image.imageInfo.rotationDegrees
)

internal fun logDetectorFailure(error: Throwable, temporary: Boolean, telemetry: DetectorTelemetry? = null) {
    val root = generateSequence(error) { it.cause }.last()
    val message = buildString {
        append("detector_failure temporary=").append(temporary)
        append(" exception=").append(error.javaClass.name)
        append(" root_cause=").append(root.javaClass.name)
        telemetry?.let {
            append(" stage=").append(it.stage)
            append(" consecutive_failures=").append(it.consecutiveFailures)
            append(" frame_width=").append(it.frameWidth)
            append(" frame_height=").append(it.frameHeight)
            append(" frame_format=").append(it.frameFormat)
            append(" frame_rotation=").append(it.frameRotationDegrees)
        }
        append(" model_asset=").append(BuildConfig.DETECTOR_MODEL_ASSET)
        append(" manufacturer=").append(Build.MANUFACTURER)
        append(" model=").append(Build.MODEL)
        append(" sdk=").append(Build.VERSION.SDK_INT)
        append(" abis=").append(Build.SUPPORTED_ABIS.joinToString(","))
    }
    if (temporary) Log.w("DetectorTelemetry", message, error) else Log.e("DetectorTelemetry", message, error)
}

/** The selection copied on the UI thread when Scan is pressed. */
class TargetCaptureRequest(val trackId: Int, previewRect: RectF) {
    init { require(!previewRect.isEmpty) }
    private val storedPreviewRect = RectF(previewRect)

    fun copyPreviewRect(): RectF = RectF(storedPreviewRect)
}

/** Freeze the selected sensor region before the asynchronous capture starts. */
internal class TargetCaptureGeometry(previewRect: RectF, sensorToView: Matrix?) {
    private val sensorRect: RectF

    init {
        val viewToSensor = Matrix()
        check(copyFiniteMatrix(sensorToView).invert(viewToSensor)) {
            "Preview transformation cannot be inverted"
        }
        sensorRect = RectF(previewRect)
        viewToSensor.mapRect(sensorRect)
        requireUsableRect(sensorRect)
    }

    fun bufferRect(sensorToBuffer: Matrix?): RectF = RectF(sensorRect).also {
        // Sensor-to-buffer includes the full image origin. Do not subtract image.cropRect.
        copyFiniteMatrix(sensorToBuffer).mapRect(it)
        requireUsableRect(it)
    }

    private fun copyFiniteMatrix(source: Matrix?): Matrix {
        val copy = Matrix(checkNotNull(source) { "Camera transformation is not ready" })
        val values = FloatArray(9)
        copy.getValues(values)
        check(values.all { it.isFinite() }) { "Camera transformation is invalid" }
        return copy
    }
}

private fun requireUsableRect(rect: RectF) {
    require(listOf(rect.left, rect.top, rect.right, rect.bottom).all { it.isFinite() } && !rect.isEmpty) {
        "Selected region is invalid"
    }
}

class TargetSnapshot(
    val trackId: Int,
    val bitmap: Bitmap,
    sourceRect: RectF,
    val sourceImageSize: Size,
    val source: TargetImageSource,
    val expandedBitmap: Bitmap? = null
) {
    private val storedSourceRect = RectF(sourceRect)

    fun copySourceRect(): RectF = RectF(storedSourceRect)
}

internal fun mapAndClampTargetRect(rect: RectF, transform: Matrix, width: Int, height: Int): RectF {
    val mapped = RectF(rect)
    transform.mapRect(mapped)
    require(width > 0 && height > 0)
    requireUsableRect(mapped)
    require(RectF.intersects(mapped, RectF(0f, 0f, width.toFloat(), height.toFloat()))) {
        "Selected region is outside the captured image"
    }
    val left = mapped.left.coerceIn(0f, (width - 1).toFloat())
    val top = mapped.top.coerceIn(0f, (height - 1).toFloat())
    return RectF(left, top, mapped.right.coerceIn(left + 1f, width.toFloat()),
        mapped.bottom.coerceIn(top + 1f, height.toFloat()))
}

class CameraController(
    private val context: Context,
    private val onImageCaptured: (Bitmap) -> Unit,
    private val onError: (Exception) -> Unit
) {
    var imageCapture: ImageCapture? = null
    var camera: Camera? = null
    @Volatile var currentAnalyzer: ObjectDetectorAnalyzer? = null
        private set
    var previewView: PreviewView? = null
    private var cameraExecutor = newExecutor("camera-capture")
    val analysisExecutor = newExecutor("camera-analysis")
    private var imageAnalysis: ImageAnalysis? = null

    /** Called on main. clearAnalyzer prevents new frames; the executor barrier closes the old
     * detector only after every already-delivered frame has returned. */
    fun replaceAnalyzer(useCase: ImageAnalysis, analyzer: ObjectDetectorAnalyzer?) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        imageAnalysis?.clearAnalyzer()
        val previous = currentAnalyzer
        currentAnalyzer = analyzer
        imageAnalysis = useCase.takeIf { analyzer != null }
        if (previous != null) analysisExecutor.execute(previous::close)
        if (analyzer != null) useCase.setAnalyzer(analysisExecutor, analyzer)
    }

    /** Removes only the analyzer which raised the terminal error. A delayed callback from an old
     * generation must not tear down a replacement that has already been installed. */
    fun removeFailedAnalyzer(useCase: ImageAnalysis, failedAnalyzer: ObjectDetectorAnalyzer) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (currentAnalyzer === failedAnalyzer) replaceAnalyzer(useCase, null)
    }

    fun takePhoto() {
        // Tối ưu hóa phản hồi: Trích xuất trực tiếp bitmap hiện tại từ PreviewView
        // Giúp phản hồi siêu tốc, không bao giờ bị đơ/treo khung hình trên máy ảo hay thiết bị thật
        val instantBitmap = previewView?.bitmap
        if (instantBitmap != null) {
            onImageCaptured(instantBitmap)
            return
        }

        val capture = imageCapture ?: run {
            onError(IllegalStateException("Camera capture is not ready"))
            return
        }

        if (cameraExecutor.isShutdown) {
            cameraExecutor = newExecutor("camera-capture")
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val bitmap = image.toBitmap()
                        val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees.toFloat())
                        onImageCaptured(rotatedBitmap)
                    } catch (e: Exception) {
                        Log.e("CameraController", "Failed to convert image proxy to bitmap", e)
                        onError(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraController", "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    /**
     * Captures exactly [request], never consulting the live tracker after this call. Preview
     * pixels are mapped through the sensor into the full ImageCapture buffer. The click-time
     * sensor region includes preview FILL_CENTER and mirroring, independently of buffer crop.
     */
    fun captureTarget(
        request: TargetCaptureRequest,
        onCaptured: (TargetSnapshot) -> Unit
    ) {
        try {
            val targetTrackId = request.trackId
            val view = checkNotNull(previewView) { "Camera preview is not ready" }
            val previewRect = mapAndClampTargetRect(request.copyPreviewRect(), Matrix(), view.width, view.height)
            // CameraX matrices may change on later frames. Freeze the sensor region on main now.
            val geometry = TargetCaptureGeometry(previewRect, view.sensorToViewTransform)
            val capture = checkNotNull(imageCapture) { "Camera capture is not ready" }
            // MainActivity handles orientation changes without recreating this use case.
            capture.targetRotation = checkNotNull(view.display) { "Camera display is not ready" }.rotation
            if (cameraExecutor.isShutdown) cameraExecutor = newExecutor("camera-capture")
            capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val sourceRect = geometry.bufferRect(image.imageInfo.sensorToBufferTransformMatrix)
                        val bitmap = image.toBitmap()
                        try {
                            val rotation = uprightTransform(bitmap.width, bitmap.height, image.imageInfo.rotationDegrees)
                            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
                            try {
                                val rect = mapAndClampTargetRect(sourceRect, rotation, upright.width, upright.height)
                                val captured = snapshot(targetTrackId, upright, rect, TargetImageSource.IMAGE_CAPTURE)
                                ContextCompat.getMainExecutor(context).execute { onCaptured(captured) }
                            } finally {
                                if (upright !== bitmap) upright.recycle()
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraController", "Failed to capture selected target", e)
                        ContextCompat.getMainExecutor(context).execute { onError(e) }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute { this@CameraController.onError(exception) }
                }
            })
        } catch (e: Exception) {
            // Includes missing/non-invertible transforms and synchronous takePicture failures.
            // The owner releases its capture reservation through this same error callback.
            onError(e)
        }
    }

    private fun snapshot(trackId: Int, source: Bitmap, rect: RectF, kind: TargetImageSource): TargetSnapshot {
        val square = expandedSquareRect(rect, source.width, source.height)
        val bitmap = squareTargetBitmap(source, square)
        val expanded = RectF(square).apply { inset(-width() * .05f, -height() * .05f) }
        val contextBitmap = squareTargetBitmap(source, expanded)
        if (com.example.BuildConfig.DEBUG) Log.d("CommonRecognition", "capture track=$trackId source=$kind " +
            "size=${source.width}x${source.height} target=$rect whole=$square expanded=$expanded")
        return TargetSnapshot(trackId, bitmap, square, Size(source.width, source.height), kind, contextBitmap)
    }


    fun toggleTorch(enable: Boolean) {
        camera?.cameraControl?.enableTorch(enable)
    }

    fun release() {
        try {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            val analyzer = currentAnalyzer
            currentAnalyzer = null
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            if (!analysisExecutor.isShutdown) {
                analysisExecutor.shutdown()
                // All analyze() calls precede termination. MediaPipe and its reusable bitmap must
                // not be closed/recycled while native inference can still be reading them.
                if (!analysisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w("CameraController", "Timed out waiting for image analysis to stop")
                }
            }
            analyzer?.close()
        } catch (e: Exception) {
            Log.w("CameraController", "Error releasing camera executor", e)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        val threadIds = AtomicInteger()
        fun newExecutor(role: String) = Executors.newSingleThreadExecutor { task ->
            Thread(task, "$role-${threadIds.incrementAndGet()}")
        }
    }
}

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = false,
    isTorchEnabled: Boolean = false,
    onControllerReady: (CameraController) -> Unit,
    onObjectsTracked: (List<TrackedBoundingBox>, Int) -> Unit = { _, _ -> },
    onDetectorError: (Exception) -> Unit = {},
    onDetectorReady: () -> Unit = {},
    detectorRetryKey: Int = 0,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val cameraController = remember(context) {
        CameraController(
            context = context,
            onImageCaptured = onImageCaptured,
            onError = onError
        ).apply {
            this.previewView = previewView
        }
    }

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    LaunchedEffect(cameraController) {
        onControllerReady(cameraController)
    }

    LaunchedEffect(isTorchEnabled) {
        cameraController.toggleTorch(isTorchEnabled)
    }

    LaunchedEffect(isFrontCamera, previewView, lifecycleOwner, detectorRetryKey) {
        // CameraX requires the Android main looper even when a Compose test installs its own
        // continuation interceptor. Explicitly restore Main after background initialization.
        withContext(Dispatchers.Main.immediate) {
        // MediaPipe loads the model and native runtime here; never perform that work on
        // CameraX's main executor. LaunchedEffect resumes on main before binding/state callbacks.
        val engine = createOwnedDetectorEngine {
            createDetectorEngine(context.applicationContext) { error ->
                logDetectorFailure(error, temporary = false)
                ContextCompat.getMainExecutor(context).execute { onDetectorError(error) }
            }
        }
        val provider = try {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        } catch (failure: Exception) {
            engine.close()
            throw failure
        }
        var analyzerOwnsEngine = false
        try {
            cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                cameraController.imageCapture = imageCapture

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    // EfficientDet Lite does not benefit from full capture resolution. Bounding
                    // the analysis surface also bounds the transient YUV/RGB memory per frame.
                    .setTargetResolution(Size(640, 480))
                    .build()

                val firstFrameWatchdog = if (engine !== DisabledObjectDetectorEngine) {
                    FirstAnalysisFrameWatchdog { error ->
                        logDetectorFailure(error, temporary = false)
                        cameraController.replaceAnalyzer(imageAnalysis, null)
                        onDetectorError(error)
                    }
                } else null
                if (engine !== DisabledObjectDetectorEngine) {
                    val replacementRequested = AtomicBoolean(false)
                    lateinit var analyzer: ObjectDetectorAnalyzer
                    analyzer = ObjectDetectorAnalyzer(
                        engine = engine,
                        onObjectsTracked = { boxes, latency, imageProxy ->
                            firstFrameWatchdog?.onFirstFrame()
                            // ImageProxy closes when analyze returns. Copy geometry on its owner
                            // thread, and read PreviewView only on main (CameraX requires it).
                            val frame = AnalysisFrameGeometry(imageProxy)
                            ContextCompat.getMainExecutor(context).execute {
                                if (cameraController.currentAnalyzer === analyzer) {
                                    onObjectsTracked(mapBoxesToPreview(boxes, frame, previewView), latency)
                                }
                            }
                        },
                        onDetectionError = { error ->
                            ContextCompat.getMainExecutor(context).execute {
                                onDetectorError(error)
                                if (error is PermanentDetectorException &&
                                    replacementRequested.compareAndSet(false, true)
                                ) {
                                    // replaceAnalyzer clears delivery first and queues close behind
                                    // all frames already submitted to the analysis executor.
                                    cameraController.removeFailedAnalyzer(imageAnalysis, analyzer)
                                }
                            }
                        },
                        onDetectionTelemetry = { error, failures, image ->
                            logDetectorFailure(
                                error,
                                temporary = error !is PermanentDetectorException,
                                detectorTelemetry(error, failures, image)
                            )
                        }
                    )
                    cameraController.replaceAnalyzer(imageAnalysis, analyzer)
                    analyzerOwnsEngine = true
                } else {
                    cameraController.replaceAnalyzer(imageAnalysis, null)
                }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()

                val bindResult = bindCameraUseCases(
                    bindWithAnalysis = {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    },
                    bindPreviewFallback = {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }
                )

                if (bindResult.analysisBound && engine !== DisabledObjectDetectorEngine) {
                    onDetectorReady()
                    firstFrameWatchdog?.start(this)
                } else if (!bindResult.analysisBound) {
                    val detectorError = checkNotNull(bindResult.detectorError)
                    Log.w("CameraPreviewView", "ImageAnalysis binding fallback", detectorError)
                    cameraController.replaceAnalyzer(imageAnalysis, null)
                    onDetectorError(detectorError)
                }

                cameraController.camera = bindResult.camera
                cameraController.toggleTorch(isTorchEnabled)
        } catch (e: Exception) {
            if (!analyzerOwnsEngine) engine.close()
            if (e is CancellationException) throw e
            Log.e("CameraPreviewView", "Failed to bind camera use cases", e)
            onError(e)
        }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                cameraController.release()
            } catch (e: Exception) {
                Log.w("CameraPreviewView", "Error in CameraPreviewView dispose", e)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle gradient vignette overlay to enhance camera HUD contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )
    }
}

/**
 * Maps detector boxes from the rotated ImageAnalysis buffer into the cropped PreviewView space.
 *
 * PreviewView uses FILL_CENTER, so scaling normalized analysis coordinates directly to the
 * Compose overlay is incorrect on most phone aspect ratios. CameraX owns the crop, rotation,
 * and mirroring matrices; mapping through sensor coordinates keeps drawing, tapping, and the
 * camera image in the same coordinate system.
 */
internal class AnalysisFrameGeometry(image: ImageProxy) {
    private val rotation = image.imageInfo.rotationDegrees
    val width = if (rotation % 180 == 0) image.width else image.height
    val height = if (rotation % 180 == 0) image.height else image.width
    val uprightToSensor = Matrix().also { inverse ->
        val sensorToUpright = Matrix(image.imageInfo.sensorToBufferTransformMatrix)
        sensorToUpright.postConcat(uprightTransform(image.width, image.height, rotation))
        check(sensorToUpright.invert(inverse)) { "Analysis transformation cannot be inverted" }
    }
}

internal fun mapBoxesToPreview(
    boxes: List<TrackedBoundingBox>,
    frame: AnalysisFrameGeometry,
    previewView: PreviewView
): List<TrackedBoundingBox> {
    val sensorToView = previewView.sensorToViewTransform ?: return emptyList()
    if (previewView.width <= 0 || previewView.height <= 0) return boxes
    val transform = Matrix(frame.uprightToSensor).apply { postConcat(sensorToView) }

    return boxes.map { box ->
        val sourceRect = RectF(
            box.normalizedRect.left * frame.width,
            box.normalizedRect.top * frame.height,
            box.normalizedRect.right * frame.width,
            box.normalizedRect.bottom * frame.height
        )
        transform.mapRect(sourceRect)
        box.copy(
            normalizedRect = normalizePreviewRect(sourceRect, previewView.width, previewView.height)
        )
    }
}

internal fun normalizePreviewRect(rect: RectF, width: Int, height: Int) = RectF(
    rect.left / width, rect.top / height, rect.right / width, rect.bottom / height
)

/** Matrix from raw buffer pixels to an upright bitmap, including positive origin translation. */
internal fun uprightTransform(width: Int, height: Int, degrees: Int): Matrix = Matrix().apply {
    setRotate(degrees.toFloat())
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    mapRect(bounds)
    postTranslate(-bounds.left, -bounds.top)
}

/** Expand, never shrink the target. Shift into the image where possible; pad otherwise. */
internal fun expandedSquareRect(target: RectF, width: Int, height: Int): RectF {
    val left = kotlin.math.floor(target.left).coerceIn(0f, (width - 1).toFloat())
    val top = kotlin.math.floor(target.top).coerceIn(0f, (height - 1).toFloat())
    val right = kotlin.math.ceil(target.right).coerceIn(left + 1, width.toFloat())
    val bottom = kotlin.math.ceil(target.bottom).coerceIn(top + 1, height.toFloat())
    val side = maxOf(right - left, bottom - top)
    fun origin(center: Float, extent: Int): Float = if (side <= extent) {
        kotlin.math.floor(center - side / 2).coerceIn(0f, extent - side)
    } else kotlin.math.floor((extent - side) / 2)
    val x = origin((left + right) / 2, width)
    val y = origin((top + bottom) / 2, height)
    return RectF(x, y, x + side, y + side)
}

internal fun squareTargetBitmap(source: Bitmap, square: RectF): Bitmap {
    val bitmap = Bitmap.createBitmap(square.width().toInt(), square.height().toInt(), Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).apply {
        drawColor(android.graphics.Color.rgb(124, 116, 104))
        drawBitmap(source, -square.left, -square.top, null)
    }
    return bitmap
}
