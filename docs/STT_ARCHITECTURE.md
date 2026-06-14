# STT architecture

Unified speech pipeline for **Android STT** and **Vosk**: same session behavior, interchangeable providers.

## Layers

| Layer | Responsibility |
|-------|----------------|
| `SpeechToTextDataSource` | One listen **segment** (mic until provider segment silence). Emits partial/final chunks. |
| `SttListeningOrchestrator` | Wake/exit, phrase buffer, **end-of-utterance** → `UtteranceReadyForLlm`, session silence. |
| `HotwordListeningService` | Foreground service, builds `ListeningConfig`, wires provider + orchestrator. |
| `ConversationViewModel` | UI + LLM on `UtteranceReadyForLlm` (echo filter, queue). |

## Timing (balanced default)

| Parameter | Default | Source |
|-----------|---------|--------|
| `endOfUtteranceMs` | **1800** | `utterance_pause_seconds=2` → balanced constant in `HotwordListeningService` |
| `segmentSilenceMs` | ~55% of end (800–1200) | `ListeningConfig.segmentSilenceFor()` → Android intent extras / Vosk segment end |
| `sessionSilenceTimeoutMs` | 15s | `conversation_silence_timeout_seconds` |

Target: ~**1.8s** from last committed words to `UtteranceReadyForLlm`, then VM → `Thinking`.

## Rules

1. **Partials** update UI only (`UtteranceInProgress`); they do **not** reset end-of-utterance clock.
2. **Transcript/final** append to buffer and set `lastContentAt`.
3. While buffer is non-empty and pause not elapsed → **poll** (`PAUSE_POLL_MS`), no second `listenWithChunks`.
4. Providers close segments at `segmentSilenceMs`; orchestrator finalizes at `endOfUtteranceMs` after last content.
5. **Session silence timeout** does not run during the assistant turn (LLM + TTS), via `isAssistantTurnActive` in `HotwordListeningService`.
6. **STT is paused** for the whole assistant turn (Thinking, tool execution, TTS). Barge-in during TTS is disabled to avoid TTS echo in the mic; listening resumes after `endAssistantTurn` + post-TTS cooldown.

## Files

- `data/hotword/SttListeningOrchestrator.kt`
- `data/hotword/ListeningConfig.kt`
- `data/speech/AndroidSpeechToTextDataSource.kt`
- `data/speech/VoskSpeechToTextDataSource.kt`
- `data/speech/SpeechToTextDataSource.kt` (contract KDoc)

See also: `docs/Drafts/STT-Analysis.md` (problem analysis + implemented notes).

## Beep suppression (Android STT)

Google `SpeechRecognizer` plays system sounds on start, end-of-speech, and results. Mitigation via [`SttBeepSuppressor`](../app/src/main/java/com/example/mydeskrobot/data/speech/SttBeepSuppressor.kt):

| When | Action |
|------|--------|
| Immediately before `SpeechRecognizer.startListening()` | `onListenStarted()` — mute `SYSTEM`, `NOTIFICATION`, `ALARM` |
| `onResults`, `onError`, cancel, or `release()` | `onListenEnded()` / `forceRestore()` — restore saved volumes |

Mute is **only** for the active listen segment in [`AndroidSpeechToTextDataSource`](../app/src/main/java/com/example/mydeskrobot/data/speech/AndroidSpeechToTextDataSource.kt). **`STREAM_MUSIC` is never touched** so TTS stays at normal volume during assistant turns.

Vosk uses `AudioRecord` directly (no suppressor).

### Manual QA (Android STT)

1. Standby hotword loop — no start/end beeps each cycle
2. Active session: speak → pause → LLM — no triple beep per phrase
3. Robot TTS during assistant turn — speech audible
4. Stop hotword service — system/media volumes restored
5. Vosk provider — unchanged (silent)

### OEM limits

Some devices (MIUI, OneUI) may still beep on proprietary streams; use **Vosk** in Impostazioni → STT as fallback.
