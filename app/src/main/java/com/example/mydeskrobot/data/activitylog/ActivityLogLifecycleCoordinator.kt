package com.example.mydeskrobot.data.activitylog

import com.example.mydeskrobot.domain.predictivity.HabitPendingMiner
import com.example.mydeskrobot.domain.predictivity.MiningResult

/**
 * SSOT for activity log maintenance order: mine pending days, then prune.
 */
class ActivityLogLifecycleCoordinator(
    private val miner: HabitPendingMiner,
    private val activityLog: ActivityLogRepository,
) {
    suspend fun mineThenPrune(): MiningResult {
        val mining = miner.minePendingDays()
        activityLog.pruneExpired()
        return mining
    }
}
