package com.example.data.classifier

import android.content.Context
import android.graphics.Bitmap
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
    fun resolve(a: List<CommonPrediction>, b: List<CommonPrediction>): RecognitionResult? {
        fun accepted(values: List<CommonPrediction>): CommonPrediction? {
            require(values.size >= 2 && values.all { it.score.isFinite() && it.score in 0f..1f })
            val ranked = values.sortedByDescending { it.score }
            return ranked[0].takeIf { it.score >= .75f && it.score - ranked[1].score >= .20f }
        }
        val first = accepted(a) ?: return null
        val second = accepted(b) ?: return null
        if (first.label != second.label) return RecognitionResult.Uncertain(emptyList())
        val score = minOf(first.score, second.score)
        val vi = names[first.label]
        if (vi != null) return RecognitionResult.CommonPlant(first.label, vi,
            if (first.label == "Granny Smith") "Apple" else first.label.replaceFirstChar { it.titlecase() }, score)
        if (isDeferred(first.label)) return RecognitionResult.Uncertain(emptyList())
        return RecognitionResult.NotOrganism("Outside plant/fruit scope", (score * 100).toInt())
    }
}

internal class MediaPipeCommonRunner(context: Context) : CommonRunner, AutoCloseable {
    private val classifier = ImageClassifier.createFromOptions(context.applicationContext,
        ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/common_plant.tflite").build())
            .setMaxResults(3).build())
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
    private val plants: SpeciesClassifier
) : SpeciesClassifier, AutoCloseable {
    private val mutex = Mutex()
    override suspend fun classify(bitmap: Bitmap, onPhase: (ScanTransportPhase) -> Unit): RecognitionResult {
        // Imported images have no pixels outside their boundary. Use a 10% inset and full image.
        val dx = (bitmap.width * .05f).toInt()
        val dy = (bitmap.height * .05f).toInt()
        val inset = Bitmap.createBitmap(bitmap, dx, dy, bitmap.width - 2 * dx, bitmap.height - 2 * dy)
        return try { classifyPair(inset, bitmap, onPhase) }
        finally { if (inset !== bitmap) inset.recycle() }
    }
    override suspend fun classifyPair(bitmap: Bitmap, expanded: Bitmap,
        onPhase: (ScanTransportPhase) -> Unit): RecognitionResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            onPhase(ScanTransportPhase.PREPARING)
            onPhase(ScanTransportPhase.CLASSIFYING)
            val a = common.run(bitmap)
            val b = common.run(expanded)
            val commonResult = CommonPlantCatalog.resolve(a, b)
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
            return try { CommonPlantClassifier(common, OnDeviceSpeciesClassifier.create(context)) }
            catch (e: Exception) { common.close(); throw e }
        }
    }
}
