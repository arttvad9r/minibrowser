package com.artt.minibrowser

import com.artt.minibrowser.data.normalizeThemePreference
import com.artt.minibrowser.engine.normalizeTranslationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPolicyTest {
    @Test fun themePreferenceAcceptsOnlyKnownModes() {
        assertEquals(0, normalizeThemePreference(null))
        assertEquals(0, normalizeThemePreference(-1))
        assertEquals(0, normalizeThemePreference(0))
        assertEquals(1, normalizeThemePreference(1))
        assertEquals(2, normalizeThemePreference(2))
        assertEquals(0, normalizeThemePreference(99))
    }

    @Test fun translationTargetIsNormalizedAndWhitelisted() {
        assertEquals("ru", normalizeTranslationTarget("ru"))
        assertEquals("en", normalizeTranslationTarget(" EN "))
        assertEquals("de", normalizeTranslationTarget("De"))
        assertEquals("fr", normalizeTranslationTarget("fr"))
        assertNull(normalizeTranslationTarget(null))
        assertNull(normalizeTranslationTarget("es"))
        assertNull(normalizeTranslationTarget("ru&x=1"))
    }
}
