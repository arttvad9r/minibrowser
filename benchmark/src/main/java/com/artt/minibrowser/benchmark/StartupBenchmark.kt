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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val TARGET_PACKAGE = "com.artt.minibrowser"
private const val GECKO_RUNTIME_CREATE_TRACE = "GeckoRuntime.create"
private const val TAB_STORE_LOAD_TRACE = "TabStore.loadState"
private const val TAB_RESTORE_MATERIALIZE_TRACE = "TabManager.restoreTabs"
private const val TAB_RESTORE_OPEN_SELECTED_TRACE = "TabManager.openSelected"

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

    @OptIn(ExperimentalMetricApi::class)
    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                TraceSectionMetric(
                    sectionName = GECKO_RUNTIME_CREATE_TRACE,
                    mode = TraceSectionMetric.Mode.First,
                    label = "geckoRuntimeCreate",
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
                TraceSectionMetric(
                    sectionName = TAB_RESTORE_OPEN_SELECTED_TRACE,
                    mode = TraceSectionMetric.Mode.First,
                    label = "tabRestoreOpenSelected",
                ),
            ),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 8,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }
}
