package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ChainStatus

/**
 * Whether intermediate LLM reply text should be spoken while a tool chain runs.
 */
object ChainSpeechPolicy {

    fun suppressIntermediateSpeech(chainStatus: ChainStatus): Boolean =
        chainStatus == ChainStatus.IN_PROGRESS
}
