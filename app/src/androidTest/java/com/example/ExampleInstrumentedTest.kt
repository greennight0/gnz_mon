package com.example

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.AppLanguage
import com.example.data.model.TrackedBoundingBox
import com.example.ui.components.ScannerOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trackingUpdateBetweenDownAndUpDoesNotCancelTap() {
        val initial = TrackedBoundingBox(
            id = 42,
            normalizedRect = RectF(0.2f, 0.25f, 0.6f, 0.55f),
            label = "Target",
            confidence = 0.9f
        )
        var boxes by mutableStateOf(listOf(initial))
        val selections = mutableListOf<Int?>()
        composeRule.setContent {
            ScannerOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                detectedSpecies = null,
                isAnalyzing = false,
                language = AppLanguage.ENGLISH,
                trackedObjects = boxes,
                onSelectTrack = { selections += it },
                onSpeciesClick = {},
                onCaptureClick = {}
            )
        }

        val canvas = composeRule.onNodeWithTag("bounding_box_canvas")
        canvas.performTouchInput { down(Offset(160f, 320f)) }
        boxes = listOf(initial.copy(normalizedRect = RectF(0.7f, 0.7f, 0.9f, 0.9f)))
        composeRule.mainClock.advanceTimeByFrame()
        canvas.performTouchInput { up() }

        assertEquals(listOf(42), selections)
    }
}
