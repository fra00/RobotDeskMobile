package com.example.mydeskrobot.memory

data class ForgetByTopicResult(
    val deletedCount: Int,
    val deletedValues: List<String>,
    val deletedIds: List<Long>,
)
