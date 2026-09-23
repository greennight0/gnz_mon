package com.example.ui.camera

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraPipelineStartupTest {
    @Test fun `cancelled initialization closes native engine before losing ownership`() = runTest {
        var closes = 0
        var returned = false
        lateinit var initialization: kotlinx.coroutines.Job
        initialization = launch {
            createOwnedDetectorEngine(StandardTestDispatcher(testScheduler)) {
                initialization.cancel()
                object : ObjectDetectorEngine {
                    override fun detect(image: androidx.camera.core.ImageProxy) = emptyList<com.example.data.model.TrackedBoundingBox>()
                    override fun close() { closes++ }
                }
            }
            returned = true
        }
        runCurrent()
        assertFalse(returned)
        assertEquals(1, closes)
    }
    @Test
    fun `full bind succeeds and does not invoke fallback`() {
        var fallbackCalls = 0

        val result = bindCameraUseCases(
            bindWithAnalysis = { "camera-with-analysis" },
            bindPreviewFallback = { fallbackCalls++; "preview-only" }
        )

        assertEquals("camera-with-analysis", result.camera)
        assertTrue(result.analysisBound)
        assertNull(result.detectorError)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `analysis bind failure keeps preview but reports detector failure`() {
        val bindFailure = IllegalArgumentException("unsupported use case combination")

        val result = bindCameraUseCases(
            bindWithAnalysis = { throw bindFailure },
            bindPreviewFallback = { "preview-only" }
        )

        assertEquals("preview-only", result.camera)
        assertFalse(result.analysisBound)
        assertNotNull(result.detectorError)
        assertEquals(bindFailure, result.detectorError?.cause)
    }

    @Test
    fun `missing first frame times out exactly once`() = runTest {
        val errors = mutableListOf<CameraAnalysisInitializationException>()
        val watchdog = FirstAnalysisFrameWatchdog(timeoutMillis = 1_000, onTimeout = errors::add)

        watchdog.start(this)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, errors.size)
        assertTrue(errors.single().message!!.contains("first frame"))
        advanceTimeBy(1_000)
        assertEquals(1, errors.size)
    }

    @Test
    fun `first frame cancels timeout`() = runTest {
        val errors = mutableListOf<CameraAnalysisInitializationException>()
        val watchdog = FirstAnalysisFrameWatchdog(timeoutMillis = 1_000, onTimeout = errors::add)

        watchdog.start(this)
        watchdog.onFirstFrame()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(errors.isEmpty())
    }
}
