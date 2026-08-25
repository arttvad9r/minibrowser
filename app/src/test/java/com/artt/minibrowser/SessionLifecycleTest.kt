package com.artt.minibrowser

import com.artt.minibrowser.engine.shouldCloseSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionLifecycleTest {
    @Test
    fun closingUnopenedSessionIsSafe() {
        assertFalse(shouldCloseSession(false))
        assertTrue(shouldCloseSession(true))
    }
}
