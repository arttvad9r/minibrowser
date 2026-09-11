package com.artt.minibrowser.engine

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.InputStream
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AndroidComponentsDownloadCompatibilityDelegateTest {
    @Test
    fun externalResponseForwardsToStockAndroidComponentsDelegate() {
        val session = GeckoSession()
        val compatibility = AndroidComponentsGeckoCompatibilityRegistry().forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = true),
        )
        var stockCalls = 0
        val stock = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                stockCalls++
            }
        }
        val delegate = AndroidComponentsContentCompatibilityDelegate(stock, compatibility)
        var bodyClosed = false
        val body = object : InputStream() {
            override fun read(): Int = -1

            override fun close() {
                bodyClosed = true
            }
        }
        val response = WebResponse.Builder("https://example.test/file.bin")
            .body(body)
            .build()

        delegate.onExternalResponse(session, response)

        assertEquals(1, stockCalls)
        assertFalse(bodyClosed)
        body.close()
    }
}
