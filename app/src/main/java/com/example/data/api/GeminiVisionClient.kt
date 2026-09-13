package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiVisionClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun identifyFloraOrFauna(
        bitmap: Bitmap,
        customApiKey: String? = null,
        onPhase: (ScanTransportPhase) -> Unit = {}
    ): RecognitionResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = if (!customApiKey.isNullOrBlank()) {
                customApiKey
            } else {
                try {
                    BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
                } catch (e: Exception) {
                    ""
                }
            }

            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext failure(ScanFailureReason.MissingApiKey)
            }

            // Downscale bitmap if too large to ensure fast transmission
            val scaledBitmap = scaleBitmapToMax(bitmap, 1024)
            onPhase(ScanTransportPhase.ENCODING)
            val base64Image = bitmapToBase64(scaledBitmap)

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

                val genConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("topP", 0.95)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
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
            Log.e("GeminiVisionClient", "Error analyzing image: ${e.message}", e)
            failure(ScanFailureReason.InvalidResponse, e)
        }
    }

    internal fun parseApiResponse(code: Int, successful: Boolean, body: String?): RecognitionResult {
        if (!successful) return failure(ScanFailureReason.Http(code))
        if (body.isNullOrBlank()) return failure(ScanFailureReason.EmptyResponse)
        val rootJson = try {
            JSONObject(body)
        } catch (error: Exception) {
            return failure(ScanFailureReason.InvalidResponse, error)
        }
        val rawText = rootJson.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text").orEmpty()
        if (rawText.isBlank()) return failure(ScanFailureReason.EmptyResponse)
        return parseSpeciesJson(cleanJsonString(rawText))
    }

    internal fun mapTransportFailure(error: IOException): RecognitionResult.Failure =
        failure(if (error is SocketTimeoutException) ScanFailureReason.Timeout else ScanFailureReason.Network, error)

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

    internal fun parseSpeciesJson(jsonString: String): RecognitionResult {
        return try {
            parseRecognitionJson(JSONObject(jsonString))
        } catch (error: Exception) {
            failure(ScanFailureReason.InvalidResponse, error)
        }
    }

    private fun parseRecognitionJson(json: JSONObject): RecognitionResult {
        require(json.has("isLivingOrganism")) { "Missing isLivingOrganism" }
        val confidence = json.optInt("confidenceScore", -1)
        require(confidence in 0..100) { "Invalid confidenceScore" }
        if (!json.getBoolean("isLivingOrganism")) {
            val label = json.optString("objectLabel").trim()
            require(label.isNotEmpty()) { "Missing objectLabel for non-organism" }
            return RecognitionResult.NotOrganism(label, confidence)
        }
        if (confidence < MINIMUM_CONFIDENCE) {
            return failure(ScanFailureReason.LowConfidence(confidence))
        }

        fun required(name: String): String = json.optString(name).trim().also {
            require(it.isNotEmpty()) { "Missing taxonomy field: $name" }
        }
        val category = try {
            SpeciesCategory.valueOf(required("category").uppercase())
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid species category", error)
        }
        val kingdom = required("kingdom")
        val expectedKingdom = when (category) {
            SpeciesCategory.PLANT -> "Plantae"
            SpeciesCategory.FUNGI -> "Fungi"
            SpeciesCategory.ANIMAL, SpeciesCategory.BIRD, SpeciesCategory.INSECT,
            SpeciesCategory.AQUATIC -> "Animalia"
            SpeciesCategory.OTHER -> null
        }
        require(expectedKingdom == null || kingdom.equals(expectedKingdom, ignoreCase = true)) {
            "Category $category is inconsistent with kingdom $kingdom"
        }

        val statusCode = json.optString("conservationStatusCode", "LC").uppercase()
        val validStatus = try {
            ConservationStatus.valueOf(statusCode).code
        } catch (e: Exception) {
            "LC"
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
            descriptionEn = json.optString("descriptionEn", "Natural species captured with GNZ MON scanner."),
            descriptionVi = json.optString("descriptionVi", "Loài sinh vật tự nhiên được ghi nhận qua máy quét GNZ MON."),
            habitatEn = json.optString("habitatEn", "Tropical and temperate natural environments."),
            habitatVi = json.optString("habitatVi", "Môi trường tự nhiên nhiệt đới và ôn đới."),
            distributionEn = json.optString("distributionEn", "Worldwide / Southeast Asia."),
            distributionVi = json.optString("distributionVi", "Toàn cầu / Khu vực Đông Nam Á & Việt Nam."),
            ecologicalRoleEn = json.optString("ecologicalRoleEn", "Contributes to ecosystem biodiversity."),
            ecologicalRoleVi = json.optString("ecologicalRoleVi", "Đóng góp duy trì cân bằng và đa dạng sinh thái."),
            mysteriaFactEn = json.optString("mysteriaFactEn", "Possesses unique morphological adaptations for survival in the wild."),
            mysteriaFactVi = json.optString("mysteriaFactVi", "Sở hữu những bí ẩn tiến hóa và khả năng thích nghi tuyệt diệu."),
            conservationStatusCode = validStatus,
            toxicityOrCareEn = json.optString("toxicityOrCareEn", "Harmless in natural habitat."),
            toxicityOrCareVi = json.optString("toxicityOrCareVi", "Lành tính trong môi trường sống tự nhiên."),
            confidenceScore = confidence,
            identifiedAtMillis = System.currentTimeMillis()
        )
        return RecognitionResult.Organism(species)
    }

    private companion object {
        const val MINIMUM_CONFIDENCE = 70
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
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
