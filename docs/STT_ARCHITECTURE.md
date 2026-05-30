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
5. **Session silence timeout** does not run during the assistant turn (LLM + TTS + barge-in), via `isAssistantTurnActive` in `HotwordListeningService`.

## Files

- `data/hotword/SttListeningOrchestrator.kt`
- `data/hotword/ListeningConfig.kt`
- `data/speech/AndroidSpeechToTextDataSource.kt`
- `data/speech/VoskSpeechToTextDataSource.kt`
- `data/speech/SpeechToTextDataSource.kt` (contract KDoc)

See also: `docs/Drafts/STT-Analysis.md` (problem analysis + implemented notes).
