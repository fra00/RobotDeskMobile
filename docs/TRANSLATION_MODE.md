# Translation mode

Continuous interpreting mode for My Desk Robot (v1).

## Behaviors

| User request | Behavior |
|--------------|----------|
| «Come si dice X in inglese?» / «Traduci questa frase in …» | One-shot dialogue. **No** tool. Italian framing OK + foreign text. |
| «Da adesso traduci in inglese quello che dico» | Tool `set_translation_mode` `enabled=true` + `target_language`. Short Italian ack. Later turns: **only** the translation. |
| «Ferma la traduzione» / «stop con le traduzioni» | Tool `enabled=false`. Brief Italian confirm. Back to normal companion. |

No Kotlin keyword short-circuit on phrases (same policy as notification reading). Enter/exit is LLM → tool.

## Tool: `set_translation_mode`

| Param | Required | Notes |
|-------|----------|-------|
| `enabled` | yes | `true` / `false` |
| `target_language` | when enabling | Free text or code (`inglese`, `en`, `francese`, …) → BCP-47 via `TranslationLanguageMapper` |

Persists in DataStore until explicit disable (survives voice session end / mic off), orthogonal to `set_robot_context` profiles.

## Prompt injection

When enabled, `TranslationModePromptProvider` appends **ACTIVE TRANSLATION MODE**, which overrides the default Italian reply rule.

## TTS

Default TTS locale is `it-IT`. While mode stays active across a turn (already on before the turn and still on after tools), `speak` uses the target BCP-47 tag. Enter-ack and exit-confirm stay Italian.

## Proactivity

While translation mode is on, predictivity speak and wellness scheduling are suppressed so interpreting is not interrupted.

## UI

Translate icon under the standby indicators when mode is active.

## Files

- `reasoning/model/TranslationModeState.kt`
- `domain/translation/TranslationLanguageMapper.kt`
- `domain/translation/TranslationModePolicy.kt`
- `data/translation/TranslationModeRepository.kt`
- `integration/tool/local/SetTranslationModeTool.kt`
- `integration/context/TranslationModePromptProviderImpl.kt`
- `ui/components/TranslationModeIndicator.kt`
