package com.example

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.TrackedBoundingBox
import com.example.ui.components.TrackSelectorBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackSelectorTest {
    @get:Rule val composeRule = createComposeRule()

    private fun box(id: Int) = TrackedBoundingBox(
        id = id,
        normalizedRect = RectF(0.1f, 0.1f, 0.3f, 0.3f),
        label = "Target $id",
        confidence = 0.8f + id / 1000f
    )

    @Test
    fun chipsSelectDeselectAdvanceAndSupportManyTargets() {
        var selectedId by mutableStateOf<Int?>(null)
        var boxes by mutableStateOf((1..12).map(::box))
        composeRule.setContent {
            TrackSelectorBar(
                trackedObjects = boxes,
                selectedTrackId = selectedId,
                isVietnamese = true,
                onSelectTrack = { selectedId = it },
                onNextTrack = {
                    val index = boxes.indexOfFirst { it.id == selectedId }
                    selectedId = boxes[(index + 1).mod(boxes.size)].id
                }
            )
        }

        composeRule.onNodeWithTag("track_selector_1").performClick().assertIsSelected()
        assertEquals(1, selectedId)
        composeRule.onNodeWithTag("next_track_button").performClick()
        composeRule.onNodeWithTag("track_selector_2").assertIsSelected()
        assertEquals(2, selectedId)
        composeRule.onNodeWithTag("track_selector_2").performClick().assertIsNotSelected()
        assertEquals(null, selectedId)

        composeRule.runOnIdle { boxes = boxes.filterNot { it.id == 2 } }
        composeRule.onNodeWithTag("track_selector_2").assertDoesNotExist()
        composeRule.onNodeWithTag("track_selector_12").performScrollTo().performClick().assertIsSelected()
        assertEquals(12, selectedId)
    }
}
