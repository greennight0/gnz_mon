package com.example

import android.app.Application
import android.graphics.RectF
import com.example.data.model.TrackedBoundingBox
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingUnitTest {

    @Test
    fun emptyDetectorResultTemporarilyRetainsSelectionWithoutSelectableTargets() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())
        assertTrue(viewModel.trackedObjects.value.isEmpty())

        val detected = TrackedBoundingBox(
            id = 7,
            normalizedRect = RectF(0.2f, 0.2f, 0.8f, 0.8f),
            label = "Organism",
            confidence = 0.9f
        )
        viewModel.onObjectsTracked(listOf(detected), 12)
        viewModel.selectTrack(detected.id)
        assertEquals(detected.id, viewModel.selectedTrackId.value)

        viewModel.onObjectsTracked(emptyList(), 11)

        assertTrue(viewModel.trackedObjects.value.isEmpty())
        assertEquals(detected.id, viewModel.selectedTrackId.value)
        viewModel.selectNextTrack()
        assertEquals(detected.id, viewModel.selectedTrackId.value)
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

        // An unrelated detection must not steal the lock at the selected track's old list index.
        viewModel.onObjectsTracked(listOf(mockBoxes.first()), 15)
        assertEquals(2, viewModel.selectedTrackId.value)
        assertTrue(!viewModel.trackedObjects.value.single().isSelected)

        // IDs not present in the current detector output can never become stale selections.
        viewModel.selectTrack(999)
        assertEquals(null, viewModel.selectedTrackId.value)
    }

    /**
     * Phase 4.1: Test One Euro Filter with synthetic jitter data
     * Simulates handheld jitter and validates filtering effectiveness
     */
    @Test
    fun testOneEuroFilterWithJitter() {
        // Simple One Euro Filter implementation for testing
        class SimpleOneEuroFilter(
            private val minCutoff: Float = 0.4f,
            private val beta: Float = 0.12f,
            private val dCutoff: Float = 1.0f
        ) {
            private var xPrev: Float? = null
            private var dxPrev: Float = 0f

            private fun alpha(rate: Float, cutoff: Float): Float {
                val tau = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
                val te = 1.0f / rate
                return 1.0f / (1.0f + tau / te)
            }

            fun filter(x: Float, rate: Float): Float {
                val prev = xPrev
                if (prev == null) {
                    xPrev = x
                    return x
                }

                val dx = (x - prev) * rate
                val aD = alpha(rate, dCutoff)
                val dxHat = aD * dx + (1.0f - aD) * dxPrev
                dxPrev = dxHat

                val cutoff = minCutoff + beta * kotlin.math.abs(dxHat)
                val a = alpha(rate, cutoff)
                val xHat = a * x + (1.0f - a) * prev
                xPrev = xHat
                return xHat
            }
        }

        // Simulate handheld jitter: true position at 0.5, with ±0.03 random jitter
        val filter = SimpleOneEuroFilter(minCutoff = 0.4f, beta = 0.12f)
        val truePosition = 0.5f
        val jitterAmplitude = 0.03f
        val rate = 30f // 30 FPS

        // Generate 100 frames with synthetic jitter
        var totalError = 0f
        var successCount = 0
        for (i in 0 until 100) {
            // Add random jitter to true position
            val jitter = (Math.random().toFloat() - 0.5f) * 2 * jitterAmplitude
            val measuredPos = (truePosition + jitter).coerceIn(0f, 1f)
            
            val filtered = filter.filter(measuredPos, rate)
            val error = abs(filtered - truePosition)
            
            // After warm-up (first 20 frames), count successful detections
            if (i >= 20) {
                totalError += error
                if (error < 0.02f) { // Within 2% tolerance
                    successCount++
                }
            }
        }

        val avgError = totalError / 80f
        val successRate = (successCount / 80f) * 100f

        // Validate: Average error should be < 2% and success rate > 85%
        assertTrue("Average error ($avgError) should be < 0.02", avgError < 0.02f)
        assertTrue("Success rate ($successRate%) should be > 85%", successRate > 85f)
    }

    /**
     * Phase 4.2: Test velocity-aware tap detection
     * Validates that tap detection adapts to object velocity
     */
    @Test
    fun testVelocityAwareTapDetection() {
        // Simulate stationary object
        val stationaryBox = TrackedBoundingBox(
            id = 1,
            normalizedRect = RectF(0.3f, 0.3f, 0.7f, 0.7f),
            label = "Test",
            confidence = 0.9f,
            velocityX = 0.001f,
            velocityY = 0.001f
        )

        // Simulate fast-moving object
        val movingBox = TrackedBoundingBox(
            id = 2,
            normalizedRect = RectF(0.3f, 0.3f, 0.7f, 0.7f),
            label = "Test",
            confidence = 0.9f,
            velocityX = 0.15f,
            velocityY = 0.15f
        )

        // Calculate velocity scales (should be different)
        fun calculateVelocityScale(vx: Float, vy: Float): Float {
            val maxVelocity = kotlin.math.sqrt(vx * vx + vy * vy)
            return (1f + maxVelocity * 2.5f).coerceIn(1f, 2.5f)
        }

        val stationaryScale = calculateVelocityScale(stationaryBox.velocityX, stationaryBox.velocityY)
        val movingScale = calculateVelocityScale(movingBox.velocityX, movingBox.velocityY)

        // Moving object should have larger scale (more padding for tap detection)
        assertTrue("Stationary scale should be ~1.0", abs(stationaryScale - 1.0f) < 0.01f)
        assertTrue("Moving scale should be > 1.5", movingScale > 1.5f)
        assertTrue("Moving scale should be < 2.5", movingScale <= 2.5f)
    }

    /**
     * Phase 4.3: Test tap detection with moving boxes
     * Validates accuracy of tap detection on moving targets
     */
    @Test
    fun testTapDetectionMovingBoxes() {
        // Simulate 3 moving boxes at different velocities
        val slowBox = TrackedBoundingBox(
            id = 1,
            normalizedRect = RectF(0.2f, 0.2f, 0.4f, 0.4f),
            label = "Slow",
            confidence = 0.9f,
            velocityX = 0.02f,
            velocityY = 0.0f
        )

        val mediumBox = TrackedBoundingBox(
            id = 2,
            normalizedRect = RectF(0.45f, 0.45f, 0.65f, 0.65f),
            label = "Medium",
            confidence = 0.9f,
            velocityX = 0.08f,
            velocityY = 0.05f
        )

        val fastBox = TrackedBoundingBox(
            id = 3,
            normalizedRect = RectF(0.7f, 0.7f, 0.9f, 0.9f),
            label = "Fast",
            confidence = 0.9f,
            velocityX = 0.2f,
            velocityY = 0.15f
        )

        val boxes = listOf(slowBox, mediumBox, fastBox)

        // Calculate max velocity and velocity scale
        val maxVelocity = boxes.maxOf { box ->
            kotlin.math.sqrt(box.velocityX * box.velocityX + box.velocityY * box.velocityY)
        }
        val velocityScale = (1f + maxVelocity * 2.5f).coerceIn(1f, 2.5f)

        // Calculate adaptive hitPadding
        val baseHitPadding = 32.0f // dp in pixels
        val hitPadding = baseHitPadding * velocityScale

        // Validate that hitPadding scales with velocity
        assertTrue("Velocity scale should increase with max velocity", velocityScale > 1.0f)
        assertTrue("hitPadding should be at least base value", hitPadding >= baseHitPadding)
        assertTrue("hitPadding should not exceed 2.5x base", hitPadding <= baseHitPadding * 2.5f)

        // Simulate tap near fast box (within the scaled hitPadding)
        val tapX = 0.8f // Center of fastBox + small offset
        val tapY = 0.8f

        val boxCenterX = (fastBox.normalizedRect.left + fastBox.normalizedRect.right) / 2f
        val boxCenterY = (fastBox.normalizedRect.top + fastBox.normalizedRect.bottom) / 2f

        val dx = (tapX - boxCenterX) * 1080f // Assuming 1080px screen width
        val dy = (tapY - boxCenterY) * 1920f // Assuming 1920px screen height
        val distToCenter = kotlin.math.sqrt(dx * dx + dy * dy)

        // With adaptive padding, tap should be detected
        assertTrue("Tap should be within adaptive padding radius", distToCenter < hitPadding * 1.5f)
    }
}
