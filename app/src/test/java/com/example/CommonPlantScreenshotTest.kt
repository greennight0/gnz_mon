package com.example

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.*
import com.example.ui.components.ScannerOverlay
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class CommonPlantScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun vietnamese() = render(AppLanguage.VIETNAMESE, "vi")
    @Test fun english() = render(AppLanguage.ENGLISH, "en")
    private fun render(language: AppLanguage, name: String) {
        val rect = RectF(.25f,.3f,.75f,.65f)
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF35473C))) {
                ScannerOverlay(detectedSpecies = null, isAnalyzing = false, language = language,
                    selectedTrackId = 1,
                    trackedObjects = listOf(TrackedBoundingBox(1,rect,"Target",.9f),
                        TrackedBoundingBox(2,RectF(.1f,.2f,.3f,.4f),"Noise",.3f)),
                    scanState = ScanState.Completed(1,rect,RecognitionResult.CommonPlant("banana","Chuối","Banana",.99f)),
                    onSpeciesClick = {}, onCaptureClick = {})
            }
        }
        rule.onNodeWithTag("common_plant_tag").assertIsDisplayed()
        rule.onNodeWithTag("box_header_tag_2").assertDoesNotExist()
        rule.onNodeWithText("99%", substring = true).assertDoesNotExist()
        rule.onNodeWithText("#1", substring = true).assertDoesNotExist()
        rule.onRoot().captureRoboImage(filePath = "build/reports/common-plant/$name.png")
    }
}
