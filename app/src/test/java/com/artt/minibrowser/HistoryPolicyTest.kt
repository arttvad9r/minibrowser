package com.artt.minibrowser

import com.artt.minibrowser.data.isHistoryUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPolicyTest {
    @Test fun keepsNormalWebPages() {
        assertTrue(isHistoryUrl("https://drive.google.com/drive/my-drive"))
        assertTrue(isHistoryUrl("http://localhost:8080/test"))
    }

    @Test fun dropsInternalAndNonWebPages() {
        assertFalse(isHistoryUrl("about:blank"))
        assertFalse(isHistoryUrl("about:config"))
        assertFalse(isHistoryUrl("file:///tmp/test"))
        assertFalse(isHistoryUrl("data:text/plain,test"))
        assertFalse(isHistoryUrl(""))
    }
}
