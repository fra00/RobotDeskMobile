package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.reasoning.RobotContextProvider

class RobotContextPromptProviderImpl(
    private val repository: RobotContextRepository,
) : RobotContextProvider {

    override suspend fun buildContextSection(): String {
        val stored = repository.getStoredState()
        return RobotContextPolicy.buildPromptSection(stored)
    }
}
