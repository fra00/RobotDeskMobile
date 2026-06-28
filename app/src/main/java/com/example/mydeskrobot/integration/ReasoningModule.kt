package com.example.mydeskrobot.integration

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.llm.LlmClientFactory
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.integration.context.RobotContextPromptProviderImpl
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.integration.memory.LlmMemoryRecallPlanner
import com.example.mydeskrobot.integration.memory.UnifiedRecallMemoryContextProvider
import com.example.mydeskrobot.integration.tool.local.SetRobotContextTool
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolRouter
import com.example.mydeskrobot.integration.tool.local.AddListItemTool
import com.example.mydeskrobot.integration.tool.local.BrowserTool
import com.example.mydeskrobot.integration.telephony.AndroidContactsPhoneResolver
import com.example.mydeskrobot.integration.telephony.PhoneContactResolver
import com.example.mydeskrobot.integration.whatsapp.AndroidWhatsAppContactResolver
import com.example.mydeskrobot.integration.whatsapp.WhatsAppTargetResolver
import com.example.mydeskrobot.integration.tool.local.DialPhoneTool
import com.example.mydeskrobot.integration.tool.local.ResolvePhoneContactTool
import com.example.mydeskrobot.integration.tool.local.ResolveWhatsAppTargetTool
import com.example.mydeskrobot.integration.tool.local.SendWhatsAppTool
import com.example.mydeskrobot.integration.tool.local.DeleteListItemTool
import com.example.mydeskrobot.integration.tool.local.ListItemsTool
import com.example.mydeskrobot.integration.tool.local.SpotifyTool
import com.example.mydeskrobot.integration.tool.local.UpdateListItemTool
import com.example.mydeskrobot.integration.tool.local.CameraTool
import com.example.mydeskrobot.integration.tool.local.DetectPresenceTool
import com.example.mydeskrobot.integration.tool.local.NotificationTool
import com.example.mydeskrobot.integration.tool.local.DeleteMemoryTool
import com.example.mydeskrobot.integration.tool.local.DeleteReminderTool
import com.example.mydeskrobot.integration.tool.local.GetRemindersTool
import com.example.mydeskrobot.integration.tool.local.ListMemoriesTool
import com.example.mydeskrobot.integration.tool.local.LogDailyActivityTool
import com.example.mydeskrobot.integration.tool.local.MakeLightTool
import com.example.mydeskrobot.integration.tool.local.ReminderTool
import com.example.mydeskrobot.integration.tool.local.SaveMemoryTool
import com.example.mydeskrobot.integration.tool.local.VolumeTool
import com.example.mydeskrobot.data.search.SearchSettingsRepository
import com.example.mydeskrobot.integration.tool.remote.ChainedWebSearchEngine
import com.example.mydeskrobot.integration.tool.remote.DuckDuckGoHtmlWebSearchEngine
import com.example.mydeskrobot.integration.tool.remote.FetchUrlTool
import com.example.mydeskrobot.integration.tool.remote.SearxngWebSearchEngine
import com.example.mydeskrobot.integration.tool.remote.WebSearchTool
import com.example.mydeskrobot.integration.tool.remote.WeatherTool
import com.example.mydeskrobot.data.body.BodySettingsRepository
import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.BodyPromptProviderImpl
import com.example.mydeskrobot.integration.input.heartbeat.HeartbeatPlaybookProviderImpl
import com.example.mydeskrobot.integration.tool.hardware.BodyHomeTool
import com.example.mydeskrobot.integration.tool.hardware.BodyStatusTool
import com.example.mydeskrobot.domain.spatial.SpatialScanSession
import com.example.mydeskrobot.integration.tool.hardware.MoveBodyJointTool
import com.example.mydeskrobot.integration.tool.hardware.MoveBodyJointsTool
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory
import kotlinx.coroutines.runBlocking
import com.example.mydeskrobot.integration.mood.DelegatingMoodContextProvider
import com.example.mydeskrobot.integration.spatial.SpatialRuntimeBindings
import com.example.mydeskrobot.integration.tool.local.spatial.AnalyzeRoomSceneTool
import com.example.mydeskrobot.integration.tool.local.spatial.ListPlacesTool
import com.example.mydeskrobot.integration.tool.local.spatial.MatchPlaceTool
import com.example.mydeskrobot.integration.tool.local.spatial.SavePlaceTool
import com.example.mydeskrobot.integration.tool.local.spatial.SetCurrentPlaceTool
import com.example.mydeskrobot.reasoning.MoodContextProvider
import com.example.mydeskrobot.reasoning.NoOpReasoningLogObserver
import com.example.mydeskrobot.reasoning.ReasoningLogObserver
import com.example.mydeskrobot.reasoning.ReasoningEngine
import com.example.mydeskrobot.reasoning.ReasoningEngineImpl

/**
 * Module that configures and creates the ReasoningEngine.
 * Wires together LLM client, tool router, and all tools.
 */
object ReasoningModule {
    
    /**
     * Create a fully configured ReasoningEngine.
     * 
     * @param context Application context for loading prompts
     * @param visionImageCapture Camera capture implementation
     * @param additionalTools Extra tools to register
     */
    fun createReasoningEngine(
        context: Context,
        visionImageCapture: VisionImageCapture,
        llmSettings: LlmSettings,
        moodContextProvider: MoodContextProvider = DelegatingMoodContextProvider(),
        spatialBindings: SpatialRuntimeBindings? = null,
        additionalTools: List<Tool> = emptyList(),
        reasoningLogObserver: ReasoningLogObserver = NoOpReasoningLogObserver,
        onBodyHardwareBusyChanged: (Boolean) -> Unit = {},
    ): ReasoningEngine {
        val basePrompt = LlmPromptLoader.loadSystemPrompt(context)
        
        val llmClient = LlmClientFactory.create(llmSettings)
        val unifiedMemoryRepository = UnifiedMemoryFactory.createRepository(context)
        val memoryWriter = UnifiedMemoryFactory.createWriter(context)
        val listItemRepository = ListItemRepository.create(context)

        val tools = buildList {
            add(CameraTool(visionImageCapture))
            add(DetectPresenceTool(visionImageCapture, llmClient, context))

            add(BrowserTool(context))
            val phoneContactResolver = PhoneContactResolver(
                contactsResolver = AndroidContactsPhoneResolver(context),
                unifiedMemoryRepository = unifiedMemoryRepository,
            )
            add(ResolvePhoneContactTool(phoneContactResolver))
            add(DialPhoneTool(context))
            val whatsAppTargetResolver = WhatsAppTargetResolver(
                whatsAppContactResolver = AndroidWhatsAppContactResolver(context),
                phoneContactResolver = phoneContactResolver,
                unifiedMemoryRepository = unifiedMemoryRepository,
            )
            add(ResolveWhatsAppTargetTool(whatsAppTargetResolver))
            add(SendWhatsAppTool(context))
            add(SpotifyTool(context))
            add(SetRobotContextTool(RobotContextRepository(context)))
            add(ReminderTool(context, memoryWriter = memoryWriter))
            add(GetRemindersTool(context))
            add(DeleteReminderTool(context, memoryWriter = memoryWriter))
            add(SaveMemoryTool(unifiedMemoryRepository))
            add(LogDailyActivityTool(memoryWriter))
            add(ListMemoriesTool(unifiedMemoryRepository))
            add(DeleteMemoryTool(unifiedMemoryRepository))
            add(AddListItemTool(listItemRepository, memoryWriter))
            add(ListItemsTool(listItemRepository))
            add(UpdateListItemTool(listItemRepository, memoryWriter))
            add(DeleteListItemTool(listItemRepository, memoryWriter))
            add(VolumeTool(context))
            add(MakeLightTool())
            add(NotificationTool(context))
            
            val weatherApiKey = getWeatherApiKey()
            if (weatherApiKey.isNotBlank()) {
                add(WeatherTool(weatherApiKey))
            }

            val searchSettings = SearchSettingsRepository(context)
            val webSearchEngine = ChainedWebSearchEngine(
                listOf(
                    SearxngWebSearchEngine.create(searchSettings),
                    DuckDuckGoHtmlWebSearchEngine(),
                ),
            )
            add(WebSearchTool(webSearchEngine))
            add(FetchUrlTool())

            val bodySettings = runBlocking { BodySettingsRepository(context).load() }
            val bodyClient = BodyApiClient.createIfConfigured(bodySettings)
            SpatialScanSession.configure(bodyAvailable = bodyClient != null)
            bodyClient?.let { client ->
                add(MoveBodyJointTool(client))
                add(MoveBodyJointsTool(client))
                add(BodyHomeTool(client))
                add(BodyStatusTool(client))
            }

            spatialBindings?.let { spatial ->
                val placeRepository = spatial.placeRepository
                add(
                    AnalyzeRoomSceneTool(
                        visionCapture = visionImageCapture,
                        llmClient = llmClient,
                        context = context,
                        onAnalyzed = { landmarks ->
                            SpatialScanSession.recordScan(landmarks)
                            spatial.manager?.updateLastScan(SpatialScanSession.mergedLandmarks())
                        },
                    ),
                )
                add(MatchPlaceTool(placeRepository))
                add(SavePlaceTool(placeRepository, memoryWriter))
                add(ListPlacesTool(placeRepository))
                add(
                    SetCurrentPlaceTool(
                        placeRepository = placeRepository,
                        spatialContextManager = { spatial.requireManager() },
                        memoryWriter = memoryWriter,
                    ),
                )
            }
            
            addAll(additionalTools)
        }
        
        val toolRouter = ToolRouter(tools)
        val memoryContextProvider = UnifiedRecallMemoryContextProvider(
            unifiedMemoryRepository = unifiedMemoryRepository,
        )
        val memoryRecallPlanner = LlmMemoryRecallPlanner(
            llmClient = llmClient,
            systemPrompt = LlmPromptLoader.loadMemoryRecallPlannerPrompt(context),
        )
        val robotContextRepository = RobotContextRepository(context)
        val robotContextProvider = RobotContextPromptProviderImpl(robotContextRepository)
        val bodyCapabilitiesProvider = BodyPromptProviderImpl(
            context = context,
            settingsRepository = BodySettingsRepository(context),
        )
        val heartbeatPlaybookProvider = HeartbeatPlaybookProviderImpl(context)
        val heartbeatCriticPrompt = LlmPromptLoader.loadHeartbeatCriticPrompt(context)

        spatialBindings?.contextProvider?.unifiedMemoryRepository = unifiedMemoryRepository

        return ReasoningEngineImpl(
            llmClient = llmClient,
            toolExecutor = toolRouter,
            baseSystemPrompt = basePrompt,
            memoryRecallPlanner = memoryRecallPlanner,
            memoryContextProvider = memoryContextProvider,
            spatialContextProvider = spatialBindings?.contextProvider,
            bodyCapabilitiesProvider = bodyCapabilitiesProvider,
            heartbeatPlaybookProvider = heartbeatPlaybookProvider,
            heartbeatCriticPrompt = heartbeatCriticPrompt,
            robotContextProvider = robotContextProvider,
            moodContextProvider = moodContextProvider,
            maxChainSteps = 10,
            reasoningLogObserver = reasoningLogObserver,
            onBodyHardwareBusyChanged = onBodyHardwareBusyChanged,
        )
    }
    
    private fun getWeatherApiKey(): String {
        return try {
            com.example.mydeskrobot.BuildConfig.WEATHER_API_KEY
        } catch (_: Exception) {
            ""
        }
    }
}
