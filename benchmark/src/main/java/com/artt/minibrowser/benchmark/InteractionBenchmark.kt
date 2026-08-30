package com.artt.minibrowser.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class InteractionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun tabOverviewOpenClose() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.WARM,
            iterations = 8,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            clickDescriptionContains(device, "вклад")
            device.pressBack()
            device.waitForIdle()
        }
    }

    @Test
    fun settingsRoundTrip() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.WARM,
            iterations = 8,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            clickDescription(device, "Меню")
            clickText(device, "Настройки")
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun clickDescription(device: UiDevice, description: String) {
        val target = checkNotNull(device.wait(Until.findObject(By.desc(description)), 3_000)) {
            "Missing UI element with content description: $description"
        }
        target.click()
        device.waitForIdle()
    }

    private fun clickDescriptionContains(device: UiDevice, descriptionPart: String) {
        val target = checkNotNull(device.wait(Until.findObject(By.descContains(descriptionPart)), 3_000)) {
            "Missing UI element with content description containing: $descriptionPart"
        }
        target.click()
        device.waitForIdle()
    }

    private fun clickText(device: UiDevice, text: String) {
        val target = checkNotNull(device.wait(Until.findObject(By.text(text)), 3_000)) {
            "Missing UI element with text: $text"
        }
        target.click()
        device.waitForIdle()
    }
}
