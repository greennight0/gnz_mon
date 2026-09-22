package com.example.data.classifier

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.RecognitionResult
import com.google.ai.edge.litert.Accelerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Fixed tensors test inference numerics; JPEG tests the actual Android image pipeline.
 * Pillow and Canvas downsampling differ and must not share exact logit goldens.
 * Fixtures are reproducible with tools/generate_plantnet_golden.py. */
@RunWith(AndroidJUnit4::class)
class PlantNetGoldenInstrumentedTest {
    @Test fun fixedInputMatchesIndependentCpuReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val bytes = assets.open("plantnet300k_reference_input.f32").use { it.readBytes() }
        val reference = assets.open("plantnet300k_reference.json").bufferedReader().use { JSONObject(it.readText()) }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(reference.getString("inputSha256"), hash)
        assertEquals(3 * 224 * 224 * 4, bytes.size)
        val input = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(input)
        val runner = LiteRtPlantNetRunner(instrumentation.targetContext, Accelerator.CPU)
        try {
            val logits = runner.run(input)
            val expected = reference.getJSONArray("logits")
            assertEquals(expected.length(), logits.size)
            logits.forEachIndexed { index, actual ->
                assertEquals("Logit $index", expected.getDouble(index).toFloat(), actual, .005f)
            }
            val top = logits.indices.maxBy { logits[it] }
            assertEquals(reference.getInt("topOne"), top)
            assertEquals(reference.getString("scientificName"), PlantNetLabels.NAMES[top])
        } finally { runner.close() }
    }

    @Test fun androidImagePipelineIdentifiesUpstreamThistle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("plantnet300k_upstream_1.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        val runner = LiteRtPlantNetRunner(instrumentation.targetContext, Accelerator.CPU)
        try {
            val logits = runner.run(PlantNetPreprocessor.toNchw(bitmap))
            assertTrue(logits.all { it.isFinite() })
            val result = OnDeviceSpeciesClassifier.resolveLogits(logits)
            assertTrue("Known fixture should meet the production gate", result is RecognitionResult.Organism)
            val species = (result as RecognitionResult.Organism).species
            assertEquals("Cirsium vulgare (Savi) Ten.", species.scientificName)
            File(instrumentation.targetContext.getExternalFilesDir(null), "plantnet-android-golden.json").writeText(
                JSONObject().put("scientificName", species.scientificName)
                    .put("confidenceScore", species.confidenceScore).put("topOneLogit", logits[4]).toString(2))
        } finally {
            runner.close()
            bitmap.recycle()
        }
    }
}
