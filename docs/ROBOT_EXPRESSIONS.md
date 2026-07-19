# Robot face expressions

**Architecture (wellbeing vs ephemeral, triggers, prompt):** [`MOOD.md`](MOOD.md) · human guide: [`guides/UMORE.md`](guides/UMORE.md).

The LLM sets `"emotion"` in every JSON response. `LlmEmotionMapper` maps tokens to `RobotEmotion` for `RobotEyes`.

Rendering uses [`EyeExpressionMapper`](app/src/main/java/com/example/mydeskrobot/ui/eyes/EyeExpressionMapper.kt): sclera shape + **pupil** + **eyebrow** + optional **micro-motion**, amplified by `emotionIntensity` (0–1) from `RobotMood`.

## LLM-controlled tokens

| Token | UI eyes | Body gesture (ESP32) | Typical trigger |
|-------|---------|----------------------|-----------------|
| `neutral` | Open calm eyes | Head/display center on ephemeral expiry | Default |
| `happy` | Smile, bounce | Brief nod (`head_tilt`) | Praise, "sii felice" |
| `sad` | Droopy sclera | Head down then return | Bad news, "sei triste" |
| `angry` | V-brow, shake | Turn display away | Eye poke |
| `surprised` | Wide eyes, pop | Quick look-around | Unexpected news |
| `confused` | Pupil drift | Brief head roll | Unclear request |
| `thinking` | Look up | — (app phase blocks auto gesture) | Tools |
| `bored` | Half-closed | Micro pan (heartbeat) | Idle / "che noia" |
| `sleeping` | Eyes closed | Sleep pose head ~−10° | Night / "dormi" |
| `drowsy` | Heavy lids | — | Night |
| `wink` | One eye closed | — | "Occhiolino" |
| `loving` | Soft smile | Brief nod | Affection |

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

`STATO ROBOT` (injected) overrides generic examples. Body gestures: `EmotionGestureMapper` + `docs/BODY_INTEGRATION.md` when ESP32 is configured.

## Reply style (MOOD_REPLY_STYLE)

`MoodReplyStyleResolver` adds a **STILE RISPOSTA** block to injected `STATO ROBOT`:

| Profile | Mood | Spoken `reply` |
|---------|------|----------------|
| `terse` | sad, angry, low valence | 1–2 frasi essenziali, tono basso/secco |
| `normal` | neutral | Breve colloquiale (default attuale) |
| `warm` | happy, loving, high valence | Più caldo da coinquilino ("dai", "figurati"), non da call center; sempre conciso |

Spec: `llm_system_prompt.txt` §9 **MOOD_REPLY_STYLE** + §6 **HUMAN_VOICE**; code: `MoodReplyStyle.kt`, `HumanVoicePrompt.kt` (injected ogni turno utente).
