package com.artt.minibrowser.browser

internal const val ACTION_NEW_TAB = "com.artt.minibrowser.action.NEW_TAB"
internal const val ACTION_NEW_PRIVATE_TAB = "com.artt.minibrowser.action.NEW_PRIVATE_TAB"

internal enum class BrowserShortcut {
    NewTab,
    NewPrivateTab,
}

internal fun browserShortcutForAction(action: String?): BrowserShortcut? = when (action) {
    ACTION_NEW_TAB -> BrowserShortcut.NewTab
    ACTION_NEW_PRIVATE_TAB -> BrowserShortcut.NewPrivateTab
    else -> null
}
