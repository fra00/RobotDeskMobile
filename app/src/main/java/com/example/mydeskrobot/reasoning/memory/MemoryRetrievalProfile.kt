package com.example.mydeskrobot.reasoning.memory

/**
 * Context-aware memory retrieval mode for prompt injection.
 */
enum class MemoryRetrievalProfile {
    /** User asks about a remembered entity (dog name, facts). */
    QUERY,
    /** Photo / vision / look-around intent. */
    VISION,
    /** Agenda, todos, reminders for today. */
    PLAN,
    /** Suggestions, hobbies, what to watch/do for fun. */
    LEISURE,
    /** Generic conversation — fuzzy match on user phrase. */
    DEFAULT,
}
