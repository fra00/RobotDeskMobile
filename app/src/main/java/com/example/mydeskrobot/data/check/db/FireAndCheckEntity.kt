package com.example.mydeskrobot.data.check.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mydeskrobot.data.check.FireAndCheckStatus
import com.example.mydeskrobot.domain.check.FireAndCheckKind

@Entity(tableName = "fire_and_check")
data class FireAndCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
    val triggerReason: String,
    val checkGoal: String?,
    val primaryMessage: String,
    val primaryReminderId: Long?,
    val verificationMessage: String?,
    val verificationReminderId: Long?,
    val primaryDueAtMillis: Long?,
    val verificationDueAtMillis: Long?,
    val primaryFiredAtMillis: Long? = null,
    val reRemindCount: Int = 0,
    val status: FireAndCheckStatus = FireAndCheckStatus.ACTIVE,
    val createdAtMillis: Long,
)
