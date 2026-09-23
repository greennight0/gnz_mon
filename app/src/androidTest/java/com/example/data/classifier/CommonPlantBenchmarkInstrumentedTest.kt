package com.example.data.classifier

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.RecognitionResult
import com.example.ui.camera.squareTargetBitmap
import com.google.ai.edge.litert.Accelerator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureNanoTime

/** No Room writes. The report always distinguishes regression fixtures from field acceptance. */
@RunWith(AndroidJUnit4::class)
class CommonPlantBenchmarkInstrumentedTest {
    @Test fun runLabelledRegressionSet() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val directory = "common-plant-benchmark"
        val cases = JSONArray(assets.open("$directory/manifest.json").bufferedReader().use { it.readText() })
        val rows = JSONArray()
        var firstView = emptyList<CommonPrediction>()
        var secondView = emptyList<CommonPrediction>()
        var decisionReason = ""
        val common = MediaPipeCommonRunner(instrumentation.targetContext)
        val plant = OnDeviceSpeciesClassifier(LiteRtPlantNetRunner(instrumentation.targetContext, Accelerator.CPU))
        val policy = CommonPlantPolicy.load(instrumentation.targetContext)
        CommonPlantClassifier(common, plant, policy) { a, b, decision ->
            firstView = a; secondView = b; decisionReason = decision.reason
        }.use { classifier ->
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val source = assets.open("$directory/${case.getString("image")}").use { BitmapFactory.decodeStream(it) }
                val bounds = RectF(0f,0f,source.width.toFloat(),source.height.toFloat())
                val expanded = squareTargetBitmap(source, RectF(bounds).apply { inset(-width()*.05f,-height()*.05f) })
                try {
                    lateinit var result: RecognitionResult
                    val elapsed = measureNanoTime { result = classifier.classifyPair(source,expanded) {} }
                    val accepted = result is RecognitionResult.CommonPlant || result is RecognitionResult.Organism
                    val code = (result as? RecognitionResult.CommonPlant)?.groupCode
                    val scientific = (result as? RecognitionResult.Organism)?.species?.scientificName
                    val correct = accepted && ((code != null && code == case.optString("groupCode")) ||
                        (scientific != null && scientific == case.optString("scientificName")))
                    rows.put(JSONObject().put("image",case.getString("image")).put("split",case.getString("split"))
                        .put("truth",case.optString("groupCode")).put("category",case.getString("category"))
                        .put("prediction",code ?: scientific ?: result.javaClass.simpleName)
                        .put("accepted",accepted).put("correct",correct).put("milliseconds",elapsed/1e6)
                        .put("reason", decisionReason)
                        .put("firstView", JSONObject(firstView.associate { it.label to it.score }))
                        .put("secondView", JSONObject(secondView.associate { it.label to it.score }))
                        .put("suggestions", JSONArray((result as? RecognitionResult.Uncertain)?.commonCandidates
                            ?.map { it.groupCode } ?: emptyList<String>())))
                } finally { source.recycle(); expanded.recycle() }
            }
        }
        val summaries = JSONArray()
        val all = (0 until rows.length()).map(rows::getJSONObject)
        for (split in listOf("calibration","acceptance")) {
            val group = all.filter { it.getString("split") == split }
            val accepted = group.count { it.getBoolean("accepted") }
            val bananas = group.filter { it.getString("truth") == "banana" }
            val precision = if (accepted == 0) null else group.count { it.getBoolean("correct") }.toDouble()/accepted
            val bananaRecall = if (bananas.isEmpty()) null else bananas.count { it.getBoolean("correct") }.toDouble()/bananas.size
            summaries.put(JSONObject().put("split",split).put("count",group.size).put("accepted",accepted)
                .put("acceptedPrecision",precision ?: JSONObject.NULL)
                .put("rejectionRate",1.0 - accepted.toDouble()/group.size)
                .put("bananaRecall",bananaRecall ?: JSONObject.NULL)
                .put("numericalGatePassed",precision != null && precision >= .95 && bananaRecall != null && bananaRecall >= .90))
        }
        val report = JSONObject().put("fieldAcceptance",false)
            .put("limitations","Small source-labelled studio regression set; not independent field validation")
            .put("policyVersion", policy.version)
            .put("device",android.os.Build.MODEL).put("rows",rows).put("summaries",summaries)
        File(instrumentation.targetContext.getExternalFilesDir(null),"common-plant-benchmark.json").writeText(report.toString(2))
        assertEquals(cases.length(), rows.length())
        if (InstrumentationRegistry.getArguments().getString("enforceAccuracy") == "true") {
            assertTrue("See common-plant-benchmark.json", summaries.getJSONObject(1).getBoolean("numericalGatePassed"))
        }
    }
}
