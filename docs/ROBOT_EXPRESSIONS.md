# Robot face expressions

The LLM sets `"emotion"` in every JSON response. `LlmEmotionMapper` maps tokens to `RobotEmotion` for `RobotEyes`.

## LLM-controlled tokens

| Token | UI | Typical trigger |
|-------|-----|-----------------|
| `neutral` | Open calm eyes | Default, "apri gli occhi" |
| `happy` | Smile | Praise, "sii felice", approx. "innamorato" |
| `sad` | Sad eyes | Bad news, empathy |
| `angry` | Angry brows | Strong irritation |
| `surprised` | Wide eyes | Unexpected news |
| `confused` | Asymmetric | Did not understand |
| `thinking` | Look up / process | Tools, "aspetta" |
| `bored` | Half-closed | "Che noia" |
| `sleeping` | Eyes closed | "Chiudi gli occhi" |
| `drowsy` | Heavy lids | Night / sleepy |
| `wink` | One eye closed | "Occhiolino" |
| `loving` | Soft wide smile | "Innamorato", affection |

## App-controlled (do not send from LLM)

`LISTENING`, `SPEAKING` — set by `ConversationViewModel` during STT/TTS.

Prompt reference: `llm_system_prompt.txt` → **ROBOT FACE EXPRESSIONS**.
