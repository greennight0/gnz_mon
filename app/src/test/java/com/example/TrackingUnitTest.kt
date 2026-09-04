package com.example

import android.app.Application
import android.graphics.RectF
import com.example.data.model.TrackedBoundingBox
import com.example.data.model.TrackingAlgorithm
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingUnitTest {

    @Test
    fun testTrackingAlgorithmsConfigured() {
        val yolo = TrackingAlgorithm.YOLO_BYTE_TRACKER
        assertEquals("YOLOv8 + BYTETracker", yolo.titleEn)
        assertTrue(yolo.latencyMs in 10..20)
        assertNotNull(yolo.descriptionVi)

        val ssd = TrackingAlgorithm.SSD_DEEP_SORT
        assertEquals("SSD MobileNet + DeepSORT", ssd.titleEn)
        assertTrue(ssd.latencyMs in 15..30)
        assertNotNull(ssd.descriptionVi)
    }

    @Test
    fun testTrackedBoundingBoxModel() {
        val rect = RectF(0.2f, 0.3f, 0.7f, 0.8f)
        val box = TrackedBoundingBox(
            id = 101,
            normalizedRect = rect,
            label = "Flora (Thực vật)",
            confidence = 0.94f,
            isSelected = true,
            trackingFrames = 15,
            velocityX = 0.05f,
            velocityY = -0.02f
        )

        assertEquals(101, box.id)
        assertEquals("Flora (Thực vật)", box.label)
        assertEquals(0.94f, box.confidence, 0.001f)
        assertTrue(box.isSelected)
        assertEquals(15, box.trackingFrames)
        assertEquals(0.05f, box.velocityX, 0.001f)
    }

    @Test
    fun testViewModelTrackingState() {
        val app = RuntimeEnvironment.getApplication()
        val viewModel = MainViewModel(app)

        assertEquals(TrackingAlgorithm.YOLO_BYTE_TRACKER, viewModel.activeAlgorithm.value)
        viewModel.toggleAlgorithm()
        assertEquals(TrackingAlgorithm.SSD_DEEP_SORT, viewModel.activeAlgorithm.value)
        viewModel.toggleAlgorithm()
        assertEquals(TrackingAlgorithm.YOLO_BYTE_TRACKER, viewModel.activeAlgorithm.value)

        // Test object tracking update
        val mockBoxes = listOf(
            TrackedBoundingBox(
                id = 1,
                normalizedRect = RectF(0.1f, 0.1f, 0.5f, 0.5f),
                label = "Flora",
                confidence = 0.92f
            ),
            TrackedBoundingBox(
                id = 2,
                normalizedRect = RectF(0.6f, 0.6f, 0.9f, 0.9f),
                label = "Fauna",
                confidence = 0.87f
            )
        )

        viewModel.onObjectsTracked(mockBoxes, 18)
        assertEquals(2, viewModel.trackedObjects.value.size)
        assertEquals(18, viewModel.inferenceLatencyMs.value)

        // Select track ID #2
        viewModel.selectTrack(2)
        assertEquals(2, viewModel.selectedTrackId.value)
        viewModel.onObjectsTracked(mockBoxes, 15)
        assertTrue(viewModel.trackedObjects.value.first { it.id == 2 }.isSelected)
    }
}
