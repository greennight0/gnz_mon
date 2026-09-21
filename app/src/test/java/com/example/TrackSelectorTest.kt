package com.example

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.click
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.ui.MainViewModel
import com.example.ui.MANUAL_TARGET_TRACK_ID
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.hitTestTrackedBoxes
import com.example.ui.components.displayedTrackedRect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp-mdpi", sdk = [36])
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

    private fun canvasPoint(x: Float, y: Float): Offset {
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag("bounding_box_canvas").fetchSemanticsNode().boundsInRoot
        return Offset(bounds.width * x, bounds.height * y)
    }

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
            .assertTopPositionInRootIsEqualTo(36.dp + 6.dp) // Header inset plus badge content padding.
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
    fun tappingEmptyCanvasCreatesTargetAtNormalizedPosition() {
        var createdTarget: Pair<Float, Float>? = null
        composeRule.setContent {
            ScannerOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                detectedSpecies = null,
                isAnalyzing = false,
                language = AppLanguage.VIETNAMESE,
                onCreateTarget = { x, y -> createdTarget = x to y },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }

        composeRule.onNodeWithTag("bounding_box_canvas")
            .performTouchInput { click(Offset(width * 0.25f, height * 0.75f)) }

        composeRule.waitForIdle()
        assertEquals(0.25f, createdTarget?.first ?: -1f, 0.001f)
        assertEquals(0.75f, createdTarget?.second ?: -1f, 0.001f)
    }

    @Test
    fun tappingExistingBoxOnCanvasStillSelectsAndDeselectsWithoutCreatingTarget() {
        val selections = mutableListOf<Int?>()
        val creations = mutableListOf<Pair<Float, Float>>()
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
                    selections += it
                },
                onCreateTarget = { x, y -> creations += x to y },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }

        val boxCenter = canvasPoint(0.4f, 0.4f)
        composeRule.onNodeWithTag("bounding_box_canvas").performTouchInput { click(boxCenter) }
        composeRule.onNodeWithTag("bounding_box_canvas").performTouchInput { click(boxCenter) }

        composeRule.waitForIdle()
        assertEquals(listOf(42, null), selections)
        assertEquals(emptyList<Pair<Float, Float>>(), creations)
    }

    @Test
    fun tappingOutsideAllBoundingBoxesKeepsSelection() {
        val selections = mutableListOf<Int?>()
        setOverlayContent { selections += it }

        composeRule.onNodeWithTag("bounding_box_target_42")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()

        composeRule.onNodeWithTag("bounding_box_canvas")
            .performTouchInput { click(Offset(20f, 400f)) }

        composeRule.waitForIdle()
        assertEquals(listOf(42), selections)
        composeRule.onNodeWithTag("bounding_box_target_42").assertIsSelected()
        composeRule.onNodeWithTag("capture_button").assertExists()
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
    fun detectorBoxSmallerThanDrawnMinimumHitsOnAllDisplayedEdges() {
        val tiny = trackedBox.copy(normalizedRect = RectF(0.25f, 0.25f, 0.26f, 0.255f))
        val displayed = displayedTrackedRect(tiny, 400f, 800f, 60f)
        val edgeTaps = listOf(
            Offset(displayed.left, displayed.center.y),
            Offset(displayed.right, displayed.center.y),
            Offset(displayed.center.x, displayed.top),
            Offset(displayed.center.x, displayed.bottom)
        )

        edgeTaps.forEach { tap ->
            assertEquals(
                42,
                hitTestTrackedBoxes(tap, listOf(tiny), null, 400f, 800f, 0f, 60f, 48f, 0f)?.id
            )
        }
    }

    @Test
    fun velocityPaddingIsBoundedAndHelpsFastMovingBox() {
        val fast = trackedBox.copy(
            normalizedRect = RectF(0.2f, 0.2f, 0.3f, 0.3f),
            velocityX = 1f,
            velocityY = -1f
        )
        assertEquals(
            42,
            hitTestTrackedBoxes(Offset(55f, 160f), listOf(fast), null, 400f, 800f, 0f, 0f, 48f, 24f)?.id
        )
        assertEquals(
            null,
            hitTestTrackedBoxes(Offset(51f, 160f), listOf(fast), null, 400f, 800f, 0f, 0f, 48f, 24f)?.id
        )
    }

    @Test
    fun gestureUsesPointerDownGeometryWhenListAndSameIdRectChange() {
        val selections = mutableListOf<Int?>()
        var boxes by mutableStateOf(listOf(trackedBox))
        composeRule.setContent {
            ScannerOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                detectedSpecies = null,
                isAnalyzing = false,
                language = AppLanguage.VIETNAMESE,
                trackedObjects = boxes,
                onSelectTrack = { selections += it },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }

        val originalCenter = canvasPoint(0.4f, 0.4f)
        composeRule.onNodeWithTag("bounding_box_canvas").performTouchInput { down(originalCenter) }
        composeRule.runOnIdle { boxes = listOf(trackedBox.copy(normalizedRect = RectF(0.7f, 0.7f, 0.9f, 0.9f))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("bounding_box_canvas").performTouchInput { up() }

        composeRule.waitForIdle()
        assertEquals(listOf(42), selections)
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
    fun tappingJustOutsideSelectedBoxKeepsSelection() {
        val selections = mutableListOf<Int?>()
        var selectedId by mutableStateOf<Int?>(42)
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
                    selections += it
                },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }

        // The visual left edge is x=80; x=75 is within the 20dp forgiving hit area.
        composeRule.onNodeWithTag("bounding_box_canvas")
            .performTouchInput { click(Offset(75f, 320f)) }

        assertEquals(emptyList<Int?>(), selections)
        composeRule.onNodeWithTag("bounding_box_target_42").assertIsSelected()
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

    @Test
    fun targetLockExpiresOnFourthMissingFrameAndResetsAfterReturn() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())
        val target = trackedBox.copy(id = 10)
        viewModel.onObjectsTracked(listOf(target), 16)
        viewModel.selectTrack(10)
        repeat(3) { viewModel.onObjectsTracked(emptyList(), 16) }
        assertEquals(10, viewModel.selectedTrackId.value)
        viewModel.onObjectsTracked(listOf(target), 16)
        repeat(3) { viewModel.onObjectsTracked(emptyList(), 16) }
        assertEquals(10, viewModel.selectedTrackId.value)
        viewModel.onObjectsTracked(emptyList(), 16)
        assertEquals(null, viewModel.selectedTrackId.value)
    }

    @Test
    fun hitTestRejectsPointsBeyondAllDisplayedEdges() {
        val tiny = trackedBox.copy(normalizedRect = RectF(0.25f, 0.25f, 0.26f, 0.255f))
        val rect = displayedTrackedRect(tiny, 400f, 800f, 60f)
        listOf(
            Offset(rect.left - 0.01f, rect.center.y),
            Offset(rect.right + 0.01f, rect.center.y),
            Offset(rect.center.x, rect.top - 0.01f),
            Offset(rect.center.x, rect.bottom + 0.01f)
        ).forEach { point ->
            assertEquals(null, hitTestTrackedBoxes(point, listOf(tiny), null, 400f, 800f, 0f, 60f, 48f, 0f))
        }
    }

    @Test
    fun emptyDetectorFrameDoesNotRemoveSelectedManualTargetBeforeCapture() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())
        viewModel.createOrMoveTargetBox(0.7f, 0.4f)

        viewModel.onObjectsTracked(emptyList(), 16)

        assertEquals(MANUAL_TARGET_TRACK_ID, viewModel.selectedTrackId.value)
        assertEquals(MANUAL_TARGET_TRACK_ID, viewModel.trackedObjects.value.single().id)
        assertEquals(true, viewModel.trackedObjects.value.single().isSelected)
    }
}
