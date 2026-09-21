package com.example.data.classifier

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.Accelerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end golden inference against PlantNet-300K upstream image images/1.jpg.
 * Source: https://github.com/plantnet/PlantNet-300K/blob/main/images/1.jpg
 * SHA-256: 51708858ba06f339d3a5761964c7dec2cb67e93d5c9784b08ee7d3bce704e730
 */
@RunWith(AndroidJUnit4::class)
class PlantNetGoldenInstrumentedTest {
    @Test
    fun upstreamThistleMatchesReferenceTopOneAndLogits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("plantnet300k_upstream_1.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        val runner = LiteRtPlantNetRunner(instrumentation.targetContext, Accelerator.CPU)
        try {
            val logits = runner.run(PlantNetPreprocessor.toNchw(bitmap))
            val topOne = logits.indices.maxBy { logits[it] }

            assertEquals(4, topOne)
            assertEquals("Cirsium vulgare (Savi) Ten.", PlantNetLabels.NAMES[topOne])
            assertEquals(-64.5483f, logits[4], 3.0f)
            assertEquals(-68.6506f, logits[365], 3.0f)
            assertTrue(logits[4] > logits[365])
        } finally {
            runner.close()
            bitmap.recycle()
        }
    }
}
