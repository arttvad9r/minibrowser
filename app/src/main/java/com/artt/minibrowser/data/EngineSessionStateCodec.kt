package com.artt.minibrowser.data

import android.util.JsonReader
import android.util.JsonWriter
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSessionState
import java.io.StringReader
import java.io.StringWriter

/**
 * Serializes Android Components session state exclusively through its public persistence contract.
 *
 * This codec is deliberately independent from TabManager. Raw GeckoSession.SessionState remains the
 * only active restore path until BrowserStore owns live EngineSessions.
 */
internal fun encodeEngineSessionStateEnvelope(
    engineName: String,
    state: EngineSessionState?,
): EngineSessionStateEnvelope? {
    val value = state ?: return null
    val output = StringWriter()
    val stateJson = runCatching {
        JsonWriter(output).use { writer ->
            value.writeTo(writer)
        }
        output.toString()
    }.getOrNull() ?: return null
    return createEngineSessionStateEnvelope(engineName, stateJson)
}

internal fun encodeEngineSessionStateEnvelope(
    engine: Engine,
    state: EngineSessionState?,
): EngineSessionStateEnvelope? = encodeEngineSessionStateEnvelope(
    engineName = engine.name(),
    state = state,
)

/**
 * Restores a compatible persisted payload without depending on an engine's private JSON format.
 */
internal fun decodeEngineSessionStateEnvelope(
    envelope: EngineSessionStateEnvelope?,
    engineName: String,
    createState: (JsonReader) -> EngineSessionState,
): EngineSessionState? {
    val stateJson = compatibleEngineSessionStateJson(envelope, engineName) ?: return null
    return runCatching {
        JsonReader(StringReader(stateJson)).use { reader -> createState(reader) }
    }.getOrNull()
}

internal fun decodeEngineSessionStateEnvelope(
    envelope: EngineSessionStateEnvelope?,
    engine: Engine,
): EngineSessionState? = decodeEngineSessionStateEnvelope(
    envelope = envelope,
    engineName = engine.name(),
    createState = { reader -> engine.createSessionStateFrom(reader) },
)

/**
 * Restores only state explicitly bound to the current tab URL.
 *
 * Persisted engine state is opaque and may describe a different history entry than the surrounding
 * tab metadata. Fail closed on missing or stale URL bindings, matching the legacy Gecko restore
 * contract, before invoking any engine parser.
 */
internal fun decodeBoundEngineSessionStateEnvelope(
    envelope: EngineSessionStateEnvelope?,
    stateUrl: String?,
    tabUrl: String,
    engineName: String,
    createState: (JsonReader) -> EngineSessionState,
): EngineSessionState? {
    if (stateUrl == null || stateUrl != tabUrl) return null
    return decodeEngineSessionStateEnvelope(
        envelope = envelope,
        engineName = engineName,
        createState = createState,
    )
}

internal fun decodeBoundEngineSessionStateEnvelope(
    envelope: EngineSessionStateEnvelope?,
    stateUrl: String?,
    tabUrl: String,
    engine: Engine,
): EngineSessionState? = decodeBoundEngineSessionStateEnvelope(
    envelope = envelope,
    stateUrl = stateUrl,
    tabUrl = tabUrl,
    engineName = engine.name(),
    createState = { reader -> engine.createSessionStateFrom(reader) },
)
