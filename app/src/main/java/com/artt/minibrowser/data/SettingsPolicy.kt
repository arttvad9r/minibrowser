package com.artt.minibrowser.data

internal fun normalizeThemePreference(value: Int?): Int = value?.takeIf { it in 0..2 } ?: 0
