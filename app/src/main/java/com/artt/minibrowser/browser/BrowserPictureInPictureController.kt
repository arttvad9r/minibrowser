package com.artt.minibrowser.browser

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity

internal data class BrowserPictureInPictureMediaState(
    val fullscreenVideo: Boolean = false,
    val playing: Boolean = false,
    val videoWidth: Long = 0L,
    val videoHeight: Long = 0L,
    val privateTab: Boolean = false,
) {
    val canEnter: Boolean get() = fullscreenVideo && !privateTab
    val canAutoEnter: Boolean get() = canEnter && playing
}

internal data class PictureInPictureAspectRatio(
    val numerator: Int,
    val denominator: Int,
)

internal fun calculatePictureInPictureAspectRatio(
    width: Long,
    height: Long,
): PictureInPictureAspectRatio {
    if (width <= 0L || height <= 0L) return PictureInPictureAspectRatio(16, 9)

    val ratio = width.toDouble() / height.toDouble()
    val maxRatio = 2.39
    val minRatio = 1.0 / maxRatio
    if (ratio >= maxRatio) return PictureInPictureAspectRatio(239, 100)
    if (ratio <= minRatio) return PictureInPictureAspectRatio(100, 239)

    val divisor = greatestCommonDivisor(width, height)
    val reducedWidth = width / divisor
    val reducedHeight = height / divisor
    if (reducedWidth <= Int.MAX_VALUE && reducedHeight <= Int.MAX_VALUE) {
        return PictureInPictureAspectRatio(reducedWidth.toInt(), reducedHeight.toInt())
    }

    val denominator = 10_000
    val numerator = (ratio * denominator).toInt().coerceAtLeast(1)
    val fallbackDivisor = greatestCommonDivisor(numerator.toLong(), denominator.toLong()).toInt()
    return PictureInPictureAspectRatio(
        numerator = numerator / fallbackDivisor,
        denominator = denominator / fallbackDivisor,
    )
}

private tailrec fun greatestCommonDivisor(a: Long, b: Long): Long =
    if (b == 0L) a else greatestCommonDivisor(b, a % b)

/** Owns platform PiP parameters. Gecko media state is supplied by BrowserPictureInPictureEffect. */
internal class BrowserPictureInPictureController(
    private val activity: ComponentActivity,
) {
    private var mediaState = BrowserPictureInPictureMediaState()

    val supported: Boolean by lazy {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun update(state: BrowserPictureInPictureMediaState) {
        mediaState = state
        if (!supported) return

        val params = buildParams(
            autoEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && state.canAutoEnter,
        )
        activity.setPictureInPictureParams(params)
    }

    /** Explicit/system request path used on Android 11 and older. Android 12+ uses auto-enter. */
    fun enterIfEligible(): Boolean {
        if (!supported || !mediaState.canAutoEnter) return false
        return runCatching {
            activity.enterPictureInPictureMode(buildParams(autoEnter = false))
        }.getOrDefault(false)
    }

    private fun buildParams(autoEnter: Boolean): PictureInPictureParams {
        val aspect = calculatePictureInPictureAspectRatio(mediaState.videoWidth, mediaState.videoHeight)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(aspect.numerator, aspect.denominator))

        val sourceRect = Rect()
        if (activity.window.decorView.getGlobalVisibleRect(sourceRect) && !sourceRect.isEmpty) {
            builder.setSourceRectHint(sourceRect)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                .setAutoEnterEnabled(autoEnter)
                .setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
