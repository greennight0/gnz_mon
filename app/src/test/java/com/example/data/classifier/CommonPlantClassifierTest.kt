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
        assertTrue(CommonPlantCatalog.resolve(prediction("banana",.7f),prediction("banana")) is RecognitionResult.Uncertain)
        assertTrue(CommonPlantCatalog.resolve(listOf(CommonPrediction("banana",.76f),CommonPrediction("orange",.60f)),prediction("banana")) is RecognitionResult.Uncertain)
    }
    @Test fun plantNetMustAgreeAcrossBothViews() = runBlocking {
        val image = Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        var calls = 0
        val classifier = CommonPlantClassifier(CommonRunner { prediction("daisy",.5f) }, SpeciesClassifier { _,_ ->
            RecognitionResult.Organism(SpeciesCatalog.fromScientificName(if (++calls % 2 == 1) "Plant A" else "Plant B", .95f)) })
        assertTrue(classifier.classifyPair(image,image) {} is RecognitionResult.Uncertain)
        assertEquals(2, calls)
        image.recycle()
    }
    @Test fun ambiguousProduceNeverBecomesAConfidentScientificSpecies() = runBlocking {
        val image = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val classifier = CommonPlantClassifier(CommonRunner { prediction("banana", .5f) },
            SpeciesClassifier { _, _ -> error("Ambiguous fruit must not reach PlantNet") })
        val result = classifier.classifyPair(image, image) {} as RecognitionResult.Uncertain
        assertTrue(result.produceGuidance)
        assertTrue(result.candidates.isEmpty())
        image.recycle()
    }

    @Test fun calibratedMeanUsesBothViewsButCannotHideDisagreement() {
        val policy = CommonPlantPolicy(thresholds = mapOf("banana" to
            CommonThresholds(.8f, .6f, .2f, .1f, .3f)))
        val result = policy.decide(prediction("banana", .7f), prediction("banana", .95f))
        assertEquals(.825f, (result.result as RecognitionResult.CommonPlant).modelScore, .0001f)
        val conflict = policy.decide(prediction("banana"), prediction("orange"))
        assertTrue(conflict.result is RecognitionResult.Uncertain)
        assertEquals("view_disagreement", conflict.reason)
    }

    @Test fun citrusCandidatesRemainDistinctAndDisabledClassesCannotConfirm() {
        val classes = listOf("lime", "lemon", "kumquat").associateWith {
            CommonThresholds(suggestionMinimum = .1f, confirmationEnabled = false)
        }
        val policy = CommonPlantPolicy(ProduceCatalog.groups, classes, specialized = true)
        val scores = listOf(CommonPrediction("lime", .55f), CommonPrediction("lemon", .25f),
            CommonPrediction("kumquat", .15f))
        val result = policy.decide(scores, scores).result as RecognitionResult.Uncertain
        assertEquals(listOf("lime", "lemon", "kumquat"), result.commonCandidates.map { it.groupCode })
        assertEquals(listOf("Chanh xanh", "Chanh vàng", "Quất / Tắc"), result.commonCandidates.map { it.nameVi })
        assertTrue(result.candidates.isEmpty())
        assertEquals(20, ProduceCatalog.groups.size)
        assertTrue(policy.decide(prediction("lime", .99f), prediction("lime", .99f)).result is RecognitionResult.Uncertain)
    }

    @Test fun unsupportedGroupsAreNeverInferredFromTheirCatalogName() {
        assertFalse(CommonPlantPolicy().groups.containsKey("lime"))
        assertFalse(CommonPlantPolicy().groups.containsKey("kumquat"))
        val result = CommonPlantPolicy().decide(prediction("banana", .5f), prediction("banana", .5f))
        assertTrue((result.result as RecognitionResult.Uncertain).commonCandidates.isEmpty())
    }

    @Test fun invalidOutputFromEitherViewIsRejected() {
        val invalid = listOf(emptyList(), listOf(CommonPrediction("banana", Float.NaN), CommonPrediction("other", .1f)),
            listOf(CommonPrediction("banana", .8f), CommonPrediction("banana", .1f)),
            listOf(CommonPrediction("banana", 1.1f), CommonPrediction("other", .1f)))
        invalid.forEach { values ->
            assertThrows(IllegalArgumentException::class.java) { CommonPlantCatalog.resolve(values, prediction("banana")) }
            assertThrows(IllegalArgumentException::class.java) { CommonPlantCatalog.resolve(prediction("banana"), values) }
        }
    }

    @Test fun importedImageKeepsSpecimenAtEdgesAndDoesNotRecycleCallerBitmap() = runBlocking {
        val image = Bitmap.createBitmap(60, 20, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GREEN)
        image.setPixel(0, 10, android.graphics.Color.RED)
        val classifier = CommonPlantClassifier(CommonRunner { view ->
            assertEquals(view.width, view.height)
            val pixels = IntArray(view.width * view.height)
            view.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
            assertTrue(pixels.any { it == android.graphics.Color.RED })
            prediction("banana")
        }, SpeciesClassifier { _, _ -> error("Unexpected fallback") })
        classifier.classify(image) {}
        assertFalse(image.isRecycled)
        image.recycle()
    }

    @Test fun packagedPolicyMatchesModelAndOnlyEnablesRealLabels() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val policy = CommonPlantPolicy.load(context)
        assertFalse(policy.specialized)
        assertEquals(CommonPlantCatalog.names.keys, policy.groups.keys)
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
