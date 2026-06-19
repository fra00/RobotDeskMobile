package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.RobotInput

fun interface HeartbeatPlaybookProvider {
    /**
     * Returns the heartbeat playbook section when [input] is a heartbeat or weekly reflection tick.
     */
    suspend fun buildContextSection(input: RobotInput?): String
}
