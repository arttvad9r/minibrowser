package com.artt.minibrowser.ui

import kotlin.math.abs

/**
 * Small UI-independent state machine for browser pull-to-refresh.
 *
 * Gecko decides whether the touched scroll container permits browser overscroll. This tracker only
 * turns an eligible, mostly-vertical downward drag into progress and a single refresh decision.
 * Gesture direction is decided once it crosses touch slop: a pan that starts by scrolling content
 * or moving sideways can never turn into pull-to-refresh midway through the same touch sequence.
 */
internal class PullRefreshGestureTracker(
    private val touchSlopPx: Float,
    private val triggerDistancePx: Float,
) {
    init {
        require(touchSlopPx >= 0f)
        require(triggerDistancePx > 0f)
    }

    private var startX = 0f
    private var startY = 0f
    private var pageEligible = false
    private var geckoEligible = false
    private var directionAccepted = false
    private var directionRejected = false
    private var progress = 0f

    fun onDown(x: Float, y: Float, pageEligible: Boolean) {
        startX = x
        startY = y
        this.pageEligible = pageEligible
        geckoEligible = false
        directionAccepted = false
        directionRejected = false
        progress = 0f
    }

    fun setGeckoEligible(eligible: Boolean) {
        geckoEligible = eligible
        if (!eligible) progress = 0f
    }

    fun onMove(x: Float, y: Float): Float {
        if (!pageEligible || directionRejected) {
            progress = 0f
            return 0f
        }

        val dx = abs(x - startX)
        val dy = y - startY
        if (!directionAccepted) {
            val crossedSlop = maxOf(dx, abs(dy)) > touchSlopPx
            if (!crossedSlop) {
                progress = 0f
                return 0f
            }
            if (dy > touchSlopPx && dy > dx) {
                directionAccepted = true
            } else {
                directionRejected = true
                progress = 0f
                return 0f
            }
        }

        if (!geckoEligible || dy <= touchSlopPx) {
            progress = 0f
            return 0f
        }

        progress = ((dy - touchSlopPx) / triggerDistancePx).coerceIn(0f, 1f)
        return progress
    }

    fun finish(commit: Boolean): Boolean {
        val shouldRefresh = commit && pageEligible && geckoEligible && directionAccepted && progress >= 1f
        reset()
        return shouldRefresh
    }

    fun cancel() {
        reset()
    }

    private fun reset() {
        pageEligible = false
        geckoEligible = false
        directionAccepted = false
        directionRejected = false
        progress = 0f
    }
}
