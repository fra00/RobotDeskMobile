package com.example.mydeskrobot.integration

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.llm.LlmClientFactory
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.integration.context.DayContextPromptProviderImpl
import com.example.mydeskrobot.integration.context.RobotContextPromptProviderImpl
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.integration.memory.MemoryPromptContextProviderImpl
import com.example.mydeskrobot.integration.tool.local.SetRobotContextTool
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolRouter
import com.example.mydeskrobot.integration.tool.local.AddListItemTool
import com.example.mydeskrobot.integration.tool.local.BrowserTool
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
import com.example.mydeskrobot.integration.tool.hardware.BodyHomeTool
import com.example.mydeskrobot.integration.tool.hardware.BodyStatusTool
import com.example.mydeskrobot.integration.tool.hardware.MoveBodyJointTool
import com.example.mydeskrobot.integration.tool.hardware.MoveBodyJointsTool
import com.example.mydeskrobot.memory.UserMemoryRepository
import kotlinx.coroutines.runBlocking
import com.example.mydeskrobot.integration.mood.DelegatingMoodContextProvider
import com.example.mydeskrobot.reasoning.MoodContextProvider
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
        additionalTools: List<Tool> = emptyList(),
    ): ReasoningEngine {
        val basePrompt = LlmPromptLoader.loadSystemPrompt(context)
        
        val llmClient = LlmClientFactory.create(llmSettings)
        val memoryRepository = UserMemoryRepository.create(context)
        val listItemRepository = ListItemRepository.create(context)

        val tools = buildList {
            add(CameraTool(visionImageCapture))
            add(DetectPresenceTool(visionImageCapture, llmClient, context))

            add(BrowserTool(context))
            add(SpotifyTool(context))
            add(SetRobotContextTool(RobotContextRepository(context)))
            add(ReminderTool(context))
            add(GetRemindersTool(context))
            add(DeleteReminderTool(context))
            add(SaveMemoryTool(memoryRepository))
            add(ListMemoriesTool(memoryRepository))
            add(DeleteMemoryTool(memoryRepository))
            add(AddListItemTool(listItemRepository))
            add(ListItemsTool(listItemRepository))
            add(UpdateListItemTool(listItemRepository))
            add(DeleteListItemTool(listItemRepository))
            add(VolumeTool(context))
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
            BodyApiClient.createIfConfigured(bodySettings)?.let { bodyClient ->
                add(MoveBodyJointTool(bodyClient))
                add(MoveBodyJointsTool(bodyClient))
                add(BodyHomeTool(bodyClient))
                add(BodyStatusTool(bodyClient))
            }
            
            addAll(additionalTools)
        }
        
        val toolRouter = ToolRouter(tools)
        val memoryContextProvider = MemoryPromptContextProviderImpl(memoryRepository)
        val scheduledTaskRepository = ScheduledTaskRepository.create(context)
        val dayContextProvider = DayContextPromptProviderImpl(
            scheduledTaskRepository = scheduledTaskRepository,
            listItemRepository = listItemRepository,
        )
        val robotContextRepository = RobotContextRepository(context)
        val robotContextProvider = RobotContextPromptProviderImpl(robotContextRepository)
        val bodyCapabilitiesProvider = BodyPromptProviderImpl(
            context = context,
            settingsRepository = BodySettingsRepository(context),
        )

        return ReasoningEngineImpl(
            llmClient = llmClient,
            toolExecutor = toolRouter,
            baseSystemPrompt = basePrompt,
            memoryContextProvider = memoryContextProvider,
            dayContextProvider = dayContextProvider,
            bodyCapabilitiesProvider = bodyCapabilitiesProvider,
            robotContextProvider = robotContextProvider,
            moodContextProvider = moodContextProvider,
            maxChainSteps = 10,
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
