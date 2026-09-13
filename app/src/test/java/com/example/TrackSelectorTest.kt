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
import com.example.ui.MainViewModel
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.hitTestTrackedBoxes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @Test
    fun hitTestIncludesPaddingJustOutsideBoxEdge() {
        val hit = hitTestTrackedBoxes(
            tapPosition = Offset(75f, 320f), // 5 px left of the box on a 400 px-wide surface.
            trackedObjects = listOf(trackedBox),
            selectedTrackId = null,
            screenW = 400f,
            screenH = 800f,
            touchPaddingPx = 20f
        )

        assertEquals(42, hit?.id)
    }

    @Test
    fun overlappingHitTargetsPreferSelectedThenSmallestBox() {
        val large = trackedBox.copy(id = 1, normalizedRect = RectF(0.1f, 0.1f, 0.8f, 0.8f))
        val small = trackedBox.copy(id = 2, normalizedRect = RectF(0.35f, 0.35f, 0.55f, 0.55f))
        val tap = Offset(180f, 360f)

        assertEquals(
            1,
            hitTestTrackedBoxes(tap, listOf(large, small), 1, 400f, 800f, 20f)?.id
        )
        assertEquals(
            2,
            hitTestTrackedBoxes(tap, listOf(large, small), null, 400f, 800f, 20f)?.id
        )
    }

    @Test
    fun nearMissOnSelectedBoxDoesNotSelectOverlappingNeighbour() {
        val selected = trackedBox.copy(id = 1, normalizedRect = RectF(0.2f, 0.2f, 0.4f, 0.4f))
        val neighbour = trackedBox.copy(id = 2, normalizedRect = RectF(0.41f, 0.2f, 0.7f, 0.4f))

        val hit = hitTestTrackedBoxes(
            Offset(164f, 240f), // Just outside selected, inside neighbour's padded target.
            listOf(neighbour, selected),
            selectedTrackId = 1,
            screenW = 400f,
            screenH = 800f,
            touchPaddingPx = 20f
        )

        assertEquals(1, hit?.id)
    }

    @Test
    fun targetLockFollowsMovingBoxWhenDetectorChangesId() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())
        val first = trackedBox.copy(id = 10, normalizedRect = RectF(0.20f, 0.20f, 0.50f, 0.50f))
        val moved = trackedBox.copy(id = 99, normalizedRect = RectF(0.24f, 0.22f, 0.54f, 0.52f))
        viewModel.onObjectsTracked(listOf(first), 16)
        viewModel.selectTrack(10)

        viewModel.onObjectsTracked(listOf(moved), 16)

        assertEquals(99, viewModel.selectedTrackId.value)
        assertEquals(true, viewModel.trackedObjects.value.single().isSelected)
    }

    @Test
    fun targetLockSurvivesBriefMissingDetectionAndStableIdReturn() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())
        val target = trackedBox.copy(id = 10)
        viewModel.onObjectsTracked(listOf(target), 16)
        viewModel.selectTrack(10)

        repeat(3) { viewModel.onObjectsTracked(emptyList(), 16) }
        assertEquals(10, viewModel.selectedTrackId.value)

        viewModel.onObjectsTracked(listOf(target.copy(normalizedRect = RectF(0.22f, 0.25f, 0.62f, 0.55f))), 16)
        assertEquals(10, viewModel.selectedTrackId.value)
        assertEquals(true, viewModel.trackedObjects.value.single().isSelected)
    }
}
