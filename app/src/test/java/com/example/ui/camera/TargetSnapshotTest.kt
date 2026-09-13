package com.example.ui.camera

import android.graphics.Matrix
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSnapshotTest {
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
        assertRect(RectF(10f, 20f, 110f, 220f), request.previewRect)
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

    @Test fun boxNearImageEdgeIsClampedBeforeCrop() {
        assertRect(RectF(0f, 0f, 640f, 480f),
            mapAndClampTargetRect(RectF(-25f, -10f, 700f, 510f), Matrix(), 640, 480))
    }
}
