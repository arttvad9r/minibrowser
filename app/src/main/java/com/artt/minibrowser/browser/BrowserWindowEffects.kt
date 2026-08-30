package com.artt.minibrowser.browser

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Owns browser-specific mutations of the host Activity window. */
internal class BrowserWindowController(
    private val window: Window,
) {
    fun setDarkTheme(darkTheme: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    fun setPrivateMode(isPrivate: Boolean) {
        if (isPrivate) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun setFullscreen(inFullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (inFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Applies window side effects from browser UI state outside the root renderer. */
@Composable
internal fun BrowserWindowEffects(
    controller: BrowserWindowController,
    darkTheme: Boolean,
    isPrivate: Boolean,
    inFullscreen: Boolean,
) {
    LaunchedEffect(controller, darkTheme) {
        controller.setDarkTheme(darkTheme)
    }
    LaunchedEffect(controller, isPrivate) {
        controller.setPrivateMode(isPrivate)
    }
    LaunchedEffect(controller, inFullscreen) {
        controller.setFullscreen(inFullscreen)
    }
}
