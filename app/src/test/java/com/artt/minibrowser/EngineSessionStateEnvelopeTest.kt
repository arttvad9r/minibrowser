package com.artt.minibrowser

import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.compatibleEngineSessionStateJson
import com.artt.minibrowser.data.createEngineSessionStateEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineSessionStateEnvelopeTest {
    @Test
    fun createsEnvelopeOnlyForJsonObjectState() {
        assertEquals(
            EngineSessionStateEnvelope(
                engine = "gecko",
                stateJson = "{\"history\":[]}",
            ),
            createEngineSessionStateEnvelope("gecko", "{\"history\":[]}"),
        )

        assertNull(createEngineSessionStateEnvelope("", "{}"))
        assertNull(createEngineSessionStateEnvelope("gecko", "[]"))
        assertNull(createEngineSessionStateEnvelope("gecko", "not-json"))
    }

    @Test
    fun restoresOnlyMatchingKnownEnvelope() {
        val current = EngineSessionStateEnvelope(
            engine = "gecko",
            stateJson = "{\"history\":[1]}",
        )
        assertEquals(
            "{\"history\":[1]}",
            compatibleEngineSessionStateJson(current, "gecko"),
        )
        assertNull(compatibleEngineSessionStateJson(current, "system"))
        assertNull(compatibleEngineSessionStateJson(current, ""))
        assertNull(
            compatibleEngineSessionStateJson(
                current.copy(version = 2),
                "gecko",
            ),
        )
        assertNull(
            compatibleEngineSessionStateJson(
                current.copy(stateJson = "null"),
                "gecko",
            ),
        )
    }
}
