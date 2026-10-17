package com.example.mydeskrobot.data.hotword

import com.example.mydeskrobot.data.speech.FakeSpeechToTextDataSource
import com.example.mydeskrobot.domain.hotword.HotwordEvent
import com.example.mydeskrobot.domain.speech.ExitPhraseMatcher
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class SttListeningOrchestratorTest {

    private val wakePhrase = "assistente"
    private val exitPhrase = "esci"

    private fun testConfig(endOfUtteranceMs: Long = 150L) = ListeningConfig(
        wakePhrase = wakePhrase,
        exitPhrase = exitPhrase,
        endOfUtteranceMs = endOfUtteranceMs,
        segmentSilenceMs = ListeningConfig.segmentSilenceFor(endOfUtteranceMs),
        sessionSilenceTimeoutMs = 60_000L,
        postTtsCooldownMs = 0L,
    )

    /**
     * Starts the orchestrator on an independent scope.
     * Do **not** use `runBlocking { launch { run(...) } }`: [run] loops while the service
     * is active, so runBlocking would wait forever for the child.
     */
    private fun runOrchestrator(
        fake: FakeSpeechToTextDataSource,
        config: ListeningConfig = testConfig(),
        isAssistantTurnActive: () -> Boolean = { false },
        onEvent: suspend (HotwordEvent) -> Unit = {},
    ): Job {
        val orchestrator = SttListeningOrchestrator(
            dataSource = fake,
            config = config,
            wakePhraseMatcher = WakePhraseMatcher(wakePhrase),
            exitPhraseMatcher = ExitPhraseMatcher(exitPhrase),
            onEvent = onEvent,
        )
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        scope.launch {
            orchestrator.run(
                isServiceActive = { job.isActive },
                isSttEnabled = { true },
                isAssistantTurnActive = isAssistantTurnActive,
                isBargeInMode = { false },
                bargeInEchoReference = { null },
            )
        }
        return job
    }

    @Test
    fun `segmentSilenceFor is fraction of end of utterance`() {
        assertEquals(990L, ListeningConfig.segmentSilenceFor(1_800L))
    }

    @Test
    fun `emits UtteranceReadyForLlm after end of utterance silence`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<HotwordEvent>())
        val fake = FakeSpeechToTextDataSource()
        fake.enqueue(
            FakeSpeechToTextDataSource.ListenScript(transcript = "assistente dimmi il meteo"),
        )

        val job = runOrchestrator(fake) { events.add(it) }
        try {
            delay(600)

            assertTrue(events.any { it is HotwordEvent.UtteranceReadyForLlm })
            val ready = events.filterIsInstance<HotwordEvent.UtteranceReadyForLlm>().single()
            assertEquals("dimmi il meteo", ready.phrase)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `does not start second listen while buffer waits for end of utterance`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<HotwordEvent>())
        val fake = FakeSpeechToTextDataSource()
        fake.enqueue(
            FakeSpeechToTextDataSource.ListenScript(transcript = "assistente prima frase"),
        )

        val job = runOrchestrator(fake, testConfig(endOfUtteranceMs = 400L)) { events.add(it) }
        try {
            delay(250)
            // Still inside end-of-utterance window with a non-empty phrase buffer.
            assertEquals(1, fake.listenCallCount)

            delay(400)
            assertTrue(events.any { it is HotwordEvent.UtteranceReadyForLlm })
            // After finalize the buffer is empty — starting another listen is expected.
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `late partials during listen do not block finalize`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<HotwordEvent>())
        val fake = FakeSpeechToTextDataSource()
        fake.enqueue(
            FakeSpeechToTextDataSource.ListenScript(
                transcript = "assistente",
            ),
            FakeSpeechToTextDataSource.ListenScript(
                transcript = "ciao mondo",
                partials = listOf("ciao", "ciao mon"),
                latePartialsAfterFinal = listOf("ciao mondo extra"),
            ),
        )

        val job = runOrchestrator(fake) { events.add(it) }
        try {
            delay(800)

            val ready = events.filterIsInstance<HotwordEvent.UtteranceReadyForLlm>().singleOrNull()
            assertEquals("ciao mondo", ready?.phrase)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `does not listen during assistant turn in active session`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<HotwordEvent>())
        val fake = FakeSpeechToTextDataSource()
        fake.enqueue(
            FakeSpeechToTextDataSource.ListenScript(transcript = "assistente"),
        )
        var assistantTurnActive = false

        val job = runOrchestrator(
            fake = fake,
            isAssistantTurnActive = { assistantTurnActive },
        ) { events.add(it) }
        try {
            delay(300)
            assertTrue(events.any { it is HotwordEvent.SessionStarted })

            val listensAtSessionStart = fake.listenCallCount
            assistantTurnActive = true
            delay(800)

            assertEquals(listensAtSessionStart, fake.listenCallCount)
        } finally {
            job.cancel()
        }
    }
}
