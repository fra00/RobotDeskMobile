package com.example.mydeskrobot.data.activitylog

import com.example.mydeskrobot.domain.predictivity.HabitPendingMiner
import com.example.mydeskrobot.domain.predictivity.MiningResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogLifecycleCoordinatorTest {

    @Test
    fun `mineThenPrune runs mining before prune`() = runTest {
        var mineCalled = false
        var pruneCalled = false
        val dao = FakeActivityLogDao(onDeleteOlderThan = {
            pruneCalled = true
            assertTrue("mining must run before prune", mineCalled)
            0
        })
        val miner = HabitPendingMiner {
            mineCalled = true
            assertFalse("prune must run after mining", pruneCalled)
            MiningResult(daysProcessed = 0, slotsUpdated = 0, lastMinedDayKey = null)
        }
        ActivityLogLifecycleCoordinator(miner, ActivityLogRepository.createForTest(dao)).mineThenPrune()
        assertTrue(mineCalled)
        assertTrue(pruneCalled)
    }
}
