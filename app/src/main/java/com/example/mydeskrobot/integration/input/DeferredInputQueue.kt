package com.example.mydeskrobot.integration.input

import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Queue for deferred system inputs that couldn't be processed immediately.
 * Handles deduplication and TTL expiration.
 */
class DeferredInputQueue(
    private val maxSize: Int = 20,
    private val dedupTtlMs: Long = 5 * 60 * 1000, // 5 minutes
) {
    private val queue = ConcurrentLinkedDeque<QueuedInput>()
    private val seenKeys = ConcurrentHashMap<String, Long>()
    private val _items = MutableStateFlow<List<DeferredQueueItem>>(emptyList())
    val items: StateFlow<List<DeferredQueueItem>> = _items.asStateFlow()

    /**
     * Add an input to the queue if not a duplicate.
     * @return true if enqueued, false if duplicate or queue full
     */
    fun enqueue(envelope: SystemInputEnvelope): Boolean {
        val now = System.currentTimeMillis()
        cleanupExpiredKeys(now)

        val key = envelope.dedupKey
        val lastSeen = seenKeys[key]
        if (lastSeen != null && now - lastSeen < dedupTtlMs) {
            return false
        }

        if (queue.size >= maxSize) {
            queue.pollFirst()
        }

        seenKeys[key] = now
        queue.addLast(QueuedInput(envelope, now))
        publishSnapshot()
        return true
    }

    /**
     * Drain all queued inputs that haven't expired.
     * @return List of envelopes to process, oldest first
     */
    fun drain(): List<SystemInputEnvelope> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<SystemInputEnvelope>()

        while (queue.isNotEmpty()) {
            val item = queue.pollFirst() ?: break
            if (now - item.enqueuedAt < dedupTtlMs) {
                result.add(item.envelope)
            }
        }

        publishSnapshot()
        return result
    }

    /**
     * Peek at the next input without removing it.
     */
    fun peek(): SystemInputEnvelope? {
        return queue.peekFirst()?.envelope
    }

    /**
     * Check if the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()

    /**
     * Current queue size.
     */
    fun size(): Int = queue.size

    /**
     * Clear all queued inputs.
     */
    fun clear() {
        queue.clear()
        seenKeys.clear()
        publishSnapshot()
    }

    /**
     * Removes a queued input without processing it (user dismissed from inbox).
     */
    fun removeByDedupKey(dedupKey: String): Boolean {
        val removed = queue.removeIf { it.envelope.dedupKey == dedupKey }
        if (removed) {
            seenKeys.remove(dedupKey)
            publishSnapshot()
        }
        return removed
    }

    fun snapshot(): List<DeferredQueueItem> = queue.map { DeferredQueueItem(it.envelope, it.enqueuedAt) }

    /**
     * Check if a key was recently seen (for external dedup checks).
     */
    fun wasRecentlySeen(key: String): Boolean {
        val now = System.currentTimeMillis()
        cleanupExpiredKeys(now)
        val lastSeen = seenKeys[key] ?: return false
        return now - lastSeen < dedupTtlMs
    }

    /**
     * Mark a key as seen (for external dedup).
     */
    fun markSeen(key: String) {
        seenKeys[key] = System.currentTimeMillis()
    }

    private fun cleanupExpiredKeys(now: Long) {
        seenKeys.entries.removeIf { now - it.value >= dedupTtlMs }
    }

    private fun publishSnapshot() {
        _items.value = snapshot()
    }

    private data class QueuedInput(
        val envelope: SystemInputEnvelope,
        val enqueuedAt: Long,
    )
}

data class DeferredQueueItem(
    val envelope: SystemInputEnvelope,
    val enqueuedAt: Long,
)
