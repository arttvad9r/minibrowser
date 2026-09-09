package com.artt.minibrowser.engine

/**
 * Raw GeckoSession lifetime authority for a MiniBrowser tab.
 *
 * The transition is intentionally one-way. [Relinquished] describes the interval beginning at the
 * exact raw-owner handoff, including fail-closed transfer setup before BrowserStore has necessarily
 * finished linking the EngineSession. Raw close/replace/content-mirroring authority must never be
 * restored after this point.
 */
internal enum class RawSessionOwnership {
    Owned,
    Relinquished,
}

internal val RawSessionOwnership.mirrorsRawContent: Boolean
    get() = this == RawSessionOwnership.Owned

/**
 * Validates the irreversible raw-session ownership boundary without depending on GeckoView in JVM
 * tests. Reference identity is required: an ownership token for one GeckoSession must never be used
 * to relinquish a replacement or otherwise different session object.
 */
internal fun <T : Any> rawSessionOwnershipAfterRelinquish(
    current: RawSessionOwnership,
    actualSession: T,
    expectedSession: T,
): RawSessionOwnership {
    check(current == RawSessionOwnership.Owned) {
        "Raw GeckoSession ownership has already been relinquished"
    }
    check(actualSession === expectedSession) {
        "Raw GeckoSession identity changed before ownership relinquish"
    }
    return RawSessionOwnership.Relinquished
}
