package com.artt.minibrowser.engine

/**
 * Resolves which browser-session abstraction may receive commands for a tab.
 *
 * Raw ownership and linked EngineSession command ownership are mutually exclusive. Once raw
 * ownership is relinquished, commands must never fall back to the raw session while the linked
 * EngineSession is still being attached. A process-restored A-C-owned tab has no raw session at all.
 */
internal sealed interface BrowserCommandTarget<out RawSession : Any, out LinkedSession : Any> {
    data class Raw<RawSession : Any>(
        val session: RawSession,
    ) : BrowserCommandTarget<RawSession, Nothing>

    data class Linked<LinkedSession : Any>(
        val session: LinkedSession,
    ) : BrowserCommandTarget<Nothing, LinkedSession>
}

internal fun <RawSession : Any, LinkedSession : Any> resolveBrowserCommandTarget(
    ownership: RawSessionOwnership,
    rawSession: RawSession?,
    linkedEngineSession: LinkedSession?,
): BrowserCommandTarget<RawSession, LinkedSession>? = when (ownership) {
    RawSessionOwnership.Owned -> {
        check(linkedEngineSession == null) {
            "Raw-owned tab must not also expose a linked EngineSession command target"
        }
        BrowserCommandTarget.Raw(
            checkNotNull(rawSession) {
                "Raw-owned tab must expose a raw session command target"
            },
        )
    }

    RawSessionOwnership.Relinquished -> linkedEngineSession?.let { session ->
        BrowserCommandTarget.Linked(session)
    }
}
