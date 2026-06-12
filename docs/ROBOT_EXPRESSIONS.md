# Robot face expressions

The LLM sets `"emotion"` in every JSON response. `LlmEmotionMapper` maps tokens to `RobotEmotion` for `RobotEyes`.

Rendering uses [`EyeExpressionMapper`](app/src/main/java/com/example/mydeskrobot/ui/eyes/EyeExpressionMapper.kt): sclera shape + **pupil** + **eyebrow** + optional **micro-motion**, amplified by `emotionIntensity` (0–1) from `RobotMood`.

## LLM-controlled tokens

| Token | UI | Typical trigger |
|-------|-----|-----------------|
| `neutral` | Open calm eyes, subtle brow | Default, "apri gli occhi" |
| `happy` | Smile sclera, arched brow, bounce | Praise, "sii felice" |
| `sad` | Droopy sclera, sad brow | Bad news, empathy |
| `angry` | Inward V-brow, inward pupils, shake | Irritation / eye poke |
| `surprised` | Wide eyes, high brow, pop scale | Unexpected news |
| `confused` | Asymmetric eyes, pupil drift | Did not understand |
| `thinking` | Look up, pupils up | Tools, "aspetta" |
| `bored` | Half-closed, slow droop | "Che noia" / idle mood |
| `sleeping` | Eyes closed, no pupil/brow | "Chiudi gli occhi" |
| `drowsy` | Heavy lids | Night / sleepy |
| `wink` | One eye closed | "Occhiolino" |
| `loving` | Soft smile + bounce | Affection |

## App-controlled (do not send from LLM)

`LISTENING`, `SPEAKING` — set by `ConversationViewModel` during STT/TTS.

## Intensity

`ConversationUiState.emotionIntensity` mirrors `RobotMood.intensity`. Higher values exaggerate angry rotation, brow thickness, and shake amplitude.

## Motion types

| Motion | Emotions |
|--------|----------|
| SHAKE | ANGRY |
| BOUNCE | HAPPY, LOVING |
| PUPIL_DRIFT | CONFUSED |
| SLOW_DROOP | BORED |
| Surprised pop | SURPRISED (one-shot scale on pair) |

Prompt reference: `llm_system_prompt.txt` → **ROBOT FACE EXPRESSIONS** and **COGNITIVE PERSONAS**.

## Cognitive personas (v1.6 mapping)

Five behavioral personas from `docs/nextPromptv1.md` map to existing LLM tokens (no new JSON fields):

| Persona | `emotion` | App mood alignment |
|---------|-----------|-------------------|
| HAPPY | `happy` | Positive interaction |
| SAD / shame | `sad` | Failure after Fire-and-Check |
| ANGRY | `angry` | Eye poke, repeated insults |
| BORED | `bored` | Long idle (`MoodEngine`) |
| SLEEPY | `drowsy` / `sleeping` | Night mode |

`STATO ROBOT` (injected) overrides generic examples. Body moves for personas: `body_capabilities_prompt.txt` when ESP32 is configured.
