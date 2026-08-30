package com.artt.minibrowser

import com.artt.minibrowser.ui.predictiveBackReveal
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserMotionScreenTest {
    @Test
    fun predictiveBackMapsProgressToReveal() {
        assertEquals(1f, predictiveBackReveal(0f))
        assertEquals(0.5f, predictiveBackReveal(0.5f))
        assertEquals(0f, predictiveBackReveal(1f))
    }

    @Test
    fun predictiveBackProgressIsClamped() {
        assertEquals(1f, predictiveBackReveal(-1f))
        assertEquals(0f, predictiveBackReveal(2f))
    }
}
