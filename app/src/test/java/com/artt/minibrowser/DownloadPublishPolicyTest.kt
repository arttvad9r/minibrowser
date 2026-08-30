package com.artt.minibrowser

import com.artt.minibrowser.engine.isMediaStorePublishSuccessful
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadPublishPolicyTest {
    @Test
    fun requiresAtLeastOneUpdatedMediaStoreRow() {
        assertFalse(isMediaStorePublishSuccessful(0))
        assertFalse(isMediaStorePublishSuccessful(-1))
        assertTrue(isMediaStorePublishSuccessful(1))
        assertTrue(isMediaStorePublishSuccessful(2))
    }
}
