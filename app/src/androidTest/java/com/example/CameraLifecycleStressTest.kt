package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.MainViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device test for the lifecycle sequence that previously raced MediaPipe native close. */
@RunWith(AndroidJUnit4::class)
class CameraLifecycleStressTest {
    @Before fun grantCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Some OEMs deny shell permission grants even when the user already granted access.
        if (instrumentation.targetContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName, Manifest.permission.CAMERA
            )
        }
    }

    @Test fun repeatedOpenFlipBackgroundForegroundAndRecreateDoesNotLeakExecutor() {
        val initialThreads = cameraThreads()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(8) {
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[MainViewModel::class.java].flipCamera()
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
            }
        }

        // Closing the scenario must synchronously drain analysis before native detector close.
        repeat(30) {
            if (cameraThreads() <= initialThreads) return
            Thread.sleep(100)
        }
        assertTrue("Camera executors leaked after lifecycle stress", cameraThreads() <= initialThreads)
    }

    private fun cameraThreads() = Thread.getAllStackTraces().keys.count {
        it.isAlive && (it.name.startsWith("camera-analysis-") || it.name.startsWith("camera-capture-"))
    }
}
