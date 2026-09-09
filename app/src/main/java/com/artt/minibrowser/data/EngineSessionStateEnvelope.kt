package com.artt.minibrowser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal const val ENGINE_SESSION_STATE_ENVELOPE_VERSION = 1

/**
 * Versioned, engine-qualified container for Android Components EngineSessionState JSON.
 *
 * This deliberately lives beside the legacy raw Gecko session-state persistence instead of
 * overloading it. A-C exposes EngineSessionState.writeTo(JsonWriter) for serialization and
 * Engine.createSessionStateFrom(...) for restore; the payload is engine-specific, so the engine
 * name is persisted with it and must match before restore.
 */
@Serializable
data class EngineSessionStateEnvelope(
    val version: Int = ENGINE_SESSION_STATE_ENVELOPE_VERSION,
    val engine: String,
    val stateJson: String,
)

private val engineStateJson = Json { ignoreUnknownKeys = true }

internal fun createEngineSessionStateEnvelope(
    engineName: String,
    stateJson: String,
): EngineSessionStateEnvelope? {
    if (engineName.isBlank() || !isJsonObject(stateJson)) return null
    return EngineSessionStateEnvelope(
        engine = engineName,
        stateJson = stateJson,
    )
}

internal fun compatibleEngineSessionStateJson(
    envelope: EngineSessionStateEnvelope?,
    engineName: String,
): String? {
    val value = envelope ?: return null
    if (value.version != ENGINE_SESSION_STATE_ENVELOPE_VERSION) return null
    if (engineName.isBlank() || value.engine != engineName) return null
    return value.stateJson.takeIf(::isJsonObject)
}

private fun isJsonObject(value: String): Boolean =
    runCatching { engineStateJson.parseToJsonElement(value) is JsonObject }.getOrDefault(false)
