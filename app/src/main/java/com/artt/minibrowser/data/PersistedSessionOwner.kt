package com.artt.minibrowser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Durable session-lifetime owner used only to decide which engine path may recreate a tab after
 * process death. It is deliberately independent from the presence of an EngineSessionState payload:
 * raw-owned shadow tabs may already carry compatible A-C restore state during migration.
 */
@Serializable
enum class PersistedSessionOwner {
    @SerialName("raw")
    Raw,

    @SerialName("android_components")
    AndroidComponents,
}
