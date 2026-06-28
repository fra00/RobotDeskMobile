package com.example.mydeskrobot.integration.input

import android.util.Log
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.presentation.conversation.ConversationUiState
import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput

/**
 * Central router for all external inputs.
 * Receives raw inputs from sources, applies policy, and routes to dispatcher or queue.
 */
class InputRouter(
    private val sources: List<InputSource> = emptyList(),
    private val deferredQueue: DeferredInputQueue = DeferredInputQueue(),
    private val getUiState: () -> ConversationUiState,
) {
    private val sourceMap: Map<String, InputSource> = sources.associateBy { it.id }

    /**
     * Process an incoming raw input from a source.
     * @param sourceId The source that generated the input
     * @param raw Raw input data to be normalized by the source
     */
    fun onInput(sourceId: String, raw: Any) {
        val source = sourceMap[sourceId]
        if (source == null) {
            Log.w(TAG, "Unknown input source: $sourceId")
            return
        }

        if (!source.isEnabled()) {
            Log.d(TAG, "Source $sourceId is disabled, dropping input")
            return
        }

        val input = source.normalize(raw)
        if (input == null) {
            Log.d(TAG, "Source $sourceId could not normalize input")
            return
        }

        processInput(source, input)
    }

    /**
     * Process a pre-normalized [RobotInput] directly.
     */
    fun onInput(input: RobotInput) {
        val source = sourceMap[input.sourceId]
        if (source == null) {
            Log.w(TAG, "Unknown input source: ${input.sourceId}")
            return
        }

        if (!source.isEnabled()) {
            Log.d(TAG, "Source ${input.sourceId} is disabled, dropping input")
            return
        }

        processInput(source, input)
    }

    private fun processInput(source: InputSource, input: RobotInput) {
        if (!source.shouldAccept(input)) {
            Log.d(TAG, "Source ${source.id} rejected input")
            return
        }

        val uiState = getUiState()

        if (!InputPolicyEngine.canAcceptInput(uiState)) {
            Log.d(TAG, "Mic not active, dropping input from ${source.id}")
            return
        }

        if (InputPolicyEngine.shouldSuppressForNightMode(uiState, input.priority)) {
            Log.d(TAG, "Night mode, suppressing deferred input from ${source.id}")
            return
        }

        val envelope = source.toEnvelope(input)

        if (deferredQueue.wasRecentlySeen(envelope.dedupKey)) {
            Log.d(TAG, "Duplicate input from ${source.id}, dedupKey=${envelope.dedupKey}")
            return
        }

        if (InputPolicyEngine.canProcessNow(input.priority, uiState)) {
            Log.i(TAG, "Processing input immediately from ${source.id}")
            deferredQueue.markSeen(envelope.dedupKey)
            SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
        } else {
            Log.i(TAG, "Deferring input from ${source.id}")
            deferredQueue.enqueue(envelope)
        }
    }

    /**
     * Route a pre-built envelope (e.g. replay from deferred queue).
     */
    fun routeEnvelope(envelope: com.example.mydeskrobot.reasoning.model.SystemInputEnvelope) {
        val uiState = getUiState()

        if (!InputPolicyEngine.canAcceptInput(uiState)) {
            Log.d(TAG, "Mic not active, dropping routed envelope")
            return
        }

        if (InputPolicyEngine.shouldSuppressForNightMode(uiState, envelope.input.priority)) {
            Log.d(TAG, "Night mode, suppressing routed envelope")
            return
        }

        if (deferredQueue.wasRecentlySeen(envelope.dedupKey)) {
            Log.d(TAG, "Duplicate routed envelope, dedupKey=${envelope.dedupKey}")
            return
        }

        if (InputPolicyEngine.canProcessNow(envelope.input.priority, uiState)) {
            deferredQueue.markSeen(envelope.dedupKey)
            SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
        } else {
            deferredQueue.enqueue(envelope)
        }
    }

    /**
     * Drain deferred inputs when the robot becomes idle.
     * Called by ViewModel after completing a turn.
     * @return List of envelopes to process
     */
    fun drainDeferred(): List<com.example.mydeskrobot.reasoning.model.SystemInputEnvelope> {
        return deferredQueue.drain()
    }

    /**
     * Check if there are deferred inputs waiting.
     */
    fun hasDeferredInputs(): Boolean = !deferredQueue.isEmpty()

    /**
     * Get a source by ID.
     */
    fun getSource(id: String): InputSource? = sourceMap[id]

    /**
     * Get all registered sources.
     */
    fun getAllSources(): List<InputSource> = sources

    /**
     * Get only enabled sources.
     */
    fun getEnabledSources(): List<InputSource> = sources.filter { it.isEnabled() }

    companion object {
        private const val TAG = "InputRouter"
    }
}
