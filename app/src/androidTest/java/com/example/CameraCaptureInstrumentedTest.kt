package com.example

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.camera.CameraController
import com.example.ui.camera.CameraPreviewView
import com.example.ui.camera.TargetCaptureRequest
import com.example.ui.camera.TargetImageSource
import com.example.ui.camera.TargetSnapshot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real capture smoke test. Does not classify, save photographs, or modify the journal. */
@RunWith(AndroidJUnit4::class)
class CameraCaptureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun frontBackPortraitLandscapeEdgesAndRetryCaptureRealFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Grant camera permission in the app before running this test",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.CAMERA))
        var front by mutableStateOf(false)
        val controller = AtomicReference<CameraController>()
        val error = AtomicReference<Exception>()
        val readyCount = AtomicInteger()
        val analyzedFrames = AtomicInteger()
        val detectorError = AtomicReference<Exception>()
        val report = JSONArray()
        val originalOrientation = rule.activity.requestedOrientation
        rule.runOnUiThread {
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.setContent {
                CameraPreviewView(isFrontCamera = front, onControllerReady = controller::set,
                    onDetectorReady = { readyCount.incrementAndGet() },
                    onDetectorError = detectorError::set,
                    onObjectsTracked = { _, _ -> analyzedFrames.incrementAndGet() },
                    onImageCaptured = { it.recycle() }, onError = error::set)
            }
        }
        try {
            for (useFront in listOf(false, true)) {
                val previousReadyCount = readyCount.get()
                rule.runOnUiThread { front = useFront }
                rule.waitUntil(30_000) {
                    error.get() != null || detectorError.get() != null ||
                        readyCount.get() > if (useFront) previousReadyCount else 0
                }
                error.get()?.let { throw AssertionError("Camera binding failed", it) }
                detectorError.get()?.let { throw AssertionError("Detector initialization failed", it) }
                for (landscape in listOf(false, true)) {
                    val previousFrames = analyzedFrames.get()
                    rule.runOnUiThread {
                        rule.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    rule.waitUntil(30_000) {
                        var ready = false
                        rule.runOnUiThread {
                            val view = controller.get()?.previewView
                            ready = view != null && view.sensorToViewTransform != null &&
                                view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING &&
                                (if (landscape) view.width > view.height else view.height > view.width)
                        }
                        ready
                    }
                    error.get()?.let { throw AssertionError("Camera startup failed", it) }
                    rule.waitUntil(30_000) { analyzedFrames.get() >= previousFrames + 3 || detectorError.get() != null }
                    detectorError.get()?.let { throw AssertionError("Detector frame processing failed", it) }
                    val camera = controller.get()
                    // Missing capture use case must produce an error, then allow a real retry.
                    rule.runOnUiThread {
                        val capture = camera.imageCapture
                        camera.imageCapture = null
                        try {
                            camera.captureTarget(TargetCaptureRequest(1, RectF(5f, 5f, 50f, 80f))) {
                                it.bitmap.recycle()
                                fail("Missing ImageCapture must fail")
                            }
                        } finally { camera.imageCapture = capture }
                    }
                    assertNotNull("Failure must be reported", error.getAndSet(null))
                    val targets = listOf(RectF(.3f, .3f, .6f, .6f), RectF(0f, 0f, .2f, .25f),
                        RectF(.8f, 0f, 1f, .25f), RectF(0f, .75f, .2f, 1f), RectF(.8f, .75f, 1f, 1f))
                    for ((index, normalized) in targets.withIndex()) {
                        val captured = AtomicReference<TargetSnapshot>()
                        val start = android.os.SystemClock.elapsedRealtime()
                        rule.runOnUiThread {
                            val view = camera.previewView!!
                            val rect = RectF(normalized.left * view.width, normalized.top * view.height,
                                normalized.right * view.width, normalized.bottom * view.height)
                            val request = TargetCaptureRequest(100 + index, rect)
                            camera.captureTarget(request, captured::set)
                            rect.setEmpty() // Later tracker/layout mutations cannot change the request.
                        }
                        rule.waitUntil(30_000) { captured.get() != null || error.get() != null }
                        error.get()?.let { throw AssertionError("Camera capture failed", it) }
                        val snapshot = captured.get()
                        try {
                            assertEquals(100 + index, snapshot.trackId)
                            assertEquals(TargetImageSource.IMAGE_CAPTURE, snapshot.source)
                            assertTrue(snapshot.bitmap.width > 0)
                            assertEquals(snapshot.bitmap.width, snapshot.bitmap.height)
                            assertNotNull(snapshot.expandedBitmap)
                            assertTrue(kotlin.math.abs((snapshot.bitmap.width * 1.1f).toInt() - snapshot.expandedBitmap!!.width) <= 1)
                            assertEquals(snapshot.expandedBitmap.width, snapshot.expandedBitmap.height)
                            assertEquals("Captured bitmap must follow display orientation", landscape,
                                snapshot.sourceImageSize.width > snapshot.sourceImageSize.height)
                            detectorError.get()?.let { throw AssertionError("Detector failed during capture", it) }
                            report.put(JSONObject().put("front", useFront).put("landscape", landscape)
                                .put("target", index).put("sourceWidth", snapshot.sourceImageSize.width)
                                .put("sourceHeight", snapshot.sourceImageSize.height)
                                .put("squareSide", snapshot.bitmap.width)
                                .put("captureMs", android.os.SystemClock.elapsedRealtime() - start))
                        } finally { snapshot.bitmap.recycle(); snapshot.expandedBitmap?.recycle() }
                    }
                }
            }
        } finally {
            File(context.getExternalFilesDir(null), "camera-capture-smoke.json").writeText(report.toString(2))
            rule.runOnUiThread { rule.activity.requestedOrientation = originalOrientation }
        }
    }
}
