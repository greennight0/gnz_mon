package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppLanguage
import com.example.data.model.ScanFailureReason
import com.example.data.model.ScanState
import com.example.ui.MainViewModel
import com.example.ui.ScanRequest
import com.example.ui.components.ScannerOverlay
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
        assertTrue(viewModel.scanThumbnail.value === thumbnail)
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

    @Test fun `unexpected analysis exception stays on scanner and permits retry`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        var attempts = 0
        viewModel.identifyImage = { _, _, _ ->
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
