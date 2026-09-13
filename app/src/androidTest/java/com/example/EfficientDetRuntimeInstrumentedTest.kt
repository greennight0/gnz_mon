package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.camera.EfficientDetLiteEngine
import org.junit.Test
import org.junit.runner.RunWith

/** Native MediaPipe is initialized on a device here, not in Robolectric's host JVM. */
@RunWith(AndroidJUnit4::class)
class EfficientDetRuntimeInstrumentedTest {
    @Test
    fun detectorInitializesWithPackagedModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        EfficientDetLiteEngine.create(context).close()
    }
}
