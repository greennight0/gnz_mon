package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.AppLanguage
import com.example.ui.components.ScannerOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraPermissionContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deniedPermission_requestButtonIsNotBlockedByScannerOverlay() {
        var permissionRequestCount = 0

        composeTestRule.setContent {
            CameraPermissionContent(
                isCameraPermissionGranted = false,
                language = AppLanguage.ENGLISH,
                onRequestPermission = { permissionRequestCount++ }
            ) {
                ScannerOverlay(
                    detectedSpecies = null,
                    isAnalyzing = false,
                    language = AppLanguage.ENGLISH,
                    onSpeciesClick = {},
                    onCaptureClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("scanner_overlay_container").assertDoesNotExist()
        composeTestRule.onNodeWithTag("demo_without_camera_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("request_camera_permission_button").performClick()

        assertEquals(1, permissionRequestCount)
    }
}
