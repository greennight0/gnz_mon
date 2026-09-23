package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppLanguage
import com.example.data.model.DetectorErrorType
import com.example.data.model.DetectorState
import com.example.data.model.DetectorStage
import com.example.data.model.ScanFailureReason
import com.example.data.model.ScanState
import com.example.ui.MainViewModel
import com.example.ui.ScanRequest
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.detectorStageCode
import com.example.ui.camera.DetectorStageException
import com.example.ui.camera.CameraAnalysisInitializationException
import com.example.data.classifier.LocalModelUnavailableException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanStateUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val rect = RectF(.2f, .2f, .8f, .8f)
    private val thumbnail = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test fun `common fruit card has no invented species or confidence badge`() {
        composeRule.setContent {
            ScannerOverlay(detectedSpecies = null, isAnalyzing = false,
                scanState = ScanState.Completed(7, rect,
                    com.example.data.model.RecognitionResult.CommonPlant("banana", "Chuối", "Banana", .99f)),
                language = AppLanguage.VIETNAMESE, onSpeciesClick = {}, onCaptureClick = {})
        }
        composeRule.onNodeWithText("Chuối").assertExists()
        composeRule.onNodeWithText("99%", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cucurbita", substring = true).assertDoesNotExist()
    }

    @Test fun `only selected target gets a visible badge and semantic box`() {
        composeRule.setContent {
            ScannerOverlay(detectedSpecies = null, isAnalyzing = false,
                language = AppLanguage.VIETNAMESE,
                trackedObjects = listOf(1,2,3).map {
                    com.example.data.model.TrackedBoundingBox(it, rect, "Target", .9f)
                }, selectedTrackId = 2, onSpeciesClick = {}, onCaptureClick = {})
        }
        composeRule.onNodeWithTag("box_header_tag_2").assertExists()
        composeRule.onNodeWithTag("box_header_tag_1").assertDoesNotExist()
        composeRule.onNodeWithTag("box_header_tag_3").assertDoesNotExist()
        composeRule.onNodeWithText("TRK", substring = true).assertDoesNotExist()
    }

    @Test fun `late recognition cannot attach to newly selected target`() = runBlocking {
        val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var started = false
        model.onObjectsTracked(listOf(1, 2).map {
            com.example.data.model.TrackedBoundingBox(it, rect, "Target", .9f)
        }, 16)
        model.selectTrack(1)
        model.identifyImage = { _, _ ->
            started = true
            gate.await()
            com.example.data.model.RecognitionResult.CommonPlant("banana", "Chuối", "Banana", .9f)
        }
        model.analyzeImage(ScanRequest(1, thumbnail, rect))
        kotlinx.coroutines.withTimeout(5_000) { while (!started) delay(10) }
        model.selectTrack(2)
        gate.complete(Unit)
        kotlinx.coroutines.withTimeout(5_000) { while (model.isAnalyzing.value) delay(10) }
        assertEquals(2, model.selectedTrackId.value)
        assertEquals(ScanState.Idle, model.scanState.value)
        assertEquals(null, model.detectedSpecies.value)
        assertTrue(model.boxSpeciesMap.value.isEmpty())
    }

    @Test fun `uncertain UI is localized and does not claim non organism`() {
        var language by androidx.compose.runtime.mutableStateOf(AppLanguage.VIETNAMESE)
        val result = com.example.data.model.RecognitionResult.Uncertain(listOf(
            com.example.data.model.RecognitionResult.Candidate("Example plant", .67f)))
        composeRule.setContent {
            ScannerOverlay(detectedSpecies = null, isAnalyzing = false,
                scanState = ScanState.Completed(7, rect, result), language = language,
                onSpeciesClick = {}, onCaptureClick = {})
        }
        composeRule.onNodeWithText("Chưa đủ chắc chắn để xác định loài").assertExists()
        composeRule.onNodeWithText("Example plant · 67.0%").assertExists()
        composeRule.onNodeWithTag("not_organism_message").assertDoesNotExist()
        composeRule.onNodeWithTag("rescan_uncertain").assertExists()
        composeRule.runOnIdle { language = AppLanguage.ENGLISH }
        composeRule.onNodeWithText("Not enough confidence to identify the species").assertExists()
    }

    @Test fun `capture error releases reservation and allows retry`() {
        val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(model.beginCapture(7, rect))
        assertFalse(model.beginCapture(8, rect))
        model.reportRecognitionError(IllegalStateException("Capture failed"))
        assertTrue(model.scanState.value is ScanState.Failed)
        assertTrue(model.beginCapture(7, rect))
    }

    @Test fun `missing camera transform reports capture failure and releases reservation`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = MainViewModel(application)
        val controller = com.example.ui.camera.CameraController(application,
            onImageCaptured = { error("Unexpected full-frame capture") },
            onError = model::reportRecognitionError)
        try {
            controller.previewView = androidx.camera.view.PreviewView(application).apply {
                layout(0, 0, 400, 800)
            }
            assertTrue(model.beginCapture(7, rect))
            controller.captureTarget(com.example.ui.camera.TargetCaptureRequest(7, RectF(20f, 20f, 120f, 220f))) {
                error("A preview with no sensor transform must not produce a snapshot")
            }
            assertTrue(model.scanState.value is ScanState.Failed)
            assertTrue(model.beginCapture(7, rect))
        } finally { controller.release() }
    }

    @Test fun `uncertain result clears previously identified target and rescan clears suggestions`() = runBlocking {
        val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.createOrMoveTargetBox(.5f, .5f)
        val id = model.selectedTrackId.value!!
        val species = com.example.data.repository.SpeciesCatalog.fromScientificName("Example plant", .9f)
        model.identifyImage = { _, _ -> com.example.data.model.RecognitionResult.Organism(species) }
        model.analyzeImage(ScanRequest(id, thumbnail, rect))
        awaitCompleted(model)
        assertEquals(species, model.boxSpeciesMap.value[id])
        model.identifyImage = { _, _ -> com.example.data.model.RecognitionResult.Uncertain(emptyList()) }
        assertTrue(model.beginCapture(id, rect))
        assertEquals(null, model.boxSpeciesMap.value[id])
        model.analyzeImage(ScanRequest(id, thumbnail, rect))
        awaitCompleted(model)
        assertEquals(null, model.detectedSpecies.value)
        assertEquals(null, model.trackedObjects.value.first().identifiedSpecies)
        model.rescanCurrentTarget()
        assertEquals(ScanState.Idle, model.scanState.value)
    }

    private suspend fun awaitCompleted(model: MainViewModel) {
        repeat(100) {
            if (model.scanState.value is ScanState.Completed && !model.isAnalyzing.value) return
            delay(10)
        }
        throw AssertionError("Scan did not complete")
    }

    @Test fun `view model captures snapshot rejects duplicate and finishes with typed failure`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.identifyImage = { _, _ ->
            com.example.data.model.RecognitionResult.Failure(
                com.example.data.model.ScanException(ScanFailureReason.Unexpected)
            )
        }
        assertTrue(viewModel.beginCapture(7, rect))
        assertTrue(viewModel.scanState.value is ScanState.CapturingFrame)
        assertFalse(viewModel.beginCapture(7, RectF(0f, 0f, 1f, 1f)))

        viewModel.analyzeImage(ScanRequest(7, thumbnail, rect))
        awaitFailure(viewModel)
        assertEquals(null, viewModel.scanThumbnail.value)
        assertTrue(viewModel.scanState.value is ScanState.Failed)
    }

    @Test fun `overlay shows crop thumbnail and phase progress`() {
        composeRule.setContent {
            ScannerOverlay(
                detectedSpecies = null,
                isAnalyzing = true,
                scanState = ScanState.Classifying(7, rect),
                scanThumbnail = thumbnail,
                language = AppLanguage.ENGLISH,
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
        composeRule.onNodeWithTag("scan_thumbnail").assertExists()
        composeRule.onNodeWithTag("scan_phase_progress").assertExists()
    }

    @Test fun `overlay shows typed scan error state`() {
        composeRule.setContent {
            ScannerOverlay(
                detectedSpecies = null,
                isAnalyzing = false,
                scanState = ScanState.Failed(7, rect, ScanFailureReason.Unexpected),
                language = AppLanguage.VIETNAMESE,
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
        composeRule.onNodeWithTag("scan_error").assertExists()
    }

    @Test fun `detector model error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(IllegalArgumentException("private details"), DetectorErrorType.INVALID_MODEL))
        assertDetectorGuidance("The detector model is invalid or missing.")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `detector ready leaves startup state explicitly`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

        assertEquals(DetectorState.NotReady, viewModel.detectorState.value)
        viewModel.onDetectorReady()

        assertEquals(DetectorState.NoObjects, viewModel.detectorState.value)
    }

    @Test fun `camera analysis startup failure replaces startup guidance with retry`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.onDetectorError(CameraAnalysisInitializationException("first frame timed out"))

        val errorState = viewModel.detectorState.value as DetectorState.Error
        assertEquals(DetectorErrorType.UNKNOWN, errorState.type)
        assertEquals(DetectorStage.CAMERA_ANALYSIS, errorState.stage)
        setDetectorState(errorState)
        assertDetectorGuidance("The detector encountered an unknown error. [CAM-ANALYSIS]")
        composeRule.onNodeWithText("Detector is starting…").assertDoesNotExist()
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `incompatible runtime error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(UnsatisfiedLinkError("native stack"), DetectorErrorType.INCOMPATIBLE_RUNTIME))
        assertDetectorGuidance("The detector runtime is incompatible with this device/ABI.")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `unknown detector error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(RuntimeException("secret"), DetectorErrorType.UNKNOWN))
        assertDetectorGuidance("The detector encountered an unknown error.")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `frame stages use safe diagnostic codes without exception details`() {
        assertEquals(
            listOf("CAM-FRAME", "MP-IMAGE", "MP-DETECT"),
            listOf(
                DetectorStage.IMAGE_TO_BITMAP,
                DetectorStage.MP_IMAGE_CREATION,
                DetectorStage.DETECTOR_DETECT
            ).map(::detectorStageCode)
        )

        setDetectorState(
            DetectorState.FrameError(
                DetectorStageException(
                    DetectorStage.MP_IMAGE_CREATION,
                    "secret message and stack",
                    IllegalStateException()
                ),
                stage = DetectorStage.MP_IMAGE_CREATION
            )
        )
        assertDetectorGuidance("This frame could not be processed. Detection is still running. [MP-IMAGE]")
        composeRule.onNodeWithText("secret", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("IllegalStateException", substring = true).assertDoesNotExist()
    }

    @Test fun `prolonged frame error offers retry after threshold`() {
        setDetectorState(
            DetectorState.Error(
                IllegalArgumentException("repeated frame bytes"),
                DetectorErrorType.FRAME_TEMPORARY
            )
        )
        assertDetectorGuidance("This frame could not be processed. Please try again. [CAM-FRAME]")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    private fun assertDetectorGuidance(expected: String) {
        composeRule.onNode(
            hasText(expected) and hasAnyAncestor(hasTestTag("detector_status_guidance")),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test fun `offline model startup failure has local retry guidance`() {
        composeRule.setContent {
            ScannerOverlay(
                detectedSpecies = null,
                isAnalyzing = false,
                scanState = ScanState.Failed(7, rect, ScanFailureReason.LocalModelUnavailable),
                language = AppLanguage.ENGLISH,
                selectedTrackId = 7,
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
        composeRule.onNodeWithText("The offline model is unavailable. Please scan again.").assertExists()
        composeRule.onNodeWithTag("capture_button").assertExists()
    }

    @Test fun `classifier initialization error maps to local model failure`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(
            ScanFailureReason.LocalModelUnavailable,
            viewModel.classifyScanFailure(LocalModelUnavailableException(IllegalStateException("gpu and cpu failed")))
        )
    }

    private fun setDetectorState(state: DetectorState) {
        composeRule.setContent {
            ScannerOverlay(
                detectedSpecies = null,
                isAnalyzing = false,
                detectorState = state,
                language = AppLanguage.ENGLISH,
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
    }

    @Test fun `unexpected analysis exception stays on scanner and permits retry`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        var attempts = 0
        viewModel.identifyImage = { _, _ ->
            attempts++
            throw IllegalStateException("surprise")
        }

        viewModel.analyzeImage(ScanRequest(7, thumbnail, rect))
        awaitFailure(viewModel) { attempts == 1 }
        assertEquals(ScanFailureReason.Unexpected, (viewModel.scanState.value as ScanState.Failed).reason)
        assertTrue(viewModel.detectedSpecies.value == null)

        // A failed scan releases the track reservation, so the camera screen can scan it again.
        viewModel.analyzeImage(ScanRequest(7, thumbnail, rect))
        awaitFailure(viewModel) { attempts == 2 }
        assertEquals(2, attempts)
    }

    @Test fun `snackbar message failure is contained and recognition error can be cleared`() = runBlocking {
        var handled = false
        showRecognitionErrorSnackbar(
            error = IllegalStateException("recognition failed"),
            buildMessage = { throw IllegalArgumentException("broken resources") },
            showMessage = { error("must not be called") },
            onHandled = { handled = true }
        )

        assertTrue(handled)
    }

    @Test fun `snackbar display failure is contained and camera state remains usable`() = runBlocking {
        var handled = false
        showRecognitionErrorSnackbar(
            error = IllegalStateException("recognition failed"),
            buildMessage = { "Please scan again" },
            showMessage = { throw IllegalStateException("Snackbar host unavailable") },
            onHandled = { handled = true }
        )

        assertTrue(handled)
    }

    private suspend fun awaitFailure(viewModel: MainViewModel, condition: () -> Boolean = { true }) {
        repeat(100) {
            if (condition() && viewModel.scanState.value is ScanState.Failed && !viewModel.isAnalyzing.value) return
            delay(10)
        }
        throw AssertionError("Scan did not fail in time")
    }
}
