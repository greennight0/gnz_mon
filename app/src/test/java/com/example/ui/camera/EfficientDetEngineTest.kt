package com.example.ui.camera

import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EfficientDetEngineTest {
    @Test fun failuresAreAttributedToEveryDetectorPipelineStage() {
        DetectorStage.values().filter { it != DetectorStage.UNKNOWN }.forEach { stage ->
            try {
                runDetectorStage(stage) { throw IllegalArgumentException("synthetic") }
                fail("Expected failure for $stage")
            } catch (error: DetectorStageException) {
                assertEquals(stage, error.stage)
                assertTrue(error.cause is IllegalArgumentException)
            }
        }
    }

    @Test fun runtimeClassificationUsesCauseTypeInsteadOfMessage() {
        assertTrue(IllegalStateException("localized or empty").hasPermanentRuntimeCause())
        assertTrue(!IllegalArgumentException("task runner has been closed").hasPermanentRuntimeCause())
    }

    @Test fun packagedModelCanBeLoadedFromAssets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.assets.open(EfficientDetLiteEngine.MODEL_ASSET).use { input ->
            val bytes = input.readBytes()
            assertTrue("The real model, not a marker file, must be packaged", bytes.size > 1_000_000)
            val prefix = bytes.copyOfRange(0, minOf(bytes.size, 128)).decodeToString()
            assertTrue("A Git LFS pointer must never be packaged", !prefix.startsWith("version https://git-lfs.github.com/spec/v1"))
        }
    }

    @Test fun decodesEfficientDetYxyxTensor() {
        val output = EfficientDetOutputDecoder.decode(
            floatArrayOf(.1f, .2f, .7f, .8f, 0f, 0f, 1f, 1f),
            floatArrayOf(.9f, .1f), .35f
        ).single()
        assertRect(RectF(.2f, .1f, .8f, .7f), output.box)
        assertEquals(.9f, output.score, 0f)
    }

    @Test fun rotatesBoxesAtZeroNinetyAndTwoSeventyDegrees() {
        val box = RectF(.1f, .2f, .4f, .6f)
        assertRect(RectF(.1f, .2f, .4f, .6f), EfficientDetOutputDecoder.rotate(box, 0))
        assertRect(RectF(.4f, .1f, .8f, .4f), EfficientDetOutputDecoder.rotate(box, 90))
        assertRect(RectF(.2f, .6f, .6f, .9f), EfficientDetOutputDecoder.rotate(box, 270))
    }

    @Test fun normalizesMappedPreviewBox() {
        assertRect(RectF(.1f, .1f, .9f, .8f), normalizePreviewRect(RectF(100f, 200f, 900f, 1600f), 1000, 2000))
    }

    @Test fun matchedDetectionIncrementsTrackingFramesAndRemainsUnselected() {
        val tracker = GeometryTracker()
        val detection = DetectorOutput(RectF(.1f, .2f, .4f, .6f), .9f)

        tracker.update(listOf(detection), threshold = .35f)
        val matched = tracker.update(listOf(detection), threshold = .35f).single()

        assertEquals(2, matched.trackingFrames)
        assertEquals(false, matched.isSelected)
    }

    @Test fun initializationFailureFallsBackWithoutStoppingAnalysis() {
        var reported: Exception? = null
        val fallback = createDetectorEngine({ reported = it }) { error("corrupt model") }
        assertTrue(reported is RecoverableDetectorInitializationException)
        assertTrue(fallback === DisabledObjectDetectorEngine)
    }

    @Test fun linkageFailureIsClassifiedAsIncompatibleRuntime() {
        var reported: Exception? = null
        val fallback = createDetectorEngine({ reported = it }) { throw UnsatisfiedLinkError("bad ABI") }
        assertTrue(reported is IncompatibleDetectorRuntimeException)
        assertTrue(fallback === DisabledObjectDetectorEngine)
    }

    private fun assertRect(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, .0001f); assertEquals(expected.top, actual.top, .0001f)
        assertEquals(expected.right, actual.right, .0001f); assertEquals(expected.bottom, actual.bottom, .0001f)
    }
}
