package com.artt.minibrowser

import com.artt.minibrowser.engine.ProgressGate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressGateTest {
    @Test
    fun suppressesRapidProgressButPublishesTerminalValue() {
        val gate = ProgressGate(intervalMs = 100)

        assertTrue(gate.accept(nowMs = 0, progress = 5))
        assertFalse(gate.accept(nowMs = 20, progress = 30))
        assertTrue(gate.accept(nowMs = 100, progress = 60))
        assertTrue(gate.accept(nowMs = 110, progress = 100))
    }
}
