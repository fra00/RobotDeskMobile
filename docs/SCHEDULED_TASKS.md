# Scheduled tasks (promemoria)

User-scheduled **deferred execution**: at trigger time the robot runs an **announce** payload (B1). Same infrastructure can later run other payloads (tools, heartbeat).

## Flow

```text
set_reminder → Room (PENDING) + AlarmManager
     → ReminderAlarmReceiver
         → Android notification (always, on ROBOT)
         → optional POST local_notification to PROXY (Mode Bot dual reminder ON)
         → SystemInputDispatcher → LLM → TTS (if mic / hotword active)
```

**Dual delivery:** scheduling stays on the robot only. Opt-in checkbox in Mode Bot (ROBOT). See [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) §6.

## Tools

| Tool | Role |
|------|------|
| `set_reminder` | Schedule announce task; optional `fire_and_check` (physical) or `commitment_follow_up` (urgent + time → Verifica +60m; urgent **without** time → sticky icon, no alarm) |
| `get_reminders` | List PENDING tasks |
| `delete_reminder` | Cancel alarm + mark CANCELLED |

## Policy

- **Not** dropped by `set_robot_context` silent external notifications.
- **Not** suppressed by night mode (user's own reminder) for the **primary** announce.
- Commitment **verification** voice (`Verifica impegno:`) follows `ProactiveSpeakGate` — deferred when work/silent; see `docs/FIRE_AND_CHECK.md`.
- If hotword listening is off: notification only, no voice.

## Persistence

- DB: `scheduled_tasks.db` (`ScheduledTaskEntity`)
- `ScheduledTaskBootReceiver` re-schedules PENDING future alarms after reboot.
- Commitment / physical loops: `fire_and_check.db`

## Files

- `data/scheduled/` — Room + `ScheduledTaskAlarmScheduler`
- `integration/tool/local/ReminderTool.kt`, `ReminderAlarmReceiver.kt`
- `reasoning/model/RobotInput.ScheduledTaskFired`
- `integration/input/scheduled/ScheduledTaskInputSource.kt`

See also: `docs/INPUT_ARCHITECTURE.md`, `docs/FIRE_AND_CHECK.md`, `docs/TODO.md`.
