package com.example

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.ui.components.ScannerOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val trackedBox = TrackedBoundingBox(
        id = 42,
        normalizedRect = RectF(0.2f, 0.25f, 0.6f, 0.55f),
        label = "Nature Specimen (Mẫu vật)",
        confidence = 0.92f
    )

    private val detectedSpecies = SpeciesInfo(
        id = "species-42",
        commonNameEn = "Specimen",
        commonNameVi = "Mẫu vật",
        scientificName = "Specimen testus",
        category = SpeciesCategory.PLANT.name,
        kingdom = "Plantae",
        family = "Testaceae",
        descriptionEn = "Test specimen",
        descriptionVi = "Mẫu kiểm thử",
        habitatEn = "Test habitat",
        habitatVi = "Môi trường kiểm thử",
        distributionEn = "Test distribution",
        distributionVi = "Phân bố kiểm thử",
        ecologicalRoleEn = "Test role",
        ecologicalRoleVi = "Vai trò kiểm thử",
        mysteriaFactEn = "Test fact",
        mysteriaFactVi = "Thông tin kiểm thử"
    )

    private fun setOverlayContent(onSelectionChanged: (Int?) -> Unit) {
        var selectedId by mutableStateOf<Int?>(null)
        composeRule.setContent {
            ScannerOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                detectedSpecies = null,
                isAnalyzing = false,
                language = AppLanguage.VIETNAMESE,
                trackedObjects = listOf(trackedBox),
                selectedTrackId = selectedId,
                onSelectTrack = {
                    selectedId = it
                    onSelectionChanged(it)
                },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }
    }

    @Test
    fun scannerDoesNotExposeAlgorithmSelectionControl() {
        setOverlayContent {}

        composeRule.onNodeWithTag("ai_telemetry_hud_bar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Toggle Algorithm").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Technology Info").assertDoesNotExist()
        composeRule.onNodeWithText("BYTETracker", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("DeepSORT", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("app_brand_badge")
            .assertExists()
            .assertTopPositionInRootIsEqualTo(36.dp)
    }

    @Test
    fun selectorBarIsRemovedAndBadgeSelectsThenDeselectsTrack() {
        var lastSelectedId: Int? = null
        setOverlayContent { lastSelectedId = it }

        composeRule.onNodeWithTag("track_selector_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_button").assertDoesNotExist()
        composeRule.onNodeWithTag("box_header_tag_42")
            .assertContentDescriptionEquals("Mục tiêu Nature Specimen (Mẫu vật)")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()
        assertEquals(42, lastSelectedId)

        composeRule.onNodeWithTag("box_header_tag_42")
            .performClick()
            .assertIsNotSelected()
        composeRule.onNodeWithTag("capture_button").assertDoesNotExist()
        assertEquals(null, lastSelectedId)
    }

    @Test
    fun boundingBoxHitTargetSelectsThenDeselectsTrack() {
        var lastSelectedId: Int? = null
        setOverlayContent { lastSelectedId = it }

        composeRule.onNodeWithTag("capture_button").assertDoesNotExist()
        composeRule.onNodeWithTag("bounding_box_target_42")
            .assertContentDescriptionEquals("Mục tiêu Nature Specimen (Mẫu vật)")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()
        assertEquals(42, lastSelectedId)

        composeRule.onNodeWithTag("bounding_box_target_42")
            .performClick()
            .assertIsNotSelected()
        composeRule.onNodeWithTag("capture_button").assertDoesNotExist()
        assertEquals(null, lastSelectedId)
    }

    @Test
    fun tappingOutsideAllBoundingBoxesClearsSelection() {
        val selections = mutableListOf<Int?>()
        setOverlayContent { selections += it }

        composeRule.onNodeWithTag("bounding_box_target_42")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()

        composeRule.onNodeWithTag("bounding_box_canvas")
            .performTouchInput { click(Offset(20f, 400f)) }

        assertEquals(listOf(42, null), selections)
        composeRule.onNodeWithTag("bounding_box_target_42").assertIsNotSelected()
        composeRule.onNodeWithTag("capture_button").assertDoesNotExist()
    }

    @Test
    fun foregroundControlsDoNotClearSelectedTrack() {
        val selections = mutableListOf<Int?>()
        var torchClicks = 0
        var captureClicks = 0
        var speciesClicks = 0
        var selectedId by mutableStateOf<Int?>(42)
        composeRule.setContent {
            ScannerOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                detectedSpecies = detectedSpecies,
                isAnalyzing = false,
                language = AppLanguage.VIETNAMESE,
                trackedObjects = listOf(trackedBox),
                selectedTrackId = selectedId,
                onSelectTrack = {
                    selectedId = it
                    selections += it
                },
                onSpeciesClick = { speciesClicks++ },
                onCaptureClick = { captureClicks++ },
                onTorchToggle = { torchClicks++ }
            )
        }

        composeRule.onNodeWithTag("flash_button").performClick()
        composeRule.onNodeWithTag("basic_info_button").performClick()
        composeRule.onNodeWithTag("capture_button").performClick()

        assertEquals(1, torchClicks)
        assertEquals(1, speciesClicks)
        assertEquals(1, captureClicks)
        assertEquals(emptyList<Int?>(), selections)
        composeRule.onNodeWithTag("bounding_box_target_42").assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()
    }
}
