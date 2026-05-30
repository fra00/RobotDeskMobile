package com.example.mydeskrobot.domain.model

enum class RobotEmotion {
    NEUTRAL,
    HAPPY,
    SURPRISED,
    LISTENING,
    THINKING,
    SPEAKING,
    CONFUSED,
    ANGRY,
    SAD,
    BORED,
    /** Occhi chiusi — robot che dorme (standby notturno). */
    SLEEPING,
    /** Occhi assonnati — sveglia di notte. */
    DROWSY,
    /** Un occhio chiuso, uno aperto (occhiolino). */
    WINK,
    /** Espressione affettuosa / innamorato (più dolce di HAPPY). */
    LOVING,
}
