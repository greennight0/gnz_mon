package com.example.data.api

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.RecognitionResult
import com.example.data.repository.SpeciesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

        assertTrue(result is RecognitionResult.Failure)
    }

    @Test
    fun `kingdom inconsistent taxonomy is rejected`() {
        val result = client.parseSpeciesJson(validOrganismJson().replace("Animalia", "Plantae"))

        assertTrue(result is RecognitionResult.Failure)
    }

    @Test
    fun `missing api key returns failure and does not persist a random species`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppDatabase.getDatabase(context).speciesDao().clearAll()
        val repository = SpeciesRepository(context)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val result = repository.identifyImage(bitmap, " ")

        assertTrue(result is RecognitionResult.Failure)
        assertTrue(repository.discoveredSpeciesFlow.first().isEmpty())
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
