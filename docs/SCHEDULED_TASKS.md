# Scheduled tasks (promemoria)

User-scheduled **deferred execution**: at trigger time the robot runs an **announce** payload (B1). Same infrastructure can later run other payloads (tools, heartbeat).

## Flow

```text
set_reminder → Room (PENDING) + AlarmManager
     → ReminderAlarmReceiver
         → Android notification (always)
         → SystemInputDispatcher → LLM → TTS (if mic / hotword active)
```

## Tools

| Tool | Role |
|------|------|
| `set_reminder` | Schedule announce task |
| `get_reminders` | List PENDING tasks |
| `delete_reminder` | Cancel alarm + mark CANCELLED |

## Policy

- **Not** dropped by `set_robot_context` silent external notifications.
- **Not** suppressed by night mode (user's own reminder).
- If hotword listening is off: notification only, no voice.

## Persistence

- DB: `scheduled_tasks.db` (`ScheduledTaskEntity`)
- `ScheduledTaskBootReceiver` re-schedules PENDING future alarms after reboot.

## Files

- `data/scheduled/` — Room + `ScheduledTaskAlarmScheduler`
- `integration/tool/local/ReminderTool.kt`, `ReminderAlarmReceiver.kt`
- `reasoning/model/RobotInput.ScheduledTaskFired`
- `integration/input/scheduled/ScheduledTaskInputSource.kt`

See also: `docs/INPUT_ARCHITECTURE.md`, `docs/Drafts/AgentEvolution-GapAnalysis.md`.
