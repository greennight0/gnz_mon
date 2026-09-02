package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ConservationStatus
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
        customApiKey: String? = null
    ): Result<SpeciesInfo> = withContext(Dispatchers.IO) {
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
                return@withContext Result.failure(
                    IllegalStateException("Gemini API Key is not configured. Using offline nature database.")
                )
            }

            // Downscale bitmap if too large to ensure fast transmission
            val scaledBitmap = scaleBitmapToMax(bitmap, 1024)
            val base64Image = bitmapToBase64(scaledBitmap)

            val prompt = """
                You are GNZ MON (Mysteria of Natural) expert botanist and zoologist AI.
                Analyze the plant, animal, bird, insect, fungi, or organism in this image.
                Provide identification in strictly valid JSON format with the following fields:
                {
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
                  "toxicityOrCareVi": "Cảnh báo độc tính, lưu ý an toàn hoặc mẹo chăm sóc bằng tiếng Việt",
                  "confidenceScore": 95
                }
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
                }
                put("generationConfig", genConfig)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    Exception("API call failed (Code ${response.code}): ${responseBody ?: response.message}")
                )
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanedJsonText = cleanJsonString(rawText)
            val parsedSpecies = parseSpeciesJson(cleanedJsonText)

            Result.success(parsedSpecies)
        } catch (e: Exception) {
            Log.e("GeminiVisionClient", "Error analyzing image: ${e.message}", e)
            Result.failure(e)
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

    private fun parseSpeciesJson(jsonString: String): SpeciesInfo {
        val json = JSONObject(jsonString)
        val validCategory = try {
            SpeciesCategory.valueOf(json.optString("category", "PLANT").uppercase()).name
        } catch (e: Exception) {
            SpeciesCategory.PLANT.name
        }

        val statusCode = json.optString("conservationStatusCode", "LC").uppercase()
        val validStatus = try {
            ConservationStatus.valueOf(statusCode).code
        } catch (e: Exception) {
            "LC"
        }

        return SpeciesInfo(
            id = UUID.randomUUID().toString(),
            commonNameEn = json.optString("commonNameEn", "Unknown Organism"),
            commonNameVi = json.optString("commonNameVi", "Sinh vật chưa xác định"),
            scientificName = json.optString("scientificName", "Incertae sedis"),
            category = validCategory,
            kingdom = json.optString("kingdom", "Nature"),
            family = json.optString("family", "Unknown Family"),
            orderName = json.optString("orderName", ""),
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
            confidenceScore = json.optInt("confidenceScore", 92),
            identifiedAtMillis = System.currentTimeMillis()
        )
    }

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
