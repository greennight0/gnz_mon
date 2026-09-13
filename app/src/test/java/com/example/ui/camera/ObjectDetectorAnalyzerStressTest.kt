package com.example.ui.camera

import android.os.Debug
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DetectorState
import com.example.data.model.DetectorStage
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObjectDetectorAnalyzerStressTest {
    @Test fun `single failed frame emits telemetry but no ui warning and remains non blocking`() {
        val failure = IllegalArgumentException("bad frame")
        val callbacks = mutableListOf<String>()
        val telemetry = mutableListOf<Exception>()
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine { throw failure },
            onObjectsTracked = { boxes, _, _ ->
                callbacks += "objects:${boxes.size}"
                viewModel.onObjectsTracked(boxes, 1)
            },
            onDetectionError = { error -> callbacks += "error"; viewModel.onDetectorError(error) },
            onDetectionTelemetry = { error, _, _ -> telemetry += error },
            minimumInferenceIntervalMs = 0
        )

        analyzer.analyze(imageProxy(0) {})

        assertEquals(emptyList<String>(), callbacks)
        assertEquals(listOf(failure), telemetry)
        assertTrue(viewModel.detectorState.value !is DetectorState.FrameError)
    }

    @Test fun `view model preserves every detector stage in frame errors`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())

        listOf(
            DetectorStage.IMAGE_TO_BITMAP,
            DetectorStage.MP_IMAGE_CREATION,
            DetectorStage.DETECTOR_DETECT
        ).forEach { stage ->
            viewModel.onDetectorError(
                DetectorStageException(stage, "sensitive $stage details", IllegalArgumentException())
            )

            val state = viewModel.detectorState.value as DetectorState.FrameError
            assertEquals(stage, state.stage)
        }
    }

    @Test fun `permanent detector failure pauses subsequent inference`() {
        var attempts = 0
        var closes = 0
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine { attempts++; throw PermanentDetectorException("dead") },
            onObjectsTracked = { _, _, _ -> },
            minimumInferenceIntervalMs = 0
        )
        repeat(3) { analyzer.analyze(imageProxy(0) { closes++ }) }
        assertEquals(1, attempts)
        assertEquals(3, closes)
    }

    @Test fun `single frame failure recovers on next frame and closes both proxies`() {
        var attempts = 0
        var closes = 0
        val errors = mutableListOf<Exception>()
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine {
                attempts++
                if (attempts == 1) throw IllegalArgumentException("invalid frame pixels")
                emptyList()
            },
            onObjectsTracked = { _, _, _ -> },
            onDetectionError = errors::add,
            minimumInferenceIntervalMs = 0
        )

        repeat(2) { analyzer.analyze(imageProxy(0) { closes++ }) }

        assertEquals(2, attempts)
        assertEquals(2, closes)
        assertTrue(errors.isEmpty())
        assertEquals(0, analyzer.consecutiveFrameFailures)
    }

    @Test fun `consecutive failures emit one recreation request and close every proxy`() {
        var attempts = 0
        var closes = 0
        var recreationRequests = 0
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine {
                attempts++
                throw IllegalArgumentException("bad frame")
            },
            onObjectsTracked = { _, _, _ -> },
            onDetectionError = { if (it is PermanentDetectorException) recreationRequests++ },
            consecutiveFailureThreshold = 3,
            minimumInferenceIntervalMs = 0
        )

        repeat(8) { analyzer.analyze(imageProxy(0) { closes++ }) }

        assertEquals(3, attempts)
        assertEquals(1, recreationRequests)
        assertEquals(8, closes)
    }

    @Test fun `telemetry identifies stage count and frame shape without frame data`() {
        val telemetry = mutableListOf<DetectorTelemetry>()
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine {
                throw DetectorStageException(
                    DetectorStage.MP_IMAGE_CREATION, "failed", IllegalArgumentException()
                )
            },
            onObjectsTracked = { _, _, _ -> },
            onDetectionTelemetry = { error, failures, image ->
                telemetry += detectorTelemetry(error, failures, image)
            },
            minimumInferenceIntervalMs = 0
        )

        analyzer.analyze(imageProxy(rotation = 90, width = 640, height = 480, format = 35) {})

        assertEquals(
            DetectorTelemetry(DetectorStage.MP_IMAGE_CREATION, 1, 640, 480, 35, 90),
            telemetry.single()
        )
    }

    @Test fun `failure threshold becomes actionable view model error`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine { throw IllegalArgumentException("bad frame") },
            onObjectsTracked = { _, _, _ -> },
            onDetectionError = viewModel::onDetectorError,
            consecutiveFailureThreshold = 3,
            minimumInferenceIntervalMs = 0
        )

        analyzer.analyze(imageProxy(0) {})
        assertTrue(viewModel.detectorState.value !is DetectorState.FrameError)
        analyzer.analyze(imageProxy(0) {})
        assertTrue(viewModel.detectorState.value is DetectorState.FrameError)
        analyzer.analyze(imageProxy(0) {})
        val error = viewModel.detectorState.value as DetectorState.Error
        assertEquals(DetectorStage.UNKNOWN, error.stage)
    }

    @Test fun `significant failure rate warns without consecutive failures`() {
        var attempts = 0
        val uiErrors = mutableListOf<Exception>()
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine {
                attempts++
                if (attempts % 2 == 1) throw IllegalArgumentException("bad frame")
                emptyList()
            },
            onObjectsTracked = { _, _, _ -> },
            onDetectionError = uiErrors::add,
            minimumInferenceIntervalMs = 0
        )

        repeat(5) { analyzer.analyze(imageProxy(0) {}) }

        assertEquals(1, uiErrors.size)
        assertTrue(uiErrors.single() !is PermanentDetectorException)
        assertEquals(1, analyzer.consecutiveFrameFailures)
    }

    @Test fun `successful inference resets consecutive frame failure count`() {
        var attempts = 0
        val uiErrors = mutableListOf<Exception>()
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine {
                attempts++
                if (attempts != 2) throw IllegalArgumentException("bad frame $attempts")
                emptyList()
            },
            onObjectsTracked = { _, _, _ -> },
            onDetectionError = uiErrors::add,
            minimumInferenceIntervalMs = 0
        )

        analyzer.analyze(imageProxy(0) {})
        assertEquals(1, analyzer.consecutiveFrameFailures)
        assertTrue(uiErrors.isEmpty())
        analyzer.analyze(imageProxy(0) {})
        assertEquals(0, analyzer.consecutiveFrameFailures)
        analyzer.analyze(imageProxy(0) {})
        assertEquals(1, analyzer.consecutiveFrameFailures)
        assertTrue(uiErrors.isEmpty())
    }
    @Test fun `continuous rotated frames keep heap bounded and every proxy is closed`() {
        val rotations = intArrayOf(0, 90, 270)
        val seenRotations = mutableListOf<Int>()
        var closedImages = 0
        var now = 0L
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine { image ->
                seenRotations += image.imageInfo.rotationDegrees
                emptyList()
            },
            onObjectsTracked = { _, _, _ -> },
            minimumInferenceIntervalMs = 100,
            clockMillis = { now }
        )

        forceGc()
        val javaHeapBefore = usedJavaHeap()
        val nativeHeapBefore = Debug.getNativeHeapAllocatedSize()
        repeat(FRAME_COUNT) { frame ->
            now += 101
            analyzer.analyze(imageProxy(rotations[frame % rotations.size]) { closedImages++ })
        }
        forceGc()
        val javaGrowth = usedJavaHeap() - javaHeapBefore
        val nativeGrowth = Debug.getNativeHeapAllocatedSize() - nativeHeapBefore

        assertEquals(FRAME_COUNT, closedImages)
        assertEquals(FRAME_COUNT, seenRotations.size)
        assertEquals(rotations.toSet(), seenRotations.toSet())
        // Wide regression guard: frame-sized leaks grow by hundreds of MB over this run.
        assertTrue("Java heap grew by $javaGrowth bytes", javaGrowth < MAX_HEAP_GROWTH_BYTES)
        assertTrue("Native heap grew by $nativeGrowth bytes", nativeGrowth < MAX_HEAP_GROWTH_BYTES)
    }

    @Test fun `throttled frames are still closed`() {
        var detections = 0
        var closes = 0
        var now = 1_000L
        val analyzer = ObjectDetectorAnalyzer(
            engine = ObjectDetectorEngine { detections++; emptyList() },
            onObjectsTracked = { _, _, _ -> },
            minimumInferenceIntervalMs = 100,
            clockMillis = { now }
        )

        repeat(20) {
            analyzer.analyze(imageProxy(0) { closes++ })
            now += 10
        }

        assertEquals(2, detections)
        assertEquals(20, closes)
    }

    private fun imageProxy(
        rotation: Int,
        width: Int = 0,
        height: Int = 0,
        format: Int = 0,
        onClose: () -> Unit
    ): ImageProxy {
        val imageInfo = proxy<ImageInfo> { method ->
            when (method) {
                "getRotationDegrees" -> rotation
                "getTimestamp" -> 0L
                else -> null
            }
        }
        return proxy { method ->
            when (method) {
                "getImageInfo" -> imageInfo
                "getWidth" -> width
                "getHeight" -> height
                "getFormat" -> format
                "getPlanes" -> emptyArray<ImageProxy.PlaneProxy>()
                "getCropRect" -> android.graphics.Rect()
                "close" -> onClose()
                else -> null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
            when (method.name) {
                "toString" -> "Test${T::class.java.simpleName}"
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.firstOrNull()
                else -> answer(method.name)
            }
        } as T

    private fun forceGc() {
        repeat(3) { System.gc(); System.runFinalization() }
    }

    private fun usedJavaHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private companion object {
        const val FRAME_COUNT = 900
        const val MAX_HEAP_GROWTH_BYTES = 16L * 1024 * 1024
    }
}
