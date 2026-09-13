package com.example.ui.camera

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
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
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
import kotlinx.coroutines.withContext
import com.example.BuildConfig

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

class TargetSnapshot(
    val trackId: Int,
    val bitmap: Bitmap,
    sourceRect: RectF,
    val sourceImageSize: Size,
    val source: TargetImageSource
) {
    private val storedSourceRect = RectF(sourceRect)

    fun copySourceRect(): RectF = RectF(storedSourceRect)
}

internal fun mapAndClampTargetRect(rect: RectF, transform: Matrix, width: Int, height: Int): RectF {
    val mapped = RectF(rect)
    transform.mapRect(mapped)
    require(width > 0 && height > 0)
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
     * pixels use PreviewView coordinates. An ImageCapture buffer is mapped with CameraX's
     * OutputTransform pair, which includes crop, rotation, FILL_CENTER and front-camera mirror.
     */
    fun captureTarget(
        request: TargetCaptureRequest,
        onCaptured: (TargetSnapshot) -> Unit
    ) {
        val targetTrackId = request.trackId
        val targetRect = request.copyPreviewRect()
        val view = previewView ?: return onError(IllegalStateException("Camera preview is not ready"))
        val previewRect = mapAndClampTargetRect(targetRect, Matrix(), view.width, view.height)
        view.bitmap?.let { bitmap ->
            // PreviewView.bitmap normally matches the view, but do not assume that for resized
            // surfaces or test providers: this is still a PreviewView-to-preview-bitmap mapping.
            val viewToBitmap = Matrix().apply {
                setScale(bitmap.width.toFloat() / view.width, bitmap.height.toFloat() / view.height)
            }
            val rect = mapAndClampTargetRect(previewRect, viewToBitmap, bitmap.width, bitmap.height)
            onCaptured(snapshot(targetTrackId, bitmap, rect, TargetImageSource.PREVIEW_VIEW))
            return
        }

        // Copy the preview transform now: a later tracking/layout update cannot alter the request.
        val previewTransform = view.outputTransform
            ?: return onError(IllegalStateException("Preview transformation is not ready"))
        val capture = imageCapture
            ?: return onError(IllegalStateException("Camera capture is not ready"))
        if (cameraExecutor.isShutdown) cameraExecutor = newExecutor("camera-capture")
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val imageTransform = ImageProxyTransformFactory().apply {
                        isUsingCropRect = true
                        isUsingRotationDegrees = true
                    }.getOutputTransform(image)
                    val sourceRect = RectF(previewRect)
                    CoordinateTransform(previewTransform, imageTransform).mapRect(sourceRect)
                    val bitmap = image.toBitmap()
                    val rect = mapAndClampTargetRect(sourceRect, Matrix(), bitmap.width, bitmap.height)
                    onCaptured(snapshot(targetTrackId, bitmap, rect, TargetImageSource.IMAGE_CAPTURE))
                } catch (e: Exception) {
                    Log.e("CameraController", "Failed to capture selected target", e)
                    onError(e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) = this@CameraController.onError(exception)
        })
    }

    private fun snapshot(trackId: Int, source: Bitmap, rect: RectF, kind: TargetImageSource): TargetSnapshot {
        val left = rect.left.toInt().coerceIn(0, source.width - 1)
        val top = rect.top.toInt().coerceIn(0, source.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, source.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, source.height)
        val exactRect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        return TargetSnapshot(
            trackId, Bitmap.createBitmap(source, left, top, right - left, bottom - top),
            exactRect, Size(source.width, source.height), kind
        )
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
        // MediaPipe loads the model and native runtime here; never perform that work on
        // CameraX's main executor. LaunchedEffect resumes on main before binding/state callbacks.
        val engine = withContext(Dispatchers.Default) {
            createDetectorEngine(context) { error ->
                logDetectorFailure(error, temporary = false)
                ContextCompat.getMainExecutor(context).execute { onDetectorError(error) }
            }
        }
        val provider = try {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        } catch (cancelled: CancellationException) {
            engine.close()
            throw cancelled
        }
        try {
            cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                cameraController.imageCapture = imageCapture

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    // EfficientDet Lite does not benefit from full capture resolution. Bounding
                    // the analysis surface also bounds the transient YUV/RGB memory per frame.
                    .setTargetResolution(Size(640, 480))
                    .build()

                if (engine !== DisabledObjectDetectorEngine) {
                    val replacementRequested = AtomicBoolean(false)
                    lateinit var analyzer: ObjectDetectorAnalyzer
                    analyzer = ObjectDetectorAnalyzer(
                        engine = engine,
                        onObjectsTracked = { boxes, latency, imageProxy ->
                            val mapped = mapBoxesToPreview(boxes, imageProxy, previewView)
                            ContextCompat.getMainExecutor(context).execute {
                                onObjectsTracked(mapped, latency)
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
                    onDetectorReady()
                } else {
                    cameraController.replaceAnalyzer(imageAnalysis, null)
                }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()

                val camera = try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } catch (bindEx: Exception) {
                    Log.w("CameraPreviewView", "ImageAnalysis binding fallback", bindEx)
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }

                cameraController.camera = camera
                cameraController.toggleTorch(isTorchEnabled)
        } catch (e: Exception) {
            Log.e("CameraPreviewView", "Failed to bind camera use cases", e)
            onError(e)
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
 * and mirroring matrices; using the paired output transforms keeps drawing, tapping, and the
 * camera image in the same coordinate system.
 */
internal fun mapBoxesToPreview(
    boxes: List<TrackedBoundingBox>,
    imageProxy: ImageProxy,
    previewView: PreviewView
): List<TrackedBoundingBox> {
    val previewTransform = previewView.outputTransform ?: return boxes
    if (previewView.width <= 0 || previewView.height <= 0) return boxes

    val imageTransform = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }.getOutputTransform(imageProxy)
    val coordinateTransform = CoordinateTransform(imageTransform, previewTransform)
    val imageWidth = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
        imageProxy.width.toFloat()
    } else {
        imageProxy.height.toFloat()
    }
    val imageHeight = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
        imageProxy.height.toFloat()
    } else {
        imageProxy.width.toFloat()
    }

    return boxes.map { box ->
        val sourceRect = RectF(
            box.normalizedRect.left * imageWidth,
            box.normalizedRect.top * imageHeight,
            box.normalizedRect.right * imageWidth,
            box.normalizedRect.bottom * imageHeight
        )
        coordinateTransform.mapRect(sourceRect)
        box.copy(
            normalizedRect = normalizePreviewRect(sourceRect, previewView.width, previewView.height)
        )
    }
}

internal fun normalizePreviewRect(rect: RectF, width: Int, height: Int) = RectF(
    rect.left / width, rect.top / height, rect.right / width, rect.bottom / height
)
