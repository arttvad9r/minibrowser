package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AndroidComponentsExistingSessionTransferCoordinatorTest {
    @Test
    fun `successful transfer does not run terminal cleanup`() {
        val events = mutableListOf<String>()

        val result = runIrreversibleExistingSessionTransfer<String, String, String>(
            preflight = {
                events += "preflight"
                "checked"
            },
            captureAndRelinquish = {
                events += "capture:$it"
                "handoff"
            },
            prepare = { preflight, handoff ->
                events += "prepare:$preflight:$handoff"
                "prepared"
            },
            linkAndReplay = { events += "link:$it" },
            terminalCleanup = { handoff, prepared -> events += "cleanup:$handoff:$prepared" },
        )

        assertEquals("prepared", result)
        assertEquals(
            listOf(
                "preflight",
                "capture:checked",
                "prepare:checked:handoff",
                "link:prepared",
            ),
            events,
        )
    }

    @Test
    fun `preflight failure leaves cleanup to raw owner`() {
        var captured = false
        var cleaned = false
        val failure = IllegalStateException("preflight")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { throw failure },
                captureAndRelinquish = {
                    captured = true
                    "handoff"
                },
                prepare = { _, _ -> "prepared" },
                linkAndReplay = {},
                terminalCleanup = { _, _ -> cleaned = true },
            )
        }

        assertSame(failure, thrown)
        assertFalse(captured)
        assertFalse(cleaned)
    }

    @Test
    fun `capture failure does not run terminal cleanup`() {
        var cleaned = false
        val failure = IllegalStateException("capture")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { "checked" },
                captureAndRelinquish = { throw failure },
                prepare = { _, _ -> "prepared" },
                linkAndReplay = {},
                terminalCleanup = { _, _ -> cleaned = true },
            )
        }

        assertSame(failure, thrown)
        assertFalse(cleaned)
    }

    @Test
    fun `prepare failure after relinquish runs terminal cleanup without prepared session`() {
        var cleanupHandoff: String? = null
        var cleanupPrepared: String? = "unexpected"
        val failure = IllegalStateException("prepare")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { "checked" },
                captureAndRelinquish = { "handoff" },
                prepare = { _, _ -> throw failure },
                linkAndReplay = {},
                terminalCleanup = { handoff, prepared ->
                    cleanupHandoff = handoff
                    cleanupPrepared = prepared
                },
            )
        }

        assertSame(failure, thrown)
        assertEquals("handoff", cleanupHandoff)
        assertEquals(null, cleanupPrepared)
    }

    @Test
    fun `link failure after prepare runs terminal cleanup with prepared session`() {
        var cleanupPrepared: String? = null
        val failure = IllegalStateException("link")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { "checked" },
                captureAndRelinquish = { "handoff" },
                prepare = { _, _ -> "prepared" },
                linkAndReplay = { throw failure },
                terminalCleanup = { _, prepared -> cleanupPrepared = prepared },
            )
        }

        assertSame(failure, thrown)
        assertEquals("prepared", cleanupPrepared)
    }

    @Test
    fun `cleanup failure is suppressed on original transfer failure`() {
        val transferFailure = IllegalStateException("link")
        val cleanupFailure = IllegalArgumentException("cleanup")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { "checked" },
                captureAndRelinquish = { "handoff" },
                prepare = { _, _ -> "prepared" },
                linkAndReplay = { throw transferFailure },
                terminalCleanup = { _, _ -> throw cleanupFailure },
            )
        }

        assertSame(transferFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(cleanupFailure, thrown.suppressed.single())
    }

    @Test
    fun `cleanup rethrowing transfer failure does not self suppress`() {
        val transferFailure = IllegalStateException("link")

        val thrown = assertFailsWith<IllegalStateException> {
            runIrreversibleExistingSessionTransfer<String, String, String>(
                preflight = { "checked" },
                captureAndRelinquish = { "handoff" },
                prepare = { _, _ -> "prepared" },
                linkAndReplay = { throw transferFailure },
                terminalCleanup = { _, _ -> throw transferFailure },
            )
        }

        assertSame(transferFailure, thrown)
        assertEquals(0, thrown.suppressed.size)
    }

    @Test
    fun `terminal cleanup runs every step and aggregates failures`() {
        val events = mutableListOf<String>()
        val firstFailure = IllegalStateException("first")
        val secondFailure = IllegalArgumentException("second")

        val thrown = assertFailsWith<IllegalStateException> {
            runAllTerminalCleanupSteps(
                {
                    events += "first"
                    throw firstFailure
                },
                { events += "middle" },
                {
                    events += "last"
                    throw secondFailure
                },
            )
        }

        assertSame(firstFailure, thrown)
        assertEquals(listOf("first", "middle", "last"), events)
        assertEquals(1, thrown.suppressed.size)
        assertSame(secondFailure, thrown.suppressed.single())
    }
}
