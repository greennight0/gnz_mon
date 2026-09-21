package com.example.data.classifier

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.RecognitionResult
import com.example.ui.camera.expandedSquareRect
import com.example.ui.camera.squareTargetBitmap
import com.google.ai.edge.litert.Accelerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureNanoTime

/** Paired input comparison, not an assertion that higher confidence means better accuracy. */
@RunWith(AndroidJUnit4::class)
class PlantNetComparisonInstrumentedTest {
    @Test fun compareLegacyCenterCropWithWholeSpecimen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manifestPath = InstrumentationRegistry.getArguments().getString("plantBenchmarkManifest")
        val manifest = manifestPath?.let { File(it) }
        val cases = if (manifest != null) JSONArray(manifest.readText()) else JSONArray("""
            [{"image":"plantnet300k_upstream_1.jpg","category":"flower",
              "scientificName":"Cirsium vulgare (Savi) Ten."}]
        """.trimIndent())
        require(cases.length() > 0)
        val runner = LiteRtPlantNetRunner(instrumentation.targetContext, Accelerator.CPU)
        val rows = JSONArray()
        try {
            // Warm up separately, so first-run compilation does not bias the first variant.
            runner.run(FloatArray(3 * 224 * 224))
            for (index in 0 until cases.length()) {
                val case = cases.getJSONObject(index)
                val category = case.getString("category")
                require(category in setOf("plant", "leaf", "flower", "fruit", "object"))
                val truth = if (category == "object") null else case.getString("scientificName")
                val name = case.getString("image")
                val source = if (manifest != null) BitmapFactory.decodeFile(File(manifest.parentFile, name).path)
                    else instrumentation.context.assets.open(name).use { BitmapFactory.decodeStream(it) }
                requireNotNull(source) { "Cannot decode $name" }
                try {
                    val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
                    val rect = case.optJSONArray("rect")?.let {
                        RectF(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(),
                            it.getDouble(2).toFloat(), it.getDouble(3).toFloat())
                    } ?: bounds
                    require(!rect.isEmpty && bounds.contains(rect))
                    for (variant in listOf("legacy", "whole_specimen")) {
                        lateinit var result: RecognitionResult
                        val elapsed = measureNanoTime {
                            val crop = if (variant == "legacy") {
                                val side = minOf(rect.width(), rect.height()).toInt()
                                Bitmap.createBitmap(source, (rect.centerX() - side / 2f).toInt(),
                                    (rect.centerY() - side / 2f).toInt(), side, side)
                            } else squareTargetBitmap(source, expandedSquareRect(rect, source.width, source.height))
                            try {
                                result = OnDeviceSpeciesClassifier.resolveLogits(runner.run(PlantNetPreprocessor.toNchw(crop)))
                            } finally {
                                if (crop !== source) crop.recycle()
                            }
                        }
                        val accepted = result as? RecognitionResult.Organism
                        rows.put(JSONObject().put("image", name).put("category", category)
                            .put("variant", variant).put("accepted", accepted != null)
                            .put("correct", accepted != null && accepted.species.scientificName == truth)
                            .put("prediction", accepted?.species?.scientificName ?: JSONObject.NULL)
                            .put("milliseconds", elapsed / 1_000_000.0))
                    }
                } finally { source.recycle() }
            }
        } finally { runner.close() }
        val summaries = JSONArray()
        val allRows = (0 until rows.length()).map(rows::getJSONObject)
        for (variant in listOf("legacy", "whole_specimen")) {
            for (category in listOf("all", "plant", "leaf", "flower", "fruit", "object")) {
                val group = allRows.filter { it.getString("variant") == variant &&
                    (category == "all" || it.getString("category") == category) }
                if (group.isEmpty()) continue
                val accepted = group.count { it.getBoolean("accepted") }
                val times = group.map { it.getDouble("milliseconds") }.sorted()
                summaries.put(JSONObject().put("variant", variant).put("category", category)
                    .put("count", group.size).put("accepted", accepted)
                    .put("acceptedPrecision", if (accepted == 0) JSONObject.NULL else
                        group.count { it.getBoolean("correct") }.toDouble() / accepted)
                    .put("uncertainRate", 1.0 - accepted.toDouble() / group.size)
                    .put("meanMs", times.average())
                    .put("p95Ms", times[(kotlin.math.ceil(times.size * .95).toInt() - 1).coerceAtLeast(0)]))
            }
        }
        val report = JSONObject().put("smokeOnly", manifest == null)
            .put("accelerator", "CPU").put("timing", "crop + preprocessing + inference; excludes camera capture")
            .put("summary", summaries).put("samples", rows)
        File(instrumentation.targetContext.getExternalFilesDir(null), "plantnet-comparison.json").writeText(report.toString(2))
    }
}
