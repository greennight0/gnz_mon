package com.example.ui.camera

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StableGeometryTrackerTest {
    private var now = 0L
    private val tracker = GeometryTracker { now }
    private fun detection(x: Float = .2f, score: Float = .9f) = DetectorOutput(RectF(x,.2f,x+.3f,.6f),score)
    private fun update(vararg d: DetectorOutput) = tracker.update(d.toList(), .22f).also { now += 33 }
    @Test fun noiseAndDuplicateBoxesNeverCreateSelectableTracks() {
        repeat(5) { assertTrue(update(detection(score=.3f)).isEmpty()) }
        repeat(2) { assertTrue(update(detection(), detection(.201f)).isEmpty()) }
        assertEquals(1, update(detection(), detection(.201f)).size)
    }
    @Test fun consecutiveHitsAreRequiredAndLowScoresOnlyMaintainConfirmedTracks() {
        update(detection()); update(); update(detection()); update(detection())
        assertTrue(update(detection(score=.4f)).isEmpty())
        update(detection()); update(detection())
        val stable = update(detection()).single()
        assertEquals(stable.id, update(detection(score=.23f)).single().id)
    }
    @Test fun briefDropoutIsRetainedButNotScannableAndExpiresByMonotonicTime() {
        repeat(3) { update(detection()) }
        assertFalse(update().single().isObserved)
        now += 501
        assertTrue(update().isEmpty())
    }
    @Test fun tenSecondsOfStaticJitterKeepIdAndReduceCenterRmsByHalf() {
        repeat(3) { update(detection()) }
        var raw = 0.0; var filtered = 0.0
        val ids = mutableSetOf<Int>()
        repeat(303) { i ->
            val offset = if (i % 2 == 0) .015f else -.015f
            val b = update(detection(.2f + offset)).single()
            ids += b.id
            raw += offset * offset
            filtered += Math.pow((b.normalizedRect.centerX() - .35f).toDouble(), 2.0)
        }
        assertEquals(1, ids.size)
        assertTrue(kotlin.math.sqrt(filtered / raw) <= .5)
    }
    @Test fun nearbyObjectsRetainSeparateIdsRegardlessOfDetectionOrder() {
        repeat(3) { update(detection(.05f), detection(.6f)) }
        val first = update(detection(.06f), detection(.59f))
        val next = update(detection(.58f), detection(.07f))
        assertEquals(first.map { it.id }, next.map { it.id })
        assertTrue(next[0].normalizedRect.centerX() < next[1].normalizedRect.centerX())
    }
    @Test fun malformedBoxesAreIgnored() {
        repeat(4) { assertTrue(update(detection(Float.NaN), detection(score=Float.NaN),
            DetectorOutput(RectF(2f,2f,3f,3f),.99f)).isEmpty()) }
    }
}
