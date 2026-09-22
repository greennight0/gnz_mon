package com.example.data.classifier

import android.graphics.Bitmap
import com.example.data.model.RecognitionResult
import com.example.data.repository.SpeciesCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommonPlantClassifierTest {
    private fun prediction(label: String, score: Float = .9f) = listOf(CommonPrediction(label, score), CommonPrediction("other", .05f))
    @Test fun bananaTakesPriorityAndNeverCallsPlantNet() = runBlocking {
        val image = Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        val classifier = CommonPlantClassifier(CommonRunner { prediction("banana") }, SpeciesClassifier { _,_ -> error("Must not call PlantNet") })
        assertEquals(RecognitionResult.CommonPlant("banana", "Chuối", "Banana", .9f), classifier.classifyPair(image,image) {})
        image.recycle()
    }
    @Test fun conflictingViewsAndOutOfScopeAreNotSpecies() {
        assertTrue(CommonPlantCatalog.resolve(prediction("banana"),prediction("orange")) is RecognitionResult.Uncertain)
        assertTrue(CommonPlantCatalog.resolve(prediction("tabby"),prediction("tabby")) is RecognitionResult.NotOrganism)
        assertTrue(CommonPlantCatalog.resolve(prediction("coffee mug"),prediction("coffee mug")) is RecognitionResult.NotOrganism)
        assertNull(CommonPlantCatalog.resolve(prediction("banana",.7f),prediction("banana")))
        assertNull(CommonPlantCatalog.resolve(listOf(CommonPrediction("banana",.76f),CommonPrediction("orange",.60f)),prediction("banana")))
    }
    @Test fun plantNetMustAgreeAcrossBothViews() = runBlocking {
        val image = Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        var calls = 0
        val classifier = CommonPlantClassifier(CommonRunner { prediction("banana",.5f) }, SpeciesClassifier { _,_ ->
            RecognitionResult.Organism(SpeciesCatalog.fromScientificName(if (++calls % 2 == 1) "Plant A" else "Plant B", .95f)) })
        assertTrue(classifier.classifyPair(image,image) {} is RecognitionResult.Uncertain)
        image.recycle()
    }
    @Test fun runtimeErrorsPropagateInsteadOfFallingBackToFalseSpecies() = runBlocking {
        val image = Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        try {
            CommonPlantClassifier(CommonRunner { error("model failed") }, SpeciesClassifier { _,_ -> error("must not run") })
                .classifyPair(image,image) {}
            fail("Expected model error")
        } catch (e: IllegalStateException) { assertEquals("model failed",e.message) }
        finally { image.recycle() }
    }
    @Test fun everyCommonNameExistsInPackagedLabels() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val labels = context.assets.open("models/common_plant_labels.txt").bufferedReader().use { it.readLines() }
        assertTrue(labels.containsAll(CommonPlantCatalog.names.keys))
    }
}
