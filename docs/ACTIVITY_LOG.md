# Activity Log (Log Day)

Episodic log of short-lived user activities, separate from long-term user memory (`save_memory`).

## Purpose

- Capture meals, walks, breaks, brief outings for **proactivity** and habit awareness.
- Retain events for **7 days** (TTL), then prune automatically.
- Generate an LLM **habit summary** from aggregated events (settings: refresh manually or in standby).

## Storage

| Layer | Location |
|-------|----------|
| Events | Room `activity_log.db` → `activity_log_events` |
| Habit profile | Room → `activity_habit_profile` (single row) |
| Settings | DataStore `activity_log_settings` |

## Capture channels

1. **Background extraction** — `ActivityLogExtractionScheduler` in standby reads new `conversationLog` lines via `activity_extractor_prompt.txt` (same pattern as memory extraction).
2. **Tool** — `log_daily_activity` during dialogue when the user states an activity clearly.

Do **not** store ephemeral activities in `save_memory`. Use `OBSERVATION` only for robot-side contextual notes (e.g. user still at desk late), not user meals.

## Context injection

- **Voice turns**: `ActivityContextProvider` → `ATTIVITÀ RECENTI` + `PROFILO ABITUDINI` in system prompt.
- **Heartbeat**: `HeartbeatContextBuilder` adds `habitProfileSummary` and `recentDailyActivities` to `RobotInput.Heartbeat` / `[SYSTEM_INPUT: heartbeat]`.

## Settings UI

**Impostazioni → Log Day**

- Toggle extraction + interval (minutes)
- Read habit summary (refresh button)
- List events grouped by day (last 7 days)
- Clear log

## Related docs

- [`MEMORY.md`](MEMORY.md) — durable memory, OBSERVATION/INTENT/PATTERN autonomy channel
- [`AGENT_REASONING.md`](AGENT_REASONING.md) — proactive heartbeat policy
