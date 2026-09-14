package com.example.data.api

import android.graphics.Bitmap
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiVisionClientTest {
    private val client = GeminiVisionClient()

    @Test
    fun `computer mouse is not converted to SpeciesInfo`() {
        assertNotOrganism("computer mouse", 98)
    }

    @Test
    fun `toy is not converted to SpeciesInfo`() {
        assertNotOrganism("plastic toy dinosaur", 94)
    }

    @Test
    fun `empty image is not converted to SpeciesInfo`() {
        assertNotOrganism("empty scene", 91)
    }

    @Test
    fun `valid organism creates organism result`() {
        val result = client.parseSpeciesJson(validOrganismJson())

        assertTrue(result is RecognitionResult.Organism)
        assertEquals("Animalia", (result as RecognitionResult.Organism).species.kingdom)
        assertEquals("BIRD", result.species.category)
    }

    @Test
    fun `organism missing taxonomy is rejected instead of defaulting to plant`() {
        val result = client.parseSpeciesJson(validOrganismJson().replace("\"family\":\"Passeridae\",", ""))

        assertFailure(result, ScanFailureReason.MissingRequiredField("family"))
    }

    @Test
    fun `kingdom inconsistent taxonomy is rejected`() {
        val result = client.parseSpeciesJson(validOrganismJson().replace("Animalia", "Plantae"))

        assertFailure(result, ScanFailureReason.InconsistentTaxonomy("BIRD", "Plantae"))
    }

    @Test
    fun `image request is sent to configured backend without api key`() = runBlocking {
        var capturedRequest: Request? = null
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            capturedRequest = chain.request()
            response(chain.request(), 200, apiResponse(listOf(validOrganismJson())))
        }.build()
        val backendClient = GeminiVisionClient("https://backend.example/v1/identify", httpClient)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val result = backendClient.identifyFloraOrFauna(bitmap)

        assertTrue(result is RecognitionResult.Organism)
        assertEquals("https://backend.example/v1/identify", capturedRequest?.url.toString())
        assertEquals(null, capturedRequest?.url?.query)
        val payload = capturedRequest?.body?.let { body ->
            okio.Buffer().also(body::writeTo).readUtf8()
        }.orEmpty()
        assertTrue(payload.contains("inlineData"))
        assertTrue(payload.contains("image/jpeg"))
    }

    @Test fun `authentication error from backend is retained`() = runBlocking {
        assertBackendFailure(401)
    }

    @Test fun `server error from backend is retained`() = runBlocking {
        assertBackendFailure(503)
    }

    @Test fun `http status is retained`() = assertFailure(
        client.parseApiResponse(503, false, "down"), ScanFailureReason.Http(503)
    )

    @Test fun `empty response has its own category`() = assertFailure(
        client.parseApiResponse(200, true, ""), ScanFailureReason.EmptyResponse
    )

    @Test fun `invalid json has its own category`() = assertFailure(
        client.parseApiResponse(200, true, "not json"), ScanFailureReason.MalformedJson
    )

    @Test fun `malformed response log contains safe diagnostics only`() {
        ShadowLog.clear()
        val sensitiveBody = "not-json-API_KEY_secret-imageBase64"

        assertFailure(client.parseApiResponse(422, true, sensitiveBody), ScanFailureReason.MalformedJson)

        val log = ShadowLog.getLogsForTag("GeminiVisionClient").joinToString { it.msg }
        assertTrue(log.contains("httpStatus=422"))
        assertTrue(log.contains("jsonErrorType=JSONException"))
        assertTrue(log.contains("missingRequiredField=none"))
        assertTrue(!log.contains(sensitiveBody))
        assertTrue(!log.contains("API_KEY_secret"))
    }

    @Test fun `missing required response field is logged by name without response data`() {
        ShadowLog.clear()
        val json = validOrganismJson().replace("\"family\":\"Passeridae\",", "")

        assertFailure(client.parseSpeciesJson(json), ScanFailureReason.MissingRequiredField("family"))

        val log = ShadowLog.getLogsForTag("GeminiVisionClient").joinToString { it.msg }
        assertTrue(log.contains("httpStatus=200"))
        assertTrue(log.contains("missingRequiredField=family"))
        assertTrue(!log.contains("Passer domesticus"))
    }

    @Test fun `timeout has its own category`() = assertFailure(
        client.mapTransportFailure(SocketTimeoutException()), ScanFailureReason.Timeout
    )

    @Test fun `dns failure has its own category`() = assertFailure(
        client.mapTransportFailure(UnknownHostException()), ScanFailureReason.Dns
    )

    @Test fun `tls failure has its own category`() = assertFailure(
        client.mapTransportFailure(SSLHandshakeException("certificate")), ScanFailureReason.Tls
    )

    @Test fun `connection refused has its own category`() = assertFailure(
        client.mapTransportFailure(ConnectException("refused")), ScanFailureReason.ConnectionRefused
    )

    @Test fun `generic io is not reported as offline while a network is available`() = assertFailure(
        client.mapTransportFailure(IOException()), ScanFailureReason.Io
    )

    @Test fun `generic io is reported as offline only after connectivity confirmation`() = assertFailure(
        GeminiVisionClient(isNetworkAvailable = { false }).mapTransportFailure(IOException()),
        ScanFailureReason.Network
    )

    @Test fun `low organism confidence has its own category`() = assertFailure(
        client.parseSpeciesJson(validOrganismJson().replace("\"confidenceScore\":96", "\"confidenceScore\":60")),
        ScanFailureReason.LowConfidence(60)
    )

    @Test fun `response schema describes both branches enum and confidence limits`() {
        val schema = client.createGenerationConfig().getJSONObject("responseSchema")
        val properties = schema.getJSONObject("properties")
        assertEquals(0, properties.getJSONObject("confidenceScore").getInt("minimum"))
        assertEquals(100, properties.getJSONObject("confidenceScore").getInt("maximum"))
        assertEquals(7, properties.getJSONObject("category").getJSONArray("enum").length())
        assertEquals(2, schema.getJSONArray("anyOf").length())
    }

    @Test fun `empty candidates is diagnosed`() = assertFailure(
        client.parseApiResponse(200, true, """{"candidates":[]}"""),
        ScanFailureReason.NoCandidates
    )

    @Test fun `all content parts are joined before parsing`() {
        val json = validOrganismJson()
        val midpoint = json.length / 2
        val response = apiResponse(listOf(json.substring(0, midpoint), json.substring(midpoint)))
        assertTrue(client.parseApiResponse(200, true, response) is RecognitionResult.Organism)
    }

    @Test fun `max tokens with cut json is diagnosed as truncation`() = assertFailure(
        client.parseApiResponse(200, true, apiResponse(listOf("{\"isLivingOrganism\":true"), "MAX_TOKENS")),
        ScanFailureReason.TruncatedResponse
    )

    @Test fun `every required organism field is diagnosed by name`() {
        val required = client.createGenerationConfig().getJSONObject("responseSchema")
            .getJSONArray("anyOf").getJSONObject(1).getJSONArray("required")
        for (index in 0 until required.length()) {
            val field = required.getString(index)
            val fixture = JSONObject(validOrganismJson()).apply { remove(field) }.toString()
            assertFailure(client.parseSpeciesJson(fixture), ScanFailureReason.MissingRequiredField(field))
        }
    }

    @Test fun `unknown category has diagnostic`() = assertFailure(
        client.parseSpeciesJson(validOrganismJson().replace("\"BIRD\"", "\"ROBOT\"")),
        ScanFailureReason.UnknownCategory("ROBOT")
    )

    @Test fun `safety finish reason is diagnosed without parsing content`() = assertFailure(
        client.parseApiResponse(200, true, apiResponse(listOf("secret response"), "SAFETY")),
        ScanFailureReason.SafetyBlocked
    )

    private fun assertFailure(result: RecognitionResult, expected: ScanFailureReason) {
        assertTrue(result is RecognitionResult.Failure)
        assertEquals(expected, ((result as RecognitionResult.Failure).error as ScanException).reason)
    }

    private suspend fun assertBackendFailure(statusCode: Int) {
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), statusCode, "backend error")
        }.build()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val result = GeminiVisionClient("https://backend.example/v1/identify", httpClient)
            .identifyFloraOrFauna(bitmap)
        assertFailure(result, ScanFailureReason.Http(statusCode))
    }

    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(body)
        .body(body.toResponseBody())
        .build()

    private fun assertNotOrganism(label: String, confidence: Int) {
        val result = client.parseSpeciesJson(
            """{"isLivingOrganism":false,"objectLabel":"$label","confidenceScore":$confidence}"""
        )
        assertEquals(RecognitionResult.NotOrganism(label, confidence), result)
    }

    private fun apiResponse(parts: List<String>, finishReason: String = "STOP"): String =
        JSONObject().put("candidates", org.json.JSONArray().put(
            JSONObject()
                .put("finishReason", finishReason)
                .put("content", JSONObject().put("parts", org.json.JSONArray().apply {
                    parts.forEach { put(JSONObject().put("text", it)) }
                }))
        )).toString()

    private fun validOrganismJson() = """
        {
          "isLivingOrganism":true,
          "objectLabel":"",
          "confidenceScore":96,
          "commonNameEn":"House sparrow",
          "commonNameVi":"Chim sẻ nhà",
          "scientificName":"Passer domesticus",
          "category":"BIRD",
          "kingdom":"Animalia",
          "family":"Passeridae",
          "orderName":"Passeriformes",
          "descriptionEn":"A small bird",
          "descriptionVi":"Một loài chim nhỏ",
          "habitatEn":"Urban and rural areas",
          "habitatVi":"Khu vực đô thị và nông thôn",
          "distributionEn":"Worldwide",
          "distributionVi":"Toàn cầu",
          "ecologicalRoleEn":"Seed dispersal",
          "ecologicalRoleVi":"Phát tán hạt",
          "mysteriaFactEn":"Highly social",
          "mysteriaFactVi":"Có tính xã hội cao",
          "conservationStatusCode":"LC",
          "toxicityOrCareEn":"Observe from a distance",
          "toxicityOrCareVi":"Quan sát từ xa"
        }
    """.trimIndent()
}
