package com.example.data.repository

import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeciesRepositoryTest {
    @Test fun `uncertain candidates are not saved but confirmed species are`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uncertain = com.example.data.model.RecognitionResult.Uncertain(listOf(
            com.example.data.model.RecognitionResult.Candidate("Example plant", .67f)))
        var response: com.example.data.model.RecognitionResult = uncertain
        val repository = SpeciesRepository(context) {
            com.example.data.classifier.SpeciesClassifier { _, _ -> response }
        }
        repository.clearJournal()
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            assertEquals(uncertain, repository.identifyImage(bitmap))
            assertEquals(0, repository.discoveredSpeciesFlow.first().size)
            response = com.example.data.model.RecognitionResult.CommonPlant("banana", "Chuối", "Banana", .9f)
            repository.identifyPair(bitmap, bitmap)
            assertEquals(0, repository.discoveredSpeciesFlow.first().size)
            val species = SpeciesCatalog.fromScientificName("Example plant", .9f)
            response = com.example.data.model.RecognitionResult.Organism(species)
            repository.identifyImage(bitmap)
            assertEquals(listOf(species), repository.discoveredSpeciesFlow.first())
        } finally {
            repository.clearJournal()
            bitmap.recycle()
        }
    }

    @Test
    fun `public social links exclude private development links and retain public networks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val linkIds = SpeciesRepository(context).getSocialAndEcosystemLinks().map { it.id }

        assertFalse(linkIds.contains("github"))
        assertEquals(
            listOf(
                "x_twitter",
                "truth_social",
                "reddit",
                "discord",
                "snapchat",
                "locket",
                "linkedin",
                "mastodon",
                "bluesky",
                "gumroad"
            ),
            linkIds
        )
    }
}
