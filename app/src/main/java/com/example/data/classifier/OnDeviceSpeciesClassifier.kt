package com.example.data.classifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.BuildConfig
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanTransportPhase
import com.example.data.repository.SpeciesCatalog
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

internal interface PlantNetRunner : AutoCloseable {
    fun run(input: FloatArray): FloatArray
}

class LocalModelUnavailableException(cause: Throwable) :
    IllegalStateException("The offline PlantNet model is unavailable", cause)

internal class LiteRtPlantNetRunner(
    context: Context,
    accelerator: Accelerator,
    modelAsset: String = BuildConfig.SPECIES_CLASSIFIER_MODEL_ASSET
) : PlantNetRunner {
    private val model = CompiledModel.create(
        context.assets, modelAsset, CompiledModel.Options(accelerator), null
    )
    private val inputs: List<TensorBuffer> = model.createInputBuffers()
    private val outputs: List<TensorBuffer> = model.createOutputBuffers()

    override fun run(input: FloatArray): FloatArray {
        inputs.single().writeFloat(input)
        model.run(inputs, outputs)
        return outputs.single().readFloat()
    }

    override fun close() = model.close()
}

/** PlantNet-300K inference. Images and predictions never leave the device. */
class OnDeviceSpeciesClassifier internal constructor(
    private val runner: PlantNetRunner,
    private val minimumScore: Float = MINIMUM_SCORE,
    private val minimumMargin: Float = MINIMUM_MARGIN
) : SpeciesClassifier, AutoCloseable {

    override suspend fun classify(
        bitmap: Bitmap,
        onPhase: (ScanTransportPhase) -> Unit
    ): RecognitionResult = withContext(Dispatchers.Default) {
        onPhase(ScanTransportPhase.PREPARING)
        val input = PlantNetPreprocessor.toNchw(bitmap)
        onPhase(ScanTransportPhase.CLASSIFYING)
        resolveLogits(runner.run(input), minimumScore, minimumMargin)
    }

    override fun close() = runner.close()

    companion object {
        const val MINIMUM_SCORE = 0.70f
        const val MINIMUM_MARGIN = 0.15f

        fun create(context: Context): OnDeviceSpeciesClassifier {
            val appContext = context.applicationContext
            return createWithFallback { accelerator ->
                LiteRtPlantNetRunner(appContext, accelerator)
            }
        }

        internal fun createWithFallback(
            factory: (Accelerator) -> PlantNetRunner
        ): OnDeviceSpeciesClassifier {
            val runner = try { factory(Accelerator.GPU) }
            catch (gpuError: Exception) {
                try { factory(Accelerator.CPU) }
                catch (cpuError: Exception) {
                    cpuError.addSuppressed(gpuError)
                    throw LocalModelUnavailableException(cpuError)
                }
            }
            return OnDeviceSpeciesClassifier(runner)
        }

        internal fun resolveLogits(
            logits: FloatArray,
            minimumScore: Float = MINIMUM_SCORE,
            minimumMargin: Float = MINIMUM_MARGIN
        ): RecognitionResult {
            require(logits.size == PlantNetLabels.NAMES.size) {
                "Expected ${PlantNetLabels.NAMES.size} PlantNet logits, got ${logits.size}"
            }
            require(logits.all { it.isFinite() }) { "Non-finite PlantNet logits" }
            val ranked = logits.indices.sortedByDescending { logits[it] }.take(3)
            val max = logits[ranked.first()]
            var sum = 0.0
            logits.forEach { sum += exp((it - max).toDouble()) }
            val firstScore = (exp((logits[ranked[0]] - max).toDouble()) / sum).toFloat()
            val secondScore = (exp((logits[ranked[1]] - max).toDouble()) / sum).toFloat()
            if (firstScore < minimumScore || firstScore - secondScore < minimumMargin) {
                return RecognitionResult.Uncertain(ranked.map { index ->
                    RecognitionResult.Candidate(
                        PlantNetLabels.NAMES[index],
                        (exp((logits[index] - max).toDouble()) / sum).toFloat()
                    )
                })
            }
            return RecognitionResult.Organism(
                SpeciesCatalog.fromScientificName(PlantNetLabels.NAMES[ranked[0]], firstScore)
            )
        }
    }
}

internal object PlantNetPreprocessor {
    const val SIZE = 224
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun toNchw(source: Bitmap): FloatArray {
        require(source.width > 0 && source.height > 0) { "Bitmap must not be empty" }
        val resized = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val side = maxOf(source.width, source.height)
            val matrix = Matrix().apply {
                postTranslate(-(source.width - side) / 2f, -(source.height - side) / 2f)
                postScale(SIZE.toFloat() / side, SIZE.toFloat() / side)
            }
            Canvas(resized).apply {
                drawColor(android.graphics.Color.rgb(124, 116, 104))
                drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val pixels = IntArray(SIZE * SIZE)
            resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            val plane = SIZE * SIZE
            return FloatArray(plane * 3).also { output ->
                pixels.forEachIndexed { index, pixel ->
                    output[index] = ((((pixel shr 16) and 0xff) / 255f) - mean[0]) / std[0]
                    output[plane + index] = ((((pixel shr 8) and 0xff) / 255f) - mean[1]) / std[1]
                    output[2 * plane + index] = (((pixel and 0xff) / 255f) - mean[2]) / std[2]
                }
            }
        } finally {
            resized.recycle()
        }
    }
}
