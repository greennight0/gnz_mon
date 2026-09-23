package com.example

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.data.model.AppLanguage
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanState
import com.example.data.model.TrackedBoundingBox
import com.example.ui.MANUAL_TARGET_TRACK_ID
import com.example.ui.components.ScannerOverlay
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 360x800 dp reproduces the attached 720x1600 screenshots at 2x density.
 * Candidate names are UI fixtures, not identifications of the user's photographs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class UncertainScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun vietnamese34Percent() = render(AppLanguage.VIETNAMESE, .34f, "vi-34")
    @Test fun english67Percent() = render(AppLanguage.ENGLISH, .67f, "en-67")

    private fun render(language: AppLanguage, score: Float, name: String) {
        val id = MANUAL_TARGET_TRACK_ID
        val rect = RectF(.36f, .24f, .76f, .55f)
        val result = RecognitionResult.Uncertain(listOf(
            RecognitionResult.Candidate("Candidate one (unconfirmed)", score),
            RecognitionResult.Candidate("Candidate two with a longer scientific name", .15f),
            RecognitionResult.Candidate("Candidate three", .1f)))
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF35473C))) {
                ScannerOverlay(detectedSpecies = null, isAnalyzing = false, language = language,
                    selectedTrackId = id,
                    trackedObjects = listOf(TrackedBoundingBox(id, rect, "Target Specimen", .95f, isSelected = true)),
                    scanState = ScanState.Completed(id, rect, result), onSpeciesClick = {}, onCaptureClick = {})
            }
        }
        rule.onNodeWithTag("uncertain_message").assertIsDisplayed()
        rule.onNodeWithTag("rescan_uncertain").assertIsDisplayed()
        rule.onNodeWithTag("capture_button").assertIsDisplayed()
        rule.onNodeWithText("TRK #$id").assertDoesNotExist()
        val badge = rule.onNodeWithTag("box_header_tag_$id").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(badge.left >= root.left && badge.right <= root.right)
        rule.onRoot().captureRoboImage(filePath = "build/reports/recognition/$name.png")
    }
}
