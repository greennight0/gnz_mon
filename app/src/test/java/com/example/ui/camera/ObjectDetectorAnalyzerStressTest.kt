package com.example.ui.camera

import android.os.Debug
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
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

    private fun imageProxy(rotation: Int, onClose: () -> Unit): ImageProxy {
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
                "getWidth", "getHeight", "getFormat" -> 0
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
