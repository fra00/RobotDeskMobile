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
| Sono al lavoro | `profile: work` (persists until `profile: normal`) |
| In call per un'ora | `profile: call`, `duration_minutes: 60` |
| Riunione 12–13 | `profile: meeting`, window_* |
| Torna normale | `profile: normal` |

## Notification handling

| Mode | Behavior |
|------|----------|
| NORMAL | Notifications → LLM → optional TTS (if enabled in settings) |
| SILENT | **Same LLM + log pipeline**; spontaneous **TTS suppressed** |

Silent mode does not bypass processing: notifications still enter `conversationLog` and the episodic extractor.  
`UnannouncedNotificationRepository` tracks items not yet read aloud for optional replay.

User voice: *"leggi le notifiche"* (TTS replay) or *"segna come lette"* (dismiss without TTS).  
Heartbeat ticks remain suppressed in SILENT profiles.

**User speech is always normal** — STT → LLM unchanged when the user talks.

## Profiles

| Profile | Default notifications | Style |
|---------|----------------------|--------|
| NORMAL | NORMAL | Default |
| WORK | SILENT | Concise until user resets |
| CALL / MEETING / FOCUS | SILENT | Short replies |

## Session scope

`session_only: true` applies only to **notification silencing** with `profile: normal` — it clears on voice session end (exit phrase or silence timeout).

Profiles (`work`, `call`, `meeting`, `focus`) **persist** across voice sessions, mic on/off, and silence timeout until the user says "torna normale" or a duration/window expires.

Timed contexts use `AlarmManager` → `RobotContextExpiryReceiver`.

## Prompt injection

`RobotContextProvider` appends an **ACTIVE ROBOT CONTEXT** block to the system prompt (with memory and date/time).

## UI indicator

When profile is not `NORMAL`, a small icon appears **below** the STT standby icon (top-left): briefcase (work), phone (call), groups (meeting), focus. Hidden in normal mode.

## Files

- `reasoning/model/RobotContextState.kt`
- `domain/context/RobotContextPolicy.kt`
- `data/context/RobotContextRepository.kt`
- `integration/tool/local/SetRobotContextTool.kt`
- `data/pending/UnannouncedNotificationRepository.kt`
- `integration/context/RobotContextPromptProviderImpl.kt`
