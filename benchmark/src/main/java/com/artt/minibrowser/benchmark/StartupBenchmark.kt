package com.artt.minibrowser.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.math.abs

internal const val TARGET_PACKAGE = "com.artt.minibrowser"
private const val GECKO_RUNTIME_CREATE_TRACE = "GeckoRuntime.create"
private const val DB_CREATE_TRACE = "DbHolder.create"
private const val TAB_STORE_LOAD_TRACE = "TabStore.loadState"
private const val TAB_RESTORE_MATERIALIZE_TRACE = "TabManager.restoreTabs"
private const val TAB_RESTORE_OPEN_SELECTED_TRACE = "TabManager.openSelected"
private const val TARGET_RESTORE_TAB_COUNT = 10
private val TOP_BAR_TAB_COUNT_TEXT = Pattern.compile("^\\d+$")
private val TAB_COUNT_TEXT = Pattern.compile("^\\d+ (?:вкладка|вкладки|вкладок)$")

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldNoCompilation() = startup(CompilationMode.None())

    @Test
    fun coldAfterJitWarmup() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3,
        ),
    )

    @Test
    fun coldWithBaselineProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        ),
    )

    @Test
    @OptIn(ExperimentalMetricApi::class)
    fun coldRestoreTenTabsWithBaselineProfile() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = restoreStartupMetrics(),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                clickTabSwitcher(device)
                val countLabel = checkNotNull(
                    device.wait(
                        Until.findObject(By.pkg(TARGET_PACKAGE).text(TAB_COUNT_TEXT)),
                        3_000,
                    ),
                ) {
                    "Missing full tab count in overview"
                }.text
                val existingTabs = countLabel.substringBefore(' ').toIntOrNull()
                check(existingTabs != null && existingTabs in 1..TARGET_RESTORE_TAB_COUNT) {
                    "Unexpected tab count while preparing restore benchmark: $countLabel"
                }
                device.pressBack()
                device.waitForIdle()
                repeat(TARGET_RESTORE_TAB_COUNT - existingTabs) { index ->
                    clickNewTab(device, expectedCount = existingTabs + index + 1)
                }
                // Home triggers MainActivity.onPause(), which requests an immediate persisted tab
                // snapshot. The wait is setup-only and keeps disk IO outside the measured cold start.
                pressHome()
                Thread.sleep(750)
            },
        ) {
            startActivityAndWait()
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = startupMetrics(),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 8,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun startupMetrics() = listOf(
        StartupTimingMetric(),
        TraceSectionMetric(
            sectionName = GECKO_RUNTIME_CREATE_TRACE,
            mode = TraceSectionMetric.Mode.First,
            label = "geckoRuntimeCreate",
        ),
        TraceSectionMetric(
            sectionName = DB_CREATE_TRACE,
            mode = TraceSectionMetric.Mode.First,
            label = "dbCreate",
        ),
        TraceSectionMetric(
            sectionName = TAB_STORE_LOAD_TRACE,
            mode = TraceSectionMetric.Mode.First,
            label = "tabStoreLoad",
        ),
        TraceSectionMetric(
            sectionName = TAB_RESTORE_MATERIALIZE_TRACE,
            mode = TraceSectionMetric.Mode.First,
            label = "tabRestoreMaterialize",
        ),
    )

    @OptIn(ExperimentalMetricApi::class)
    private fun restoreStartupMetrics() = startupMetrics() + TraceSectionMetric(
        sectionName = TAB_RESTORE_OPEN_SELECTED_TRACE,
        mode = TraceSectionMetric.Mode.First,
        label = "tabRestoreOpenSelected",
    )

    private fun clickTabSwitcher(device: UiDevice) {
        val count = topBarTabCount(device)
        val clickable = clickableAncestor(count)
        if (clickable != null) {
            clickable.click()
        } else {
            val center = count.visibleCenter
            device.click(center.x, center.y)
        }
        device.waitForIdle()
    }

    private fun clickNewTab(device: UiDevice, expectedCount: Int) {
        val count = topBarTabCount(device)
        val center = count.visibleCenter
        val verticalTolerance = count.visibleBounds.height().coerceAtLeast(1) * 2
        val addButton = checkNotNull(
            device.wait(Until.findObjects(By.pkg(TARGET_PACKAGE).clickable(true)), 3_000)
                ?.filter { candidate ->
                    val candidateCenter = candidate.visibleCenter
                    candidateCenter.x < center.x &&
                        abs(candidateCenter.y - center.y) <= verticalTolerance
                }
                ?.maxByOrNull { it.visibleCenter.x },
        ) {
            "Missing clickable new-tab control next to top-bar tab count"
        }
        addButton.click()
        device.waitForIdle()
        topBarTabCount(device, expectedCount)
    }

    private fun topBarTabCount(device: UiDevice, expectedCount: Int? = null): UiObject2 {
        val pattern = expectedCount?.let { Pattern.compile("^$it$") } ?: TOP_BAR_TAB_COUNT_TEXT
        val candidates = device.wait(
            Until.findObjects(By.pkg(TARGET_PACKAGE).text(pattern)),
            3_000,
        ).orEmpty()
        return checkNotNull(
            candidates
                .filter { it.visibleCenter.y < device.displayHeight / 4 }
                .minByOrNull { it.visibleCenter.y },
        ) {
            if (expectedCount == null) {
                "Missing numeric top-bar tab count"
            } else {
                "Top-bar tab count did not reach $expectedCount"
            }
        }
    }

    private fun clickableAncestor(node: UiObject2): UiObject2? {
        var current: UiObject2? = node
        repeat(4) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }
}
