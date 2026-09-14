package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ConservationStatus
import com.example.data.model.RecognitionResult
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import com.example.data.model.ScanTransportPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiVisionClient internal constructor(
    private val backendEndpoint: String = BuildConfig.GNZ_MON_BACKEND_ENDPOINT,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val isNetworkAvailable: () -> Boolean = { true }
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun identifyFloraOrFauna(
        bitmap: Bitmap,
        onPhase: (ScanTransportPhase) -> Unit = {}
    ): RecognitionResult = withContext(Dispatchers.IO) {
        try {
            // Downscale bitmap if too large to ensure fast transmission
            onPhase(ScanTransportPhase.ENCODING)
            val scaledBitmap = scaleBitmapToMax(bitmap, 1024)
            val base64Image = try {
                bitmapToBase64(scaledBitmap)
            } finally {
                if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            }

            val prompt = """
                You are GNZ MON (Mysteria of Natural) expert botanist and zoologist AI.
                First decide whether the main subject is a real, living or once-living natural organism.
                Electronics, furniture, toys, empty scenes, inanimate objects, photographs, screens,
                drawings, statues, models, replicas and other simulations MUST NOT be inferred as species.
                Use a minimum confidence threshold of 70. If uncertain or below 70, set
                isLivingOrganism=false and describe the visible object/scene instead of inventing taxonomy.

                Return exactly one JSON object matching this fixed schema. No extra keys are allowed:
                {
                  "isLivingOrganism": true or false,
                  "objectLabel": "visible object/scene label; required when false, empty when true",
                  "confidenceScore": integer from 0 to 100,
                  "commonNameEn": "Common name in English",
                  "commonNameVi": "Tên gọi phổ biến trong tiếng Việt",
                  "scientificName": "Binomial Latin name",
                  "category": "PLANT or ANIMAL or BIRD or INSECT or FUNGI or AQUATIC or OTHER",
                  "kingdom": "Plantae or Animalia or Fungi",
                  "family": "Family name (Họ)",
                  "orderName": "Order name (Bộ)",
                  "descriptionEn": "Concise physical description in English",
                  "descriptionVi": "Mô tả hình dáng và đặc điểm nhận dạng bằng tiếng Việt",
                  "habitatEn": "Natural habitat in English",
                  "habitatVi": "Môi trường sống tự nhiên bằng tiếng Việt",
                  "distributionEn": "Geographical distribution in English",
                  "distributionVi": "Khu vực phân bố địa lý bằng tiếng Việt",
                  "ecologicalRoleEn": "Ecological niche and importance in English",
                  "ecologicalRoleVi": "Vai trò sinh thái trong tự nhiên bằng tiếng Việt",
                  "mysteriaFactEn": "Fascinating mystery, adaptation, or cool trivia in English",
                  "mysteriaFactVi": "Điều kỳ bí, sự thích nghi đặc biệt hoặc sự thật thú vị bằng tiếng Việt",
                  "conservationStatusCode": "LC or NT or VU or EN or CR or NE",
                  "toxicityOrCareEn": "Any toxicity, care advice, or safety notice in English",
                  "toxicityOrCareVi": "Cảnh báo độc tính, lưu ý an toàn hoặc mẹo chăm sóc bằng tiếng Việt"
                }
                When isLivingOrganism=false, taxonomy and organism-description fields must be empty strings.
                When isLivingOrganism=true, objectLabel must be empty and every taxonomy field
                (common names, scientificName, category, kingdom, family and orderName) is required.
                Return ONLY the raw JSON object, without markdown block backticks.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                put("generationConfig", createGenerationConfig())
            }

            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(backendEndpoint)
                .post(body)
                .build()

            onPhase(ScanTransportPhase.UPLOADING)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) onPhase(ScanTransportPhase.ANALYZING)
            parseApiResponse(response.code, response.isSuccessful, responseBody)
        } catch (e: SocketTimeoutException) {
            mapTransportFailure(e)
        } catch (e: IOException) {
            mapTransportFailure(e)
        } catch (e: Exception) {
            // Exceptions from HTTP/request parsing can embed the URL (and therefore the API key).
            Log.e(TAG, "Image analysis failed: errorType=${e.javaClass.simpleName}")
            failure(ScanFailureReason.Unexpected, e)
        }
    }

    internal fun parseApiResponse(code: Int, successful: Boolean, body: String?): RecognitionResult {
        if (!successful) {
            logResponseError(code, "HttpError")
            return failure(ScanFailureReason.Http(code))
        }
        if (body.isNullOrBlank()) {
            logResponseError(code, "EmptyResponse")
            return failure(ScanFailureReason.EmptyResponse)
        }
        val rootJson = try {
            JSONObject(body)
        } catch (error: Exception) {
            logResponseError(code, error.javaClass.simpleName, throwable = error)
            return failure(ScanFailureReason.MalformedJson, error)
        }
        val blockReason = rootJson.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        if (blockReason.isNotBlank() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
            logResponseError(code, "SafetyBlocked")
            return failure(ScanFailureReason.SafetyBlocked)
        }
        val candidates = rootJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            logResponseError(code, "NoCandidates")
            return failure(ScanFailureReason.NoCandidates)
        }
        val candidate = candidates.optJSONObject(0) ?: return failure(ScanFailureReason.MissingContent)
        val safetyRatings = candidate.optJSONArray("safetyRatings")
        if (safetyRatings != null && (0 until safetyRatings.length()).any {
                safetyRatings.optJSONObject(it)?.optBoolean("blocked") == true
            }) {
            return failure(ScanFailureReason.SafetyBlocked)
        }
        when (candidate.optString("finishReason")) {
            "", "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY" ->
                return failure(ScanFailureReason.SafetyBlocked)
            "MAX_TOKENS", "RECITATION", "MALFORMED_FUNCTION_CALL", "UNEXPECTED_TOOL_CALL" ->
                return failure(ScanFailureReason.TruncatedResponse)
            else -> return failure(ScanFailureReason.InvalidResponse)
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) return failure(ScanFailureReason.MissingContent)
        val rawText = buildString {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index)
                if (part == null || !part.has("text") || part.optString("text").isBlank()) {
                    logResponseError(code, "MissingContent", "candidates[0].content.parts[$index].text")
                    return failure(ScanFailureReason.MissingContent)
                }
                append(part.getString("text"))
            }
        }
        return parseSpeciesJson(cleanJsonString(rawText), code)
    }

    internal fun mapTransportFailure(error: IOException): RecognitionResult.Failure = failure(
        when (error) {
            is UnknownHostException -> ScanFailureReason.Dns
            is SSLException -> ScanFailureReason.Tls
            is ConnectException -> ScanFailureReason.ConnectionRefused
            is SocketTimeoutException -> ScanFailureReason.Timeout
            else -> if (!isNetworkAvailable()) ScanFailureReason.Network else ScanFailureReason.Io
        },
        error
    )

    /** Kept separate from request construction so the wire contract can be fixture-tested. */
    internal fun createGenerationConfig(): JSONObject = JSONObject().apply {
        put("temperature", 0.2)
        put("topP", 0.95)
        put("responseMimeType", "application/json")
        put("responseSchema", recognitionResponseSchema())
    }

    private fun recognitionResponseSchema(): JSONObject {
        fun scalar(type: String, enum: List<String>? = null) = JSONObject().apply {
            put("type", type)
            enum?.let { values -> put("enum", JSONArray(values)) }
        }
        val stringFields = listOf(
            "objectLabel", "commonNameEn", "commonNameVi", "scientificName", "kingdom",
            "family", "orderName", "descriptionEn", "descriptionVi", "habitatEn", "habitatVi",
            "distributionEn", "distributionVi", "ecologicalRoleEn", "ecologicalRoleVi",
            "mysteriaFactEn", "mysteriaFactVi", "toxicityOrCareEn", "toxicityOrCareVi"
        )
        val properties = JSONObject().apply {
            put("isLivingOrganism", scalar("boolean"))
            put("confidenceScore", scalar("integer").apply {
                put("minimum", 0)
                put("maximum", 100)
            })
            stringFields.forEach { put(it, scalar("string")) }
            put("category", scalar("string", SpeciesCategory.entries.map { it.name }))
            put("conservationStatusCode", scalar("string", listOf("LC", "NT", "VU", "EN", "CR", "NE")))
        }
        val organismRequired = listOf(
            "isLivingOrganism", "objectLabel", "confidenceScore", "commonNameEn", "commonNameVi",
            "scientificName", "category", "kingdom", "family", "orderName", "descriptionEn",
            "descriptionVi", "habitatEn", "habitatVi", "distributionEn", "distributionVi",
            "ecologicalRoleEn", "ecologicalRoleVi", "mysteriaFactEn", "mysteriaFactVi",
            "conservationStatusCode", "toxicityOrCareEn", "toxicityOrCareVi"
        )
        fun branch(required: List<String>) = JSONObject().apply {
            put("required", JSONArray(required))
        }
        return JSONObject().apply {
            put("type", "object")
            put("properties", properties)
            put("anyOf", JSONArray().apply {
                put(branch(listOf("isLivingOrganism", "objectLabel", "confidenceScore")))
                put(branch(organismRequired))
            })
        }
    }

    private fun cleanJsonString(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        }
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }

    internal fun parseSpeciesJson(jsonString: String, httpStatus: Int = 200): RecognitionResult {
        return try {
            parseRecognitionJson(JSONObject(jsonString))
        } catch (error: Exception) {
            val missingField = (error as? MissingRequiredFieldException)?.field
            logResponseError(httpStatus, error.javaClass.simpleName, missingField, error)
            val reason = when (error) {
                is MissingRequiredFieldException -> ScanFailureReason.MissingRequiredField(error.field)
                is UnknownCategoryException -> ScanFailureReason.UnknownCategory(error.category)
                is InconsistentTaxonomyException ->
                    ScanFailureReason.InconsistentTaxonomy(error.category, error.kingdom)
                is org.json.JSONException -> if (looksTruncated(jsonString)) {
                    ScanFailureReason.TruncatedResponse
                } else {
                    ScanFailureReason.MalformedJson
                }
                else -> ScanFailureReason.InvalidResponse
            }
            failure(reason, error)
        }
    }

    private fun looksTruncated(text: String): Boolean {
        val clean = cleanJsonString(text)
        return clean.startsWith("{") && !clean.endsWith("}")
    }

    private fun parseRecognitionJson(json: JSONObject): RecognitionResult {
        if (!json.has("isLivingOrganism")) throw MissingRequiredFieldException("isLivingOrganism")
        if (!json.has("objectLabel")) throw MissingRequiredFieldException("objectLabel")
        if (!json.has("confidenceScore")) throw MissingRequiredFieldException("confidenceScore")
        val confidence = json.optInt("confidenceScore", -1)
        require(confidence in 0..100) { "Invalid confidenceScore" }
        if (!json.getBoolean("isLivingOrganism")) {
            val label = json.optString("objectLabel").trim()
            if (label.isEmpty()) throw MissingRequiredFieldException("objectLabel")
            return RecognitionResult.NotOrganism(label, confidence)
        }
        if (confidence < MINIMUM_CONFIDENCE) {
            return failure(ScanFailureReason.LowConfidence(confidence))
        }

        fun required(name: String): String = json.optString(name).trim().also {
            if (it.isEmpty()) throw MissingRequiredFieldException(name)
        }
        val categoryValue = required("category")
        val category = try {
            SpeciesCategory.valueOf(categoryValue.uppercase())
        } catch (error: IllegalArgumentException) {
            throw UnknownCategoryException(categoryValue)
        }
        val kingdom = required("kingdom")
        val expectedKingdom = when (category) {
            SpeciesCategory.PLANT -> "Plantae"
            SpeciesCategory.FUNGI -> "Fungi"
            SpeciesCategory.ANIMAL, SpeciesCategory.BIRD, SpeciesCategory.INSECT,
            SpeciesCategory.AQUATIC -> "Animalia"
            SpeciesCategory.OTHER -> null
        }
        if (expectedKingdom != null && !kingdom.equals(expectedKingdom, ignoreCase = true)) {
            throw InconsistentTaxonomyException(category.name, kingdom)
        }

        val statusCode = required("conservationStatusCode").uppercase()
        val validStatus = try {
            ConservationStatus.valueOf(statusCode).code
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid conservation status", e)
        }

        val species = SpeciesInfo(
            id = UUID.randomUUID().toString(),
            commonNameEn = required("commonNameEn"),
            commonNameVi = required("commonNameVi"),
            scientificName = required("scientificName"),
            category = category.name,
            kingdom = kingdom,
            family = required("family"),
            orderName = required("orderName"),
            descriptionEn = required("descriptionEn"),
            descriptionVi = required("descriptionVi"),
            habitatEn = required("habitatEn"),
            habitatVi = required("habitatVi"),
            distributionEn = required("distributionEn"),
            distributionVi = required("distributionVi"),
            ecologicalRoleEn = required("ecologicalRoleEn"),
            ecologicalRoleVi = required("ecologicalRoleVi"),
            mysteriaFactEn = required("mysteriaFactEn"),
            mysteriaFactVi = required("mysteriaFactVi"),
            conservationStatusCode = validStatus,
            toxicityOrCareEn = required("toxicityOrCareEn"),
            toxicityOrCareVi = required("toxicityOrCareVi"),
            confidenceScore = confidence,
            identifiedAtMillis = System.currentTimeMillis()
        )
        return RecognitionResult.Organism(species)
    }

    private companion object {
        const val MINIMUM_CONFIDENCE = 70
        const val TAG = "GeminiVisionClient"
    }

    private class MissingRequiredFieldException(val field: String) :
        IllegalArgumentException("Missing required response field: $field")

    private class UnknownCategoryException(val category: String) :
        IllegalArgumentException("Unknown species category")

    private class InconsistentTaxonomyException(val category: String, val kingdom: String) :
        IllegalArgumentException("Inconsistent taxonomy")

    private fun logResponseError(
        httpStatus: Int,
        jsonErrorType: String,
        missingRequiredField: String? = null,
        throwable: Throwable? = null
    ) {
        // Deliberately log metadata only: never the URL/API key, response body, prompt, or image data.
        val message = "Response analysis failed: httpStatus=$httpStatus, " +
            "jsonErrorType=$jsonErrorType, missingRequiredField=${missingRequiredField ?: "none"}"
        // Do not attach the throwable: JSON exception messages may quote response fragments.
        Log.e(TAG, message)
    }

    private fun failure(reason: ScanFailureReason, cause: Throwable? = null) =
        RecognitionResult.Failure(ScanException(reason, cause))

    private fun scaleBitmapToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Stream JPEG directly into Base64 characters. This avoids retaining a JPEG buffer plus
        // the copy made by ByteArrayOutputStream.toByteArray() alongside the Base64 payload.
        val encoded = StringBuilder()
        val characterSink = object : OutputStream() {
            override fun write(value: Int) { encoded.append((value and 0xff).toChar()) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                for (index in offset until offset + length) write(bytes[index].toInt())
            }
        }
        Base64OutputStream(characterSink, Base64.NO_WRAP).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) { "JPEG encoding failed" }
        }
        return encoded.toString()
    }
}
