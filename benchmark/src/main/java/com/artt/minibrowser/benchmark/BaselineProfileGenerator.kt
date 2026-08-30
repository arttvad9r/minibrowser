package com.artt.minibrowser.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val TAB_COUNT_DESCRIPTION = Pattern.compile("^\\d+ (?:вкладка|вкладки|вкладок)$")

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() {
        resetTargetState()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            outputFilePrefix = "startup",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun coreBrowserJourneys() {
        resetTargetState()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            outputFilePrefix = "core-browser",
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()

            clickDescription(device, "Новая вкладка")
            clickTabSwitcher(device)
            device.pressBack()
            device.waitForIdle()

            clickDescription(device, "Меню")
            clickText(device, "Настройки")
            device.pressBack()
            device.waitForIdle()
        }
    }

    /** Keep generated rules independent from JUnit method order and previous profile journeys. */
    private fun resetTargetState() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val result = device.executeShellCommand("pm clear $TARGET_PACKAGE")
        check(result.contains("Success", ignoreCase = true)) {
            "Unable to reset $TARGET_PACKAGE before profile collection: $result"
        }
        device.waitForIdle()
    }

    private fun clickDescription(device: UiDevice, description: String) {
        val target = checkNotNull(device.wait(Until.findObject(By.desc(description)), 3_000)) {
            "Missing UI element with content description: $description"
        }
        target.click()
        device.waitForIdle()
    }

    private fun clickTabSwitcher(device: UiDevice) {
        val target = checkNotNull(device.wait(Until.findObject(By.desc(TAB_COUNT_DESCRIPTION)), 3_000)) {
            "Missing tab switcher with plural tab-count content description"
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
