package com.example.data.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanTransportPhase
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class CommonPrediction(val label: String, val score: Float)
internal fun interface CommonRunner { fun run(bitmap: Bitmap): List<CommonPrediction> }

/** Names are common groups, never scientific species or cultivar identifications. */
internal object CommonPlantCatalog {
    val names = mapOf(
        "banana" to "Chuối", "orange" to "Cam", "lemon" to "Chanh vàng",
        "pineapple" to "Dứa", "jackfruit" to "Mít", "custard apple" to "Na",
        "pomegranate" to "Lựu", "strawberry" to "Dâu tây", "fig" to "Sung",
        "Granny Smith" to "Táo", "cucumber" to "Dưa chuột", "zucchini" to "Bí ngòi",
        "head cabbage" to "Bắp cải", "broccoli" to "Bông cải xanh", "cauliflower" to "Súp lơ",
        "spaghetti squash" to "Bí sợi", "acorn squash" to "Bí quả sồi",
        "butternut squash" to "Bí hồ lô", "artichoke" to "Atisô", "bell pepper" to "Ớt chuông",
        "cardoon" to "Atisô gai", "rapeseed" to "Cải dầu", "daisy" to "Hoa cúc",
        "yellow lady's slipper" to "Lan hài vàng", "corn" to "Ngô", "ear" to "Bắp ngô",
        "acorn" to "Quả sồi", "hip" to "Quả tầm xuân", "buckeye" to "Hạt dẻ ngựa"
    )
    private val deferred = setOf("hay", "mushroom", "coral fungus", "agaric", "gyromitra",
        "stinkhorn", "earthstar", "hen-of-the-woods", "bolete")
    fun isDeferred(label: String) = label in deferred
    fun resolve(a: List<CommonPrediction>, b: List<CommonPrediction>): RecognitionResult? =
        CommonPlantPolicy().decide(a, b).result
}

internal class MediaPipeCommonRunner(context: Context) : CommonRunner, AutoCloseable {
    private val classifier = ImageClassifier.createFromOptions(context.applicationContext,
        ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/common_plant.tflite").build())
            // Full vectors are needed for a meaningful mean across both views.
            .setMaxResults(1000).build())
    override fun run(bitmap: Bitmap): List<CommonPrediction> {
        // MPImage owns its bitmap; preserve the shared capture for PlantNet and the second view.
        val owned = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return BitmapImageBuilder(owned).build().use { image ->
            classifier.classify(image).classificationResult().classifications().single().categories()
                .map { CommonPrediction(it.categoryName(), it.score()) }
        }
    }
    override fun close() = classifier.close()
}

internal class CommonPlantClassifier(
    private val common: CommonRunner,
    private val plants: SpeciesClassifier,
    private val policy: CommonPlantPolicy = CommonPlantPolicy(),
    private val onDecision: (List<CommonPrediction>, List<CommonPrediction>, CommonDecision) -> Unit = { _, _, _ -> }
) : SpeciesClassifier, AutoCloseable {
    private val mutex = Mutex()
    override suspend fun classify(bitmap: Bitmap, onPhase: (ScanTransportPhase) -> Unit): RecognitionResult {
        // Preserve the entire imported specimen; never cut the tips off a banana bunch.
        val bounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val whole = com.example.ui.camera.squareTargetBitmap(bitmap, bounds)
        val expanded = com.example.ui.camera.squareTargetBitmap(bitmap,
            android.graphics.RectF(bounds).apply { inset(-width() * .05f, -height() * .05f) })
        return try { classifyPair(whole, expanded, onPhase) }
        finally { whole.recycle(); expanded.recycle() }
    }
    override suspend fun classifyPair(bitmap: Bitmap, expanded: Bitmap,
        onPhase: (ScanTransportPhase) -> Unit): RecognitionResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            onPhase(ScanTransportPhase.PREPARING)
            onPhase(ScanTransportPhase.CLASSIFYING)
            val a = common.run(bitmap)
            val b = common.run(expanded)
            val decision = policy.decide(a, b)
            onDecision(a, b, decision)
            if (BuildConfig.DEBUG) Log.d("CommonRecognition", "policy=${policy.version} " +
                "views=${bitmap.width}x${bitmap.height},${expanded.width}x${expanded.height} " +
                "a=${a.sortedByDescending { it.score }.take(3)} b=${b.sortedByDescending { it.score }.take(3)} " +
                "mean=${decision.combined.take(3)} reason=${decision.reason}")
            val commonResult = decision.result
            if (commonResult != null) return@withLock commonResult
            val first = plants.classify(bitmap, onPhase)
            val second = plants.classify(expanded, onPhase)
            when {
                first is RecognitionResult.Failure -> first
                second is RecognitionResult.Failure -> second
                first is RecognitionResult.Organism && second is RecognitionResult.Organism &&
                    first.species.scientificName == second.species.scientificName ->
                    RecognitionResult.Organism(first.species.copy(confidenceScore =
                        minOf(first.species.confidenceScore, second.species.confidenceScore)))
                else -> RecognitionResult.Uncertain(emptyList())
            }
        }
    }
    override fun close() { (common as? AutoCloseable)?.close(); (plants as? AutoCloseable)?.close() }
    companion object {
        fun create(context: Context): CommonPlantClassifier {
            val common = try { MediaPipeCommonRunner(context) }
            catch (e: Exception) { throw LocalModelUnavailableException(e) }
            return try {
                val policy = CommonPlantPolicy.load(context)
                CommonPlantClassifier(common, OnDeviceSpeciesClassifier.create(context), policy)
            }
            catch (e: Exception) { common.close(); throw e }
        }
    }
}
