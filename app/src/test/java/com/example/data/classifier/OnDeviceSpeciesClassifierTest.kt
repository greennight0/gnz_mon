package com.example.data.classifier

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.RecognitionResult
import com.google.ai.edge.litert.Accelerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnDeviceSpeciesClassifierTest {
    @Test fun `label map contains 1081 unique scientific names`() {
        assertEquals(1081, PlantNetLabels.NAMES.size)
        assertEquals(1081, PlantNetLabels.NAMES.toSet().size)
    }

    @Test fun `preprocessor emits normalized RGB planes in NCHW order`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(255, 0, 128))
        }
        val output = PlantNetPreprocessor.toNchw(bitmap)
        val plane = PlantNetPreprocessor.SIZE * PlantNetPreprocessor.SIZE
        assertEquals(3 * plane, output.size)
        assertEquals((1f - .485f) / .229f, output[0], .0001f)
        assertEquals((0f - .456f) / .224f, output[plane], .0001f)
        assertEquals(((128f / 255f) - .406f) / .225f, output[2 * plane], .0001f)
    }

    @Test fun `confident logits map to scientific-name species`() {
        val logits = FloatArray(1081) { -10f }.apply { this[7] = 10f }
        val result = OnDeviceSpeciesClassifier.resolveLogits(logits)
        assertTrue(result is RecognitionResult.Organism)
        assertEquals(
            PlantNetLabels.NAMES[7],
            (result as RecognitionResult.Organism).species.scientificName
        )
    }

    @Test fun `flat logits are rejected as unknown`() {
        assertTrue(
            OnDeviceSpeciesClassifier.resolveLogits(FloatArray(1081)) is RecognitionResult.Uncertain
        )
    }

    private fun logits(first: Float, second: Float): FloatArray {
        val remainder = (1f - first - second) / 1079
        return FloatArray(1081) { kotlin.math.ln(remainder) }.apply {
            this[7] = kotlin.math.ln(first)
            this[9] = kotlin.math.ln(second)
        }
    }

    @Test fun `34 and 67 percent are uncertain and preserve ranked suggestions`() {
        for (score in listOf(.34f, .67f, .699f)) {
            val result = OnDeviceSpeciesClassifier.resolveLogits(logits(score, .2f)) as RecognitionResult.Uncertain
            assertEquals(3, result.candidates.size)
            assertEquals(PlantNetLabels.NAMES[7], result.candidates[0].scientificName)
            assertEquals(PlantNetLabels.NAMES[9], result.candidates[1].scientificName)
            assertEquals(score, result.candidates[0].score, .0001f)
            assertTrue(result.candidates.zipWithNext().all { (a, b) -> a.score >= b.score })
        }
    }

    @Test fun `threshold and margin are applied independently`() {
        assertTrue(OnDeviceSpeciesClassifier.resolveLogits(logits(.701f, .2f)) is RecognitionResult.Organism)
        assertTrue(OnDeviceSpeciesClassifier.resolveLogits(logits(.46f, .45f)) is RecognitionResult.Uncertain)
        // With the production 0.70 threshold, a 0.15 margin is mathematically guaranteed.
        // Lower only the test threshold to exercise the independent margin gate.
        assertTrue(OnDeviceSpeciesClassifier.resolveLogits(logits(.55f, .44f), .5f, .15f) is RecognitionResult.Uncertain)
    }

    @Test fun `malformed model output is rejected`() {
        for (values in listOf(FloatArray(2), FloatArray(1081) { Float.NaN }, FloatArray(1081) { Float.POSITIVE_INFINITY })) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                OnDeviceSpeciesClassifier.resolveLogits(values)
            }
        }
    }

    @Test fun `portrait preprocessing preserves top and bottom of specimen`() {
        val bitmap = Bitmap.createBitmap(20, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        for (x in 0 until 20) for (y in 0 until 15) {
            bitmap.setPixel(x, y, Color.RED)
            bitmap.setPixel(x, 99 - y, Color.BLUE)
        }
        val output = PlantNetPreprocessor.toNchw(bitmap)
        val plane = 224 * 224
        assertTrue(output[10 * 224 + 112] > 2f)
        assertTrue(output[2 * plane + 213 * 224 + 112] > 2f)
        bitmap.recycle()
    }

    @Test fun `runner initialization falls back from GPU to CPU`() {
        val attempts = mutableListOf<Accelerator>()
        val classifier = OnDeviceSpeciesClassifier.createWithFallback { accelerator ->
            attempts += accelerator
            if (accelerator == Accelerator.GPU) error("GPU unavailable")
            FakeRunner()
        }
        assertEquals(listOf(Accelerator.GPU, Accelerator.CPU), attempts)
        classifier.close()
    }

    @Test(expected = IllegalStateException::class)
    fun `runner initialization fails when GPU and CPU are unavailable`() {
        OnDeviceSpeciesClassifier.createWithFallback { error("unavailable") }
    }

    private class FakeRunner : PlantNetRunner {
        override fun run(input: FloatArray) = FloatArray(1081)
        override fun close() = Unit
    }
}
