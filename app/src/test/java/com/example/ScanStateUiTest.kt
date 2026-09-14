package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
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

    @Test fun `view model captures snapshot rejects duplicate and finishes with typed failure`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(viewModel.beginCapture(7, rect))
        assertTrue(viewModel.scanState.value is ScanState.CapturingFrame)
        assertFalse(viewModel.beginCapture(7, RectF(0f, 0f, 1f, 1f)))

        viewModel.analyzeImage(ScanRequest(7, thumbnail, rect))
        repeat(100) {
            if (viewModel.scanState.value is ScanState.Failed) return@repeat
            delay(10)
        }
        assertEquals(null, viewModel.scanThumbnail.value)
        assertTrue(viewModel.scanState.value is ScanState.Failed)
    }

    @Test fun `overlay shows crop thumbnail and phase progress`() {
        composeRule.setContent {
            ScannerOverlay(
                detectedSpecies = null,
                isAnalyzing = true,
                scanState = ScanState.Uploading(7, rect),
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
                scanState = ScanState.Failed(7, rect, ScanFailureReason.Network),
                language = AppLanguage.VIETNAMESE,
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
        composeRule.onNodeWithTag("scan_error").assertExists()
    }

    @Test fun `detector model error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(IllegalArgumentException("private details"), DetectorErrorType.INVALID_MODEL))
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("The detector model is invalid or missing.", "Retry detector")
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
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("The detector encountered an unknown error. [CAM-ANALYSIS]", "Retry detector")
        composeRule.onNodeWithText("Detector is starting…").assertDoesNotExist()
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `incompatible runtime error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(UnsatisfiedLinkError("native stack"), DetectorErrorType.INCOMPATIBLE_RUNTIME))
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("The detector runtime is incompatible with this device/ABI.", "Retry detector")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
    }

    @Test fun `unknown detector error has safe guidance and retry`() {
        setDetectorState(DetectorState.Error(RuntimeException("secret"), DetectorErrorType.UNKNOWN))
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("The detector encountered an unknown error.", "Retry detector")
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
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("This frame could not be processed. Detection is still running. [MP-IMAGE]")
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
        composeRule.onNodeWithTag("detector_status_guidance")
            .assertTextEquals("This frame could not be processed. Please try again. [CAM-FRAME]", "Retry detector")
        composeRule.onNodeWithTag("retry_detector_button").assertExists()
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
