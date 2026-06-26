# Activity Log (Log Day)

Episodic timeline of the user's day — physical activities, future plans, social threads — separate from long-term user memory (`save_memory`).

## Purpose

- Capture meals, walks, breaks, brief outings for **proactivity** and habit awareness.
- Capture **future-relevant episodes** (plans, social messages, commitments) from dialogue and notifications via semantic organizer.
- Support **tentative → confirmed** conjectures (e.g. vague WhatsApp then confirmed time).
- Retain events for **7 days** (TTL), then prune automatically.
- Generate an LLM **habit summary** from aggregated events (settings: refresh manually or in standby).
- Inject **EPISODI PROSSIMI** into PLAN profile when user asks "cosa devo fare oggi/domani".

## Storage

| Layer | Location |
|-------|----------|
| Events | Room `activity_log.db` → `activity_log_events` (v2: episodic fields) |
| Habit profile | Room → `activity_habit_profile` (single row) |
| Settings | DataStore `activity_log_settings` |

### Event fields (v2)

| Field | Use |
|-------|-----|
| `eventKind` | `PHYSICAL_NOW`, `PLAN`, `SOCIAL_THREAD`, `COMMITMENT` |
| `confidence` | `TENTATIVE` (congettura), `CONFIRMED` |
| `scheduledDayKey` | Target day `yyyy-MM-dd` for upcoming episodes |
| `scheduledAtMs` | Optional precise time on that day |
| `actor` | Contact name (e.g. WhatsApp title) |
| `sourceChannel` | e.g. WhatsApp, dialogo |

`timestampMs` / `dayKey` = when the event was logged; `scheduled*` = when it matters for the user's life.

## Capture channels

1. **Background extraction** — `ActivityLogExtractionScheduler` in standby reads new `conversationLog` lines (Tu/Robot/**Sistema**) via `episodic_extractor_prompt.txt`.
2. **Tool** — `log_daily_activity` during dialogue (optional kind, scheduled_day, actor, confidence).

Do **not** store ephemeral activities in `save_memory`. Do **not** dump all notifications — organizer returns empty for spam/irrelevant content.

## Context injection (unified recall)

All episodic and planning context is retrieved via **unified recall** (`UnifiedRecallMemoryContextProvider`) — no separate Day/Activity providers.

| User cue | Injected via |
|----------|----------------|
| **Temporal** (ieri/oggi/domani) | All `EPISODE` + `REMINDER` for resolved `dayKey` in `MEMORIA` |
| **Planning** ("cosa devo fare domani") | Same — semantic + day scope |
| **Habit** | `HABIT_SUMMARY` doc when recall scores it |

`PlanningDayResolver` maps "domani"/"dopodomani" in user phrase to `dayKey` filter.

Legacy operational DBs (`activity_log.db`, etc.) remain for write/tools; read path uses `memory_documents` projections. Episode projections include optional `rawPhrase` snippet in the indexed `value` (e.g. social message content) for unified recall.

## Settings UI

**Impostazioni → Log Day**

- Toggle extraction + interval (minutes)
- Read habit summary (refresh button)
- List events grouped by day (kind, confidence, scheduled time, raw phrase)
- Clear log

## Related docs

- [`MEMORY.md`](MEMORY.md) — durable memory; PLAN profile blocks
- [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md) — notification pipeline (live LLM vs Log Day organizer)
- [`AGENT_REASONING.md`](AGENT_REASONING.md) — proactive heartbeat policy
