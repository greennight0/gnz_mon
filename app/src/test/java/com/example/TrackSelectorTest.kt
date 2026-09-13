package com.example

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
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
}
