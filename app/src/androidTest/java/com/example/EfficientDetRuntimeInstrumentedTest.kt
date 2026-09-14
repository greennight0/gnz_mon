package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.camera.EfficientDetLiteEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native MediaPipe is initialized on a device here, not in Robolectric's host JVM. */
@RunWith(AndroidJUnit4::class)
class NatureScopeDetectorInstrumentedTest {
    @Test
    fun detectorInitializesWithPackagedModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        EfficientDetLiteEngine.create(context).close()
    }

    @Test
    fun packagedModelDeclaresEveryCalibratedOrganismGroup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manifest = context.assets.open(
            "models/nature_scope_efficientdet_lite0_int8.manifest.json"
        ).bufferedReader().use { JSONObject(it.readText()) }
        val actual = manifest.getJSONArray("supportedOrganismGroups")
            .let { groups -> (0 until groups.length()).map(groups::getString).toSet() }
        assertEquals(
            setOf("plant", "leaf", "flower", "fruit", "vegetable", "fungus", "insect", "animal"),
            actual
        )
        val calibration = manifest.getJSONObject("calibration")
        assertEquals(0.22, calibration.getDouble("selectedThreshold"), 0.0)
        assertTrue(calibration.getJSONArray("requiredConditions").length() >= 4)
    }
}
