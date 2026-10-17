package com.example.mydeskrobot.data.health

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityClient
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory

object HealthConnectModule {

    fun createLocalReader(context: Context): HealthConnectExerciseReader =
        HealthConnectExerciseReaderImpl(context.applicationContext)

    fun createReader(context: Context): HealthConnectExerciseReader {
        val app = context.applicationContext
        val local = createLocalReader(app)
        val modeRepo = DeviceModeRepository(app)
        val client = PhoneCapabilityClient(app, modeRepo)
        return RemotePreferringExerciseReader(local, modeRepo, client)
    }

    fun createSync(context: Context): HealthConnectExerciseSync {
        val appContext = context.applicationContext
        return HealthConnectExerciseSync(
            reader = createReader(appContext),
            activityLogRepository = ActivityLogRepository.create(appContext),
            memoryWriter = UnifiedMemoryFactory.createWriter(appContext),
        )
    }

    fun createSync(
        reader: HealthConnectExerciseReader,
        activityLogRepository: ActivityLogRepository,
        memoryWriter: com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter,
    ): HealthConnectExerciseSync =
        HealthConnectExerciseSync(
            reader = reader,
            activityLogRepository = activityLogRepository,
            memoryWriter = memoryWriter,
        )
}
