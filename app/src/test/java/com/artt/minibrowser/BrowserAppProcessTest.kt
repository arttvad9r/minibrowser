package com.artt.minibrowser

import com.artt.minibrowser.engine.isMainApplicationProcess
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserAppProcessTest {
    @Test fun mainProcessIsAccepted() {
        assertTrue(isMainApplicationProcess("com.artt.minibrowser", "com.artt.minibrowser"))
    }

    @Test fun geckoChildProcessIsRejected() {
        assertFalse(isMainApplicationProcess("com.artt.minibrowser:tab", "com.artt.minibrowser"))
        assertFalse(isMainApplicationProcess("com.artt.minibrowser:gpu", "com.artt.minibrowser"))
    }

    @Test fun unknownProcessNameFallsBackToMainForCompatibility() {
        assertTrue(isMainApplicationProcess(null, "com.artt.minibrowser"))
    }
}
