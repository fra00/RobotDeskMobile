# Robot Context — Desk Robot Only

> How the robot adapts interaction (work, call, meeting) and silences **its own** notification announcements.  
> Does **not** change Android Do Not Disturb or mute other apps.

---

## Tool: `set_robot_context`

Single tool for context and notification policy. Reset with `profile: "normal"`.

| Voice example | Params |
|---------------|--------|
| Silenzia notifiche per questa sessione | `notifications: silent`, `session_only: true` |
| Silenzia per un'ora | `notifications: silent`, `duration_minutes: 60` |
| Sono al lavoro | `profile: work`, `session_only: true` |
| In call per un'ora | `profile: call`, `duration_minutes: 60` |
| Riunione 12–13 | `profile: meeting`, window_* |
| Torna normale | `profile: normal` |

## Notification handling

| Mode | Behavior |
|------|----------|
| NORMAL | Notifications → LLM → optional TTS (if enabled in settings) |
| SILENT | **DROP** — no LLM, TTS, or conversation log line |

DROP is enforced in `NotificationInputSource` and `ConversationViewModel` (guard).

**User speech is always normal** — STT → LLM unchanged when the user talks.

## Profiles

| Profile | Default notifications | Style |
|---------|----------------------|--------|
| NORMAL | NORMAL | Default |
| WORK | SILENT | Concise, session-scoped typical |
| CALL / MEETING / FOCUS | SILENT | Short replies |

## Session scope

If `session_only: true`, context clears on voice session end (exit phrase or silence timeout).

Timed contexts use `AlarmManager` → `RobotContextExpiryReceiver`.

## Prompt injection

`RobotContextProvider` appends an **ACTIVE ROBOT CONTEXT** block to the system prompt (with memory and date/time).

## Files

- `reasoning/model/RobotContextState.kt`
- `domain/context/RobotContextPolicy.kt`
- `data/context/RobotContextRepository.kt`
- `integration/tool/local/SetRobotContextTool.kt`
- `integration/context/RobotContextPromptProviderImpl.kt`
