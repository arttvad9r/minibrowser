package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.parseDownloadHistoryJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadHistoryJsonTest {
    @Test
    fun malformedAndDuplicateEntriesAreSkippedAndFlagRewrite() {
        val parsed = parseDownloadHistoryJson(
            """
            [
              {"id":"good","name":"good.bin","sourceUrl":"https://example.com","status":"Completed","startedAt":1},
              "not-an-object",
              {"name":"missing-id","status":"Completed","startedAt":2},
              {"id":"good","name":"duplicate.bin","sourceUrl":"https://example.com","status":"Completed","startedAt":3},
              {"id":"second","name":"second.bin","sourceUrl":"https://example.org","status":"Failed","startedAt":4,"failureReason":"SaveFailed"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("good", "second"), parsed.items.map { it.id })
        assertTrue(parsed.needsRewrite)
    }

    @Test
    fun oversizedArrayIsBoundedAndFlaggedForRewrite() {
        val parsed = parseDownloadHistoryJson(
            """
            [
              {"id":"one","name":"1.bin","status":"Completed","startedAt":1},
              {"id":"two","name":"2.bin","status":"Completed","startedAt":2},
              {"id":"three","name":"3.bin","status":"Completed","startedAt":3}
            ]
            """.trimIndent(),
            limit = 2,
        )

        assertEquals(listOf("one", "two"), parsed.items.map { it.id })
        assertTrue(parsed.needsRewrite)
    }

    @Test
    fun settledValidArrayDoesNotRequestRewrite() {
        val parsed = parseDownloadHistoryJson(
            """
            [
              {"id":"done","name":"done.bin","sourceUrl":"https://example.com","mime":"application/octet-stream","status":"Completed","startedAt":1,"finishedAt":2,"bytes":10,"location":"/tmp/done.bin"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("done"), parsed.items.map { it.id })
        assertFalse(parsed.needsRewrite)
    }
}
