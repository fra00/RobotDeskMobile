package com.example.mydeskrobot.integration.input

import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeferredInputQueueTest {

    private lateinit var queue: DeferredInputQueue

    @Before
    fun setUp() {
        queue = DeferredInputQueue(maxSize = 5, dedupTtlMs = 60_000)
    }

    @Test
    fun `enqueue adds item to queue`() {
        val envelope = createTestEnvelope("test-1")
        assertTrue(queue.enqueue(envelope))
        assertEquals(1, queue.size())
        assertFalse(queue.isEmpty())
    }

    @Test
    fun `enqueue rejects duplicate within TTL`() {
        val envelope1 = createTestEnvelope("test-1")
        val envelope2 = createTestEnvelope("test-1")

        assertTrue(queue.enqueue(envelope1))
        assertFalse(queue.enqueue(envelope2))
        assertEquals(1, queue.size())
    }

    @Test
    fun `enqueue accepts different keys`() {
        val envelope1 = createTestEnvelope("test-1")
        val envelope2 = createTestEnvelope("test-2")

        assertTrue(queue.enqueue(envelope1))
        assertTrue(queue.enqueue(envelope2))
        assertEquals(2, queue.size())
    }

    @Test
    fun `drain returns all items and clears queue`() {
        queue.enqueue(createTestEnvelope("test-1"))
        queue.enqueue(createTestEnvelope("test-2"))
        queue.enqueue(createTestEnvelope("test-3"))

        val drained = queue.drain()
        assertEquals(3, drained.size)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `drain returns items in FIFO order`() {
        queue.enqueue(createTestEnvelope("first"))
        queue.enqueue(createTestEnvelope("second"))
        queue.enqueue(createTestEnvelope("third"))

        val drained = queue.drain()
        assertEquals("first", drained[0].dedupKey)
        assertEquals("second", drained[1].dedupKey)
        assertEquals("third", drained[2].dedupKey)
    }

    @Test
    fun `enqueue evicts oldest when max size reached`() {
        repeat(6) { i ->
            queue.enqueue(createTestEnvelope("item-$i"))
        }

        assertEquals(5, queue.size())

        val drained = queue.drain()
        assertEquals("item-1", drained[0].dedupKey)
        assertEquals("item-5", drained[4].dedupKey)
    }

    @Test
    fun `peek returns first item without removing`() {
        queue.enqueue(createTestEnvelope("first"))
        queue.enqueue(createTestEnvelope("second"))

        assertEquals("first", queue.peek()?.dedupKey)
        assertEquals(2, queue.size())
    }

    @Test
    fun `clear removes all items`() {
        queue.enqueue(createTestEnvelope("test-1"))
        queue.enqueue(createTestEnvelope("test-2"))

        queue.clear()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
    }

    @Test
    fun `wasRecentlySeen returns true for recently enqueued key`() {
        val envelope = createTestEnvelope("test-key")
        queue.enqueue(envelope)

        assertTrue(queue.wasRecentlySeen("test-key"))
    }

    @Test
    fun `wasRecentlySeen returns false for unknown key`() {
        assertFalse(queue.wasRecentlySeen("unknown-key"))
    }

    @Test
    fun `removeByDedupKey removes item without draining others`() {
        queue.enqueue(createTestEnvelope("keep"))
        queue.enqueue(createTestEnvelope("remove-me"))

        assertTrue(queue.removeByDedupKey("remove-me"))
        assertEquals(1, queue.size())
        assertEquals("keep", queue.peek()?.dedupKey)
    }

    @Test
    fun `markSeen makes key appear as recently seen`() {
        assertFalse(queue.wasRecentlySeen("manual-key"))

        queue.markSeen("manual-key")

        assertTrue(queue.wasRecentlySeen("manual-key"))
    }

    private fun createTestEnvelope(key: String): SystemInputEnvelope {
        val input = RobotInput.Notification(
            packageName = "com.test",
            appLabel = "Test App",
            title = "Test",
            text = "Test message",
            notificationKey = key,
            timestamp = System.currentTimeMillis(),
        )
        return SystemInputEnvelope(
            input = input,
            formattedForLlm = "[SYSTEM_INPUT: notification]\nTest",
            dedupKey = key,
        )
    }
}
