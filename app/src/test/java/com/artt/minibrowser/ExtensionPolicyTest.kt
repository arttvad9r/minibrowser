package com.artt.minibrowser

import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.ExtensionLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionPolicyTest {
    @Test fun privateBrowsingPolicyIsExplicit() {
        assertEquals(true, ExtensionLoader.privateAllowedInPrivate(ExtensionLoader.UBLOCK_ID))
        assertEquals(false, ExtensionLoader.privateAllowedInPrivate(ExtensionLoader.VOT_ID))
        assertEquals(false, ExtensionLoader.privateAllowedInPrivate("unknown"))
    }

    @Test fun votIsEnabledByDefault() {
        assertEquals(true, Prefs().votEnabled)
    }
}
