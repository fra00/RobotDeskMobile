package com.example.mydeskrobot.data.scheduled

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.scheduled.db.ScheduledTaskDatabase
import com.example.mydeskrobot.data.scheduled.db.ScheduledTaskEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduledTaskRepository(
    private val database: ScheduledTaskDatabase,
) {
    private val dao = database.scheduledTaskDao()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun schedule(message: String, triggerAtMillis: Long): Long {
        val now = System.currentTimeMillis()
        val entity = ScheduledTaskEntity(
            message = message.trim(),
            triggerAtMillis = triggerAtMillis,
            status = ScheduledTaskStatus.PENDING,
            createdAtMillis = now,
        )
        return dao.insert(entity)
    }

    suspend fun getById(id: Long): ScheduledTaskEntity? = dao.getById(id)

    suspend fun listPending(): List<ScheduledTaskEntity> =
        dao.getByStatus(ScheduledTaskStatus.PENDING)

    fun observePending(): Flow<List<ScheduledTaskEntity>> {
        val fromStore = dao.observeByStatus(ScheduledTaskStatus.PENDING)
        val ticker = flow {
            emit(dao.getByStatus(ScheduledTaskStatus.PENDING))
            while (currentCoroutineContext().isActive) {
                delay(PENDING_TICK_MS)
                emit(dao.getByStatus(ScheduledTaskStatus.PENDING))
            }
        }
        return merge(fromStore, ticker).distinctUntilChanged()
    }

    suspend fun listPendingForDay(
        startOfDayMillis: Long,
        endOfDayMillis: Long,
    ): List<ScheduledTaskEntity> =
        dao.getPendingBetween(
            ScheduledTaskStatus.PENDING,
            startOfDayMillis,
            endOfDayMillis,
        )

    suspend fun markFired(id: Long) {
        dao.updateStatus(id, ScheduledTaskStatus.FIRED)
    }

    suspend fun cancel(id: Long): Boolean {
        val task = dao.getById(id) ?: return false
        if (task.status != ScheduledTaskStatus.PENDING) return false
        dao.updateStatus(id, ScheduledTaskStatus.CANCELLED)
        return true
    }

    suspend fun rescheduleAllPendingAlarms(context: Context) {
        val now = System.currentTimeMillis()
        val pending = dao.getPendingFuture(ScheduledTaskStatus.PENDING, now)
        pending.forEach { task ->
            ScheduledTaskAlarmScheduler.schedule(context, task.id, task.triggerAtMillis)
        }
    }

    fun formatScheduledTime(triggerAtMillis: Long): String = timeFormat.format(Date(triggerAtMillis))

    companion object {
        private const val PENDING_TICK_MS = 5_000L

        @Volatile
        private var databaseInstance: ScheduledTaskDatabase? = null

        fun create(context: Context): ScheduledTaskRepository {
            return ScheduledTaskRepository(database(context))
        }

        private fun database(context: Context): ScheduledTaskDatabase {
            val appContext = context.applicationContext
            return databaseInstance ?: synchronized(ScheduledTaskRepository::class.java) {
                databaseInstance ?: Room.databaseBuilder(
                    appContext,
                    ScheduledTaskDatabase::class.java,
                    "scheduled_tasks.db",
                ).build().also { databaseInstance = it }
            }
        }

        fun createInMemory(context: Context): ScheduledTaskRepository {
            val db = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ScheduledTaskDatabase::class.java,
            ).allowMainThreadQueries().build()
            return ScheduledTaskRepository(db)
        }
    }
}
