package com.example.ui.camera

import android.graphics.Matrix
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TargetSnapshotTest {
    @Test fun sensorMappingPreservesFullBufferOriginAndDifferentAspectRatios() {
        // A 300x200 preview sees sensor [200,100,800,500], while capture retains
        // the full 1600x1200 buffer. Preview and capture do not share a crop origin.
        val sensorToView = Matrix().apply {
            setScale(.5f, .5f)
            postTranslate(-100f, -50f)
        }
        val sensorToBuffer = Matrix().apply { setScale(2f, 2f) }
        val geometry = TargetCaptureGeometry(RectF(0f, 0f, 100f, 100f), sensorToView)
        assertRect(RectF(400f, 200f, 800f, 600f), geometry.bufferRect(sensorToBuffer))
        // Some capture pipelines also translate their output buffer relative to the sensor.
        sensorToBuffer.postTranslate(30f, 40f)
        assertRect(RectF(430f, 240f, 830f, 640f), geometry.bufferRect(sensorToBuffer))
    }

    @Test fun mirroredPreviewMapsIntoFullBufferBeforeEveryRotation() {
        val sensorToView = Matrix().apply {
            setScale(-.5f, .5f)
            postTranslate(400f, 0f)
        }
        val geometry = TargetCaptureGeometry(RectF(50f, 20f, 150f, 120f), sensorToView)
        val expected = listOf(RectF(500f, 40f, 700f, 240f), RectF(360f, 500f, 560f, 700f),
            RectF(100f, 360f, 300f, 560f), RectF(40f, 100f, 240f, 300f))
        for ((index, degrees) in listOf(0, 90, 180, 270).withIndex()) {
            val raw = geometry.bufferRect(Matrix())
            val (width, height) = if (degrees % 180 == 0) 800 to 600 else 600 to 800
            assertRect(expected[index], mapAndClampTargetRect(raw, uprightTransform(800, 600, degrees), width, height))
        }
    }

    @Test fun sensorRegionIsUnaffectedByLaterPreviewAndTrackingChanges() {
        val liveRect = RectF(10f, 20f, 110f, 220f)
        val liveTransform = Matrix().apply { setScale(.5f, .5f) }
        val geometry = TargetCaptureGeometry(liveRect, liveTransform)
        liveRect.set(0f, 0f, 1f, 1f)
        liveTransform.setScale(10f, 10f)
        assertRect(RectF(20f, 40f, 220f, 440f), geometry.bufferRect(Matrix()))
        geometry.bufferRect(Matrix()).setEmpty()
        assertRect(RectF(20f, 40f, 220f, 440f), geometry.bufferRect(Matrix()))
    }

    @Test fun missingSingularAndNonFiniteTransformsFailInsteadOfGuessing() {
        val rect = RectF(10f, 20f, 110f, 220f)
        val singular = Matrix().apply { setScale(0f, 1f) }
        val invalid = Matrix().apply { setValues(floatArrayOf(Float.NaN, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) }
        for (transform in listOf(null, singular, invalid)) {
            assertThrows(IllegalStateException::class.java) { TargetCaptureGeometry(rect, transform) }
        }
        val geometry = TargetCaptureGeometry(rect, Matrix())
        assertThrows(IllegalStateException::class.java) { geometry.bufferRect(null) }
        assertThrows(IllegalArgumentException::class.java) { geometry.bufferRect(singular) }
    }

    @Test fun targetOutsideCapturedImageIsRejectedInsteadOfBecomingOnePixel() {
        assertThrows(IllegalArgumentException::class.java) {
            mapAndClampTargetRect(RectF(1000f, 1000f, 1200f, 1200f), Matrix(), 640, 480)
        }
    }

    @Test fun squareExpansionContainsSpecimenAtEveryEdge() {
        for ((w, h) in listOf(640 to 480, 480 to 640)) {
            for (rect in listOf(RectF(0f, 0f, 80f, 200f), RectF(w - 80f, 0f, w.toFloat(), 200f),
                RectF(0f, h - 200f, 80f, h.toFloat()), RectF(w - 80f, h - 200f, w.toFloat(), h.toFloat()),
                RectF(0f, 0f, w.toFloat(), h.toFloat()))) {
                val expanded = expandedSquareRect(rect, w, h)
                assertEquals(expanded.width(), expanded.height(), .01f)
                org.junit.Assert.assertTrue(expanded.contains(rect))
                if (expanded.width() <= minOf(w, h)) {
                    org.junit.Assert.assertTrue(RectF(0f, 0f, w.toFloat(), h.toFloat()).contains(expanded))
                }
            }
        }
    }

    @Test fun allRotationsMapRawPixelsToUprightPositiveBounds() {
        val expected = listOf(RectF(10f, 20f, 110f, 220f), RectF(260f, 10f, 460f, 110f),
            RectF(530f, 260f, 630f, 460f), RectF(20f, 530f, 220f, 630f))
        for ((index, degrees) in listOf(0, 90, 180, 270).withIndex()) {
            val rect = RectF(10f, 20f, 110f, 220f)
            uprightTransform(640, 480, degrees).mapRect(rect)
            assertRect(expected[index], rect)
        }
    }

    private fun assertRect(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, .01f)
        assertEquals(expected.top, actual.top, .01f)
        assertEquals(expected.right, actual.right, .01f)
        assertEquals(expected.bottom, actual.bottom, .01f)
    }

    @Test fun requestIsUnaffectedByTrackingRectMutationWhileSnapshotStarts() {
        val liveTrackingRect = RectF(10f, 20f, 110f, 220f)
        val request = TargetCaptureRequest(17, liveTrackingRect)
        liveTrackingRect.set(500f, 500f, 600f, 600f)

        assertEquals(17, request.trackId)
        assertRect(RectF(10f, 20f, 110f, 220f), request.copyPreviewRect())

        request.copyPreviewRect().setEmpty()
        assertRect(RectF(10f, 20f, 110f, 220f), request.copyPreviewRect())
    }

    @Test fun backCameraPortraitMapsWithoutMirror() {
        val transform = Matrix().apply { setScale(2f, 2f) }
        assertRect(RectF(20f, 40f, 220f, 440f),
            mapAndClampTargetRect(RectF(10f, 20f, 110f, 220f), transform, 1000, 1600))
    }

    @Test fun frontCameraLandscapeAppliesPreviewMirror() {
        val transform = Matrix().apply {
            setScale(-2f, 2f)
            postTranslate(1200f, 0f)
        }
        assertRect(RectF(980f, 40f, 1180f, 440f),
            mapAndClampTargetRect(RectF(10f, 20f, 110f, 220f), transform, 1200, 800))
    }

    @Test fun rotatedCaptureMapsPortraitPreviewIntoLandscapeBuffer() {
        val transform = Matrix().apply {
            setRotate(90f)
            postTranslate(1600f, 0f)
        }
        assertRect(RectF(1380f, 10f, 1580f, 110f),
            mapAndClampTargetRect(RectF(10f, 20f, 110f, 220f), transform, 1600, 1200))
    }

    @Test fun fillCenterWithDifferentAspectRatioIncludesCropOffset() {
        // Inverse FILL_CENTER: remove the horizontal preview crop, then scale to capture pixels.
        val transform = Matrix().apply {
            setTranslate(140f, 0f)
            postScale(2f, 2f)
        }
        assertRect(RectF(480f, 100f, 880f, 500f),
            mapAndClampTargetRect(RectF(100f, 50f, 300f, 250f), transform, 1920, 1080))
    }

    @Test fun previewBitmapWithDifferentSizeUsesPreviewCoordinates() {
        val viewToBitmap = Matrix().apply { setScale(0.5f, 0.25f) }
        assertRect(RectF(50f, 25f, 150f, 75f),
            mapAndClampTargetRect(RectF(100f, 100f, 300f, 300f), viewToBitmap, 500, 250))
    }

    @Test fun boxNearImageEdgeIsClampedBeforeCrop() {
        assertRect(RectF(0f, 0f, 640f, 480f),
            mapAndClampTargetRect(RectF(-25f, -10f, 700f, 510f), Matrix(), 640, 480))
    }
}
