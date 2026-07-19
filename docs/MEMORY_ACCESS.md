# Unified Memory Access — Contract

## Principle

**Voice dialog retrieval** uses a single path:

```
User question → LlmMemoryRecallPlanner (JSON) → recallForQuestion() → MEMORIA block → LLM
```

Planner contract: [`MEMORY_RECALL_PLANNER.md`](MEMORY_RECALL_PLANNER.md). On planner failure the voice turn ends with `ReasoningResult.Error` (no rule-based fallback).

Implementation: `UnifiedRecallMemoryContextProvider` → `UnifiedMemoryRepository.recallForQuestion()` → `RecallContextFormatter`.

Physical database count is an implementation detail; the robot must treat **MEMORIA** as the only cognitive store for dialog.

## What is memory vs inbox vs execution

| Concept | Store | Dialog recall? | Notes |
|---------|-------|----------------|-------|
| Episodes (activities, plans, social, notifications) | `activity_log.db` + `memory_documents` (EPISODE) | Yes | Writer keeps both in sync |
| Unread notifications | EPISODE with `isUnread=true` | Yes (`NOTIFICHE_NON_LETTE`) | Cleared on read/dismiss |
| User facts / autonomy | `memory_documents` (USER_FACT / AUTONOMY) | Yes | `save_memory`, extractors |
| Reminders (scheduled) | `scheduled_tasks` + EPISODE/REMINDER projection | Yes | AlarmManager fires independently |
| Lists | `list_items` + LIST_ITEM projection | Yes | Checkbox state = operational only |
| Spatial | `spatial_places` + SPATIAL projection | Yes | |
| Habit summary | `activity_habit_profile` + HABIT_SUMMARY | Yes | |
| Pending inbox UI | Derived from unread EPISODE + deferred queue + pending reminders | Partial | Not a separate memory store |
| Alarm / reminder fire | `ScheduledTaskRepository`, AlarmManager | No | Deterministic execution |
| List item checked | `ListItemRepository` | No | Operational state |

## Write rule

Every fact that should be **recallable in dialog** must pass through `UnifiedMemoryWriter` in the same `suspend` scope:

1. Write operational store (activity log, scheduled task, list, spatial, habit profile).
2. Write cognitive index (`memory_documents.db`) — **always**, not optional.
3. On index failure: `Log.e` + `MemorySettingsRepository.recordProjectionDrift()`; caller receives `indexOk=false`.

`MemoryProjectionSync` is removed; use `UnifiedMemoryWriter` only.

## Reconcile / bootstrap

`MemoryProjectionBootstrap` + `MemoryProjectionReconciler` are **cold-start repair** for legacy drift, not the normal write path. After unified writer rollout, episode reconcile at startup logs a **warning** when `repaired > 0` (historical drift).

Weekly full reconcile remains for reminders, lists, spatial legacy data.

## Exceptions (documented)

- **Mic off / input rejected**: notifications not accepted by `InputPolicyEngine` are **not** written to memory (no episode).
- **Sensitive content** (OTP, banking): same filters as `NotificationInputSource`; skip `saveNotificationEpisode`.
- **USER_FACT** via `SaveMemoryTool` writes directly to unified index (no separate operational store).
- **Deterministic execution** (alarm fire, list checkbox) does not go through RAG recall.

## Write-path audit

| Channel | Operational | Kind | Writer method | Call sites |
|---------|-------------|------|---------------|------------|
| Episodes | `ActivityLogRepository` | EPISODE | `saveEpisode` | `LogDailyActivityTool`, `ActivityExtractionService`, `onReminderFired` |
| Notifications | `ActivityLogRepository` | EPISODE + unread | `saveNotificationEpisode` | `ConversationViewModel.onSystemInputReceived` |
| Promemoria | `ScheduledTaskRepository` | REMINDER | `saveReminder` / `cancelReminder` | `ReminderTool`, `DeleteReminderTool`, `ReminderAlarmReceiver` |
| Liste | `ListItemRepository` | LIST_ITEM | `saveListItem` / `removeListItem` | `AddListItemTool`, `UpdateListItemTool`, `DeleteListItemTool` |
| Spatial | `SpatialPlaceRepository` | SPATIAL | `savePlace` / `setCurrentPlace` | `SavePlaceTool`, `SetCurrentPlaceTool` |
| Habit | `activity_habit_profile` | HABIT_SUMMARY | `saveHabitSummary` | `ActivityHabitSummarizer` |
| USER_FACT | — | USER_FACT | `UnifiedMemoryRepository.upsertUserFacingFact` | `SaveMemoryTool`, `MemoryExtractionService`; consolidation via `MemoryReorganizeService` |

## Recall sections (MEMORIA block)

| Section | Source |
|---------|--------|
| `NOTIFICHE_NON_LETTE` | EPISODE where `isUnread=true` |
| `EPISODI` | EPISODE (includes archived messages/notifications) |
| `PROMEMORIA` | REMINDER |
| `LISTE` | LIST_ITEM |
| `SPAZIO` | SPATIAL |
| `PROFILO ABITUDINI` | HABIT_SUMMARY |
| `FATTI` | USER_FACT + AUTONOMY |

Unread episodes from the last 7 days are included in recall even without a temporal cue (light score boost).

## Acceptance checklist (manual QA)

1. Episodio via `log_daily_activity` → turno successivo → recall OK senza reconcile.
2. Notifica WhatsApp (mic attivo) → EPISODE unread → “riepilogo messaggi di ieri” via MEMORIA.
3. “Leggi le notifiche” → legge da `NOTIFICHE_NON_LETTE` → mark read → sparisce da inbox.
4. “Segna come lette” → `isUnread=false`.
5. Promemoria creato → recall “cosa devo fare domani”.
6. Log Day UI mostra `[non letto]` su notifiche non lette.
7. Prompt: nessun riferimento a `QUEUED NOTIFICATIONS`.

## Related docs

- `docs/guides/MEMORIA.md` — human-first overview (IT)
- `docs/guides/MEMORIA_TECNICA.md` — recall/write paths, code map (IT)
- `docs/MEMORY.md` — user memory, extractors, consolidation
- `docs/ACTIVITY_LOG.md` — episodic log, kinds, extractor
- `docs/ROBOT_CONTEXT.md` — silent notification mode (TTS only, not memory ingest)
