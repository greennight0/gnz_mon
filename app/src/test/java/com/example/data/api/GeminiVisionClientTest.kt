package com.example.data.api

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import com.example.data.repository.SpeciesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
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

        assertFailure(result, ScanFailureReason.InvalidResponse)
    }

    @Test
    fun `kingdom inconsistent taxonomy is rejected`() {
        val result = client.parseSpeciesJson(validOrganismJson().replace("Animalia", "Plantae"))

        assertFailure(result, ScanFailureReason.InvalidResponse)
    }

    @Test
    fun `missing api key returns failure and does not persist a random species`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppDatabase.getDatabase(context).speciesDao().clearAll()
        val repository = SpeciesRepository(context)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val result = repository.identifyImage(bitmap, " ")

        assertFailure(result, ScanFailureReason.MissingApiKey)
        assertTrue(repository.discoveredSpeciesFlow.first().isEmpty())
    }

    @Test fun `http status is retained`() = assertFailure(
        client.parseApiResponse(503, false, "down"), ScanFailureReason.Http(503)
    )

    @Test fun `empty response has its own category`() = assertFailure(
        client.parseApiResponse(200, true, ""), ScanFailureReason.EmptyResponse
    )

    @Test fun `invalid json has its own category`() = assertFailure(
        client.parseApiResponse(200, true, "not json"), ScanFailureReason.InvalidResponse
    )

    @Test fun `malformed response log contains safe diagnostics only`() {
        ShadowLog.clear()
        val sensitiveBody = "not-json-API_KEY_secret-imageBase64"

        assertFailure(client.parseApiResponse(422, true, sensitiveBody), ScanFailureReason.InvalidResponse)

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

        assertFailure(client.parseSpeciesJson(json), ScanFailureReason.InvalidResponse)

        val log = ShadowLog.getLogsForTag("GeminiVisionClient").joinToString { it.msg }
        assertTrue(log.contains("httpStatus=200"))
        assertTrue(log.contains("missingRequiredField=family"))
        assertTrue(!log.contains("Passer domesticus"))
    }

    @Test fun `timeout has its own category`() = assertFailure(
        client.mapTransportFailure(SocketTimeoutException()), ScanFailureReason.Timeout
    )

    @Test fun `network failure has its own category`() = assertFailure(
        client.mapTransportFailure(IOException()), ScanFailureReason.Network
    )

    @Test fun `low organism confidence has its own category`() = assertFailure(
        client.parseSpeciesJson(validOrganismJson().replace("\"confidenceScore\":96", "\"confidenceScore\":60")),
        ScanFailureReason.LowConfidence(60)
    )

    private fun assertFailure(result: RecognitionResult, expected: ScanFailureReason) {
        assertTrue(result is RecognitionResult.Failure)
        assertEquals(expected, ((result as RecognitionResult.Failure).error as ScanException).reason)
    }

    private fun assertNotOrganism(label: String, confidence: Int) {
        val result = client.parseSpeciesJson(
            """{"isLivingOrganism":false,"objectLabel":"$label","confidenceScore":$confidence}"""
        )
        assertEquals(RecognitionResult.NotOrganism(label, confidence), result)
    }

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
