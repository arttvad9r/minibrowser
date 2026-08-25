package com.artt.minibrowser

import com.artt.minibrowser.engine.faviconSampleSize
import com.artt.minibrowser.engine.isValidFaviconDimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaviconSizingTest {
    @Test fun sampleSizeKeepsSmallImagesUnscaled() {
        assertEquals(1, faviconSampleSize(32, 32, 128))
    }

    @Test fun sampleSizeReducesLargeImages() {
        assertTrue(faviconSampleSize(256, 256, 128) >= 2)
        assertTrue(faviconSampleSize(1024, 1024, 128) >= 8)
    }

    @Test fun dimensionsAndPixelsAreBounded() {
        assertTrue(isValidFaviconDimensions(4000, 4000))
        assertEquals(false, isValidFaviconDimensions(4097, 1))
        assertEquals(false, isValidFaviconDimensions(4096, 4096 * 2))
        assertEquals(false, isValidFaviconDimensions(0, 32))
    }
}
