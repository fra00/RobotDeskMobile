package com.example.mydeskrobot.domain.interaction

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Tracks repeated eye pokes within a sliding window for escalating annoyance.
 */
class EyePokeTracker(
    private val windowMs: Long = POKE_WINDOW_MS,
) {
    private val pokeTimestamps = ArrayDeque<Long>()

    fun recordPoke(now: Long = System.currentTimeMillis()): EyePokeReaction {
        pruneOld(now)
        pokeTimestamps.addLast(now)
        val count = pokeTimestamps.size
        return reactionForCount(count)
    }

    fun recentPokeCount(now: Long = System.currentTimeMillis()): Int {
        pruneOld(now)
        return pokeTimestamps.size
    }

    private fun pruneOld(now: Long) {
        while (pokeTimestamps.isNotEmpty() && now - pokeTimestamps.first() > windowMs) {
            pokeTimestamps.removeFirst()
        }
    }

    companion object {
        const val POKE_WINDOW_MS = 120_000L

        fun reactionForCount(count: Int): EyePokeReaction = when {
            count >= 6 -> EyePokeReaction(RobotEmotion.ANGRY, tier = 3)
            count >= 3 -> EyePokeReaction(RobotEmotion.ANGRY, tier = 2)
            count >= 2 -> EyePokeReaction(RobotEmotion.CONFUSED, tier = 1)
            else -> EyePokeReaction(RobotEmotion.CONFUSED, tier = 1)
        }
    }
}

data class EyePokeReaction(
    val emotion: RobotEmotion,
    val tier: Int,
)
