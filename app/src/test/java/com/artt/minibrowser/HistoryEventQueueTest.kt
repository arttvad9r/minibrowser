package com.artt.minibrowser

import com.artt.minibrowser.data.LosslessBoundedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HistoryEventQueueTest {
    @Test
    fun overflowBackpressuresInsteadOfDroppingAndPreservesFifoOrder() = runBlocking {
        val queue = LosslessBoundedQueue<Int>(capacity = 2)
        val bufferFilled = CountDownLatch(1)
        val producerFinished = CountDownLatch(1)
        val producer = Thread {
            queue.offer(1)
            queue.offer(2)
            bufferFilled.countDown()
            queue.offer(3)
            producerFinished.countDown()
        }

        producer.start()
        assertTrue(bufferFilled.await(2, TimeUnit.SECONDS))
        assertFalse(producerFinished.await(100, TimeUnit.MILLISECONDS))

        assertEquals(1, queue.receive())
        assertTrue(producerFinished.await(2, TimeUnit.SECONDS))
        assertEquals(2, queue.receive())
        assertEquals(3, queue.receive())
        producer.join()
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        val failure = runCatching { LosslessBoundedQueue<Int>(capacity = 0) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
