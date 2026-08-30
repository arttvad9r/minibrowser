package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoSessionSettings

/** Applies the user-requested desktop/mobile presentation policy outside Compose rendering. */
internal fun toggleDesktopMode(tab: Tab) {
    tab.desktop = !tab.desktop
    tab.session.settings.userAgentMode =
        if (tab.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
    tab.session.settings.viewportMode =
        if (tab.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    tab.session.reload()
}
