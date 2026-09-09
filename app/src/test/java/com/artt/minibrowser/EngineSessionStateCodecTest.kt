package com.artt.minibrowser

import android.app.Application
import android.util.JsonReader
import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.decodeEngineSessionStateEnvelope
import com.artt.minibrowser.data.encodeEngineSessionStateEnvelope
import mozilla.components.concept.engine.EngineSessionState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class EngineSessionStateCodecTest {
    private data class TestState(val value: String) : EngineSessionState {
        override fun writeTo(writer: JsonWriter) {
            writer.beginObject()
            writer.name("value").value(value)
            writer.endObject()
        }
    }

    @Test
    fun roundTripsThroughPublicEngineStateContracts() {
        val envelope = encodeEngineSessionStateEnvelope(
            engineName = "gecko",
            state = TestState("opaque-state"),
        )

        assertEquals(
            EngineSessionStateEnvelope(
                engine = "gecko",
                stateJson = "{\"value\":\"opaque-state\"}",
            ),
            envelope,
        )

        val restored = decodeEngineSessionStateEnvelope(
            envelope = envelope,
            engineName = "gecko",
        ) { reader ->
            reader.beginObject()
            assertEquals("value", reader.nextName())
            val value = reader.nextString()
            reader.endObject()
            TestState(value)
        }

        assertEquals(TestState("opaque-state"), restored)
    }

    @Test
    fun incompatibleEnvelopeDoesNotInvokeEngineParser() {
        var invoked = false
        val envelope = EngineSessionStateEnvelope(
            engine = "system",
            stateJson = "{\"value\":\"state\"}",
        )

        val restored = decodeEngineSessionStateEnvelope(
            envelope = envelope,
            engineName = "gecko",
        ) {
            invoked = true
            TestState("unexpected")
        }

        assertNull(restored)
        assertFalse(invoked)
    }

    @Test
    fun serializationFailureOrNonObjectPayloadIsRejected() {
        val throwingState = object : EngineSessionState {
            override fun writeTo(writer: JsonWriter) {
                error("cannot serialize")
            }
        }
        val arrayState = object : EngineSessionState {
            override fun writeTo(writer: JsonWriter) {
                writer.beginArray()
                writer.value("state")
                writer.endArray()
            }
        }

        assertNull(encodeEngineSessionStateEnvelope("gecko", throwingState))
        assertNull(encodeEngineSessionStateEnvelope("gecko", arrayState))
        assertNull(encodeEngineSessionStateEnvelope("gecko", null))
    }

    @Test
    fun engineParserFailureIsRejected() {
        val envelope = EngineSessionStateEnvelope(
            engine = "gecko",
            stateJson = "{\"value\":\"state\"}",
        )

        val restored = decodeEngineSessionStateEnvelope(
            envelope = envelope,
            engineName = "gecko",
        ) { _: JsonReader ->
            error("cannot restore")
        }

        assertNull(restored)
    }
}
