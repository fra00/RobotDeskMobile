package com.example.mydeskrobot.domain.awareness

/**
 * Tracks and infers user's mental state from interactions.
 * Provides methods to analyze text for mood indicators.
 */
object UserStateTracker {

    private val BUSY_KEYWORDS = setOf(
        "occupato", "impegnato", "fretta", "dopo", "non ora",
        "aspetta", "busy", "meeting", "riunione", "chiamata",
        "lavoro", "lavorando", "concentrato", "concentrazione",
    )

    private val STRESSED_KEYWORDS = setOf(
        "stress", "stressato", "nervoso", "agitato", "arrabbiato",
        "frustrato", "stanco", "esausto", "troppo", "basta",
    )

    private val RELAXED_KEYWORDS = setOf(
        "bene", "benissimo", "ottimo", "perfetto", "tranquillo",
        "relax", "libero", "finito", "completato", "fatto",
    )

    private val POSITIVE_KEYWORDS = setOf(
        "grazie", "fantastico", "bravo", "utile", "perfetto",
        "ottimo", "grande", "wow", "bello", "interessante",
    )

    private val TOPIC_KEYWORDS = mapOf(
        "meteo" to setOf("meteo", "tempo", "pioggia", "sole", "temperatura", "previsioni"),
        "reminder" to setOf("promemoria", "ricorda", "reminder", "sveglia", "allarme"),
        "email" to setOf("email", "mail", "posta", "messaggio"),
        "calendario" to setOf("calendario", "appuntamento", "evento", "meeting"),
        "notizie" to setOf("notizie", "news", "giornale", "articolo"),
        "musica" to setOf("musica", "canzone", "spotify", "playlist"),
    )

    /**
     * Analyze user text for mood indicators.
     * Returns updated awareness state.
     */
    fun analyzeUserText(text: String, currentState: UserAwarenessState): UserAwarenessState {
        val lower = text.lowercase()
        val words = lower.split(Regex("\\s+"))
        val wordCount = words.size

        var state = currentState.withUserResponse(wordCount)

        if (containsAny(lower, BUSY_KEYWORDS)) {
            state = state.withBusyMention()
        }

        if (containsAny(lower, POSITIVE_KEYWORDS) || containsAny(lower, RELAXED_KEYWORDS)) {
            state = state.withPositiveInteraction()
        }

        val discussedTopics = extractTopics(lower)
        for (topic in discussedTopics) {
            state = state.withUserKnowsAbout(topic)
        }

        return state
    }

    /**
     * Analyze robot response to track what user now knows.
     */
    fun analyzeRobotResponse(text: String, currentState: UserAwarenessState): UserAwarenessState {
        val lower = text.lowercase()
        val mentionedTopics = extractTopics(lower)

        var state = currentState
        for (topic in mentionedTopics) {
            state = state.withUserKnowsAbout(topic)
        }
        return state
    }

    /**
     * Check if user seems busy based on recent patterns.
     */
    fun seemsBusy(state: UserAwarenessState): Boolean {
        if (state.inferredMood == UserMood.BUSY) return true

        val busyMention = state.lastBusyMentionMillis
        if (busyMention != null) {
            val elapsed = System.currentTimeMillis() - busyMention
            if (elapsed < BUSY_COOLDOWN_MS) return true
        }

        return false
    }

    /**
     * Get a confidence score for proactive intervention (0.0-1.0).
     * Lower when user seems busy, higher when relaxed.
     */
    fun interventionConfidenceModifier(state: UserAwarenessState): Float {
        return when (state.inferredMood) {
            UserMood.BUSY -> 0.5f
            UserMood.FRUSTRATED -> 0.3f
            UserMood.RELAXED -> 1.2f
            UserMood.NEUTRAL -> 1.0f
            UserMood.UNKNOWN -> 0.8f
        }
    }

    /**
     * Check if a topic should be mentioned based on user awareness.
     * Returns true if user probably doesn't know about it yet.
     */
    fun shouldMentionTopic(topic: String, state: UserAwarenessState): Boolean {
        if (state.userKnowsAbout(topic)) return false
        return true
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun extractTopics(text: String): List<String> {
        return TOPIC_KEYWORDS.entries
            .filter { (_, keywords) -> keywords.any { text.contains(it) } }
            .map { it.key }
    }

    private const val BUSY_COOLDOWN_MS = 30 * 60 * 1000L
}
