package com.example.mydeskrobot.data.check.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mydeskrobot.data.check.FireAndCheckStatus

@Entity(tableName = "fire_and_check")
data class FireAndCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val triggerReason: String,
    val checkGoal: String?,
    val primaryMessage: String,
    val primaryReminderId: Long?,
    val verificationMessage: String?,
    val verificationReminderId: Long?,
    val primaryDueAtMillis: Long?,
    val verificationDueAtMillis: Long?,
    val primaryFiredAtMillis: Long? = null,
    val status: FireAndCheckStatus = FireAndCheckStatus.ACTIVE,
    val createdAtMillis: Long,
)
