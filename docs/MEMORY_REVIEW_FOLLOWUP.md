# Memory Review Follow-up (2.1 / 2.2 / 2.3)

Implementation of the third-party memory review follow-up. Scope: projection consistency, recall budget, safety pinning (Level 1 only). No Room schema migration, no voice “ricordalo sempre”.

## 2.1 MUST — Projection guard + reconcile

### Problem

Cognitive projections (`memory_documents.db`) are synced **synchronously** (`suspend`) from operational stores (reminders, lists, activity log, spatial). Risks before this work:

- Nullable `projectionSync` on some tools → silent skip
- No verify/retry after write
- No periodic reconciliation if index drifts

### Solution

| Component | Role |
|-----------|------|
| [`MemoryProjectionGuard`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionGuard.kt) | Wraps each projection write: verify `externalRef` + `isActive` + non-blank value; **one retry**; on double failure → drift counter in DataStore |
| [`MemoryProjectionSync`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionSync.kt) | All handlers delegate to guard |
| [`MemoryProjectionReconciler`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionReconciler.kt) | Idempotent rebuild from `ScheduledTaskRepository`, `ListItemRepository`, `ActivityLogRepository`, spatial stores |
| [`MemoryProjectionBootstrap`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionBootstrap.kt) | Weekly reconcile (7 days) on app start via `ConversationViewModel` |

**DataStore keys** (`MemorySettingsRepository`): `projection_drift_count`, `last_projection_drift_at_ms`, `last_projection_reconcile_at_ms`.

**Wiring:** `UnifiedMemoryFactory` always provides `projectionSync`. `ReminderTool` logs a warning if `projectionSync == null` (Context-only test constructors).

## 2.2 SHOULD — Recall budget + temporal scope

### Problem

`recallForQuestion()` merged all kinds then `.take(60)`. Dense days (50+ episodes) could crowd out semantically relevant `USER_FACT` rows.

### Solution

| Component | Role |
|-----------|------|
| [`MemoryRecallBudget`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryRecallBudget.kt) | `TOTAL=60`, `EPISODE_MAX_SINGLE_DAY=40`, `NON_EPISODE_MIN_SINGLE_DAY=20`, `EPISODE_MAX_WIDE_RANGE=10`, `USER_FACT_MIN_DEFAULT=15` |
| [`applyRecallBudget`](../app/src/main/java/com/example/mydeskrobot/memory/unified/UnifiedMemoryRepository.kt) | Partition by `MemoryDocumentKind` before global cap |
| [`TemporalScope`](../app/src/main/java/com/example/mydeskrobot/reasoning/memory/TemporalScope.kt) | `NONE`, `SINGLE_DAY`, `WEEK`, `MONTH` |
| [`MemoryRecallCueResolver`](../app/src/main/java/com/example/mydeskrobot/reasoning/memory/MemoryRecallCueResolver.kt) | Italian cues: *questa settimana*, *settimana scorsa*, *questo mese*, *mese scorso* |

**Routing:**

- **SINGLE_DAY** — all episodes for `focusDayKey` (cap 40) + min 20 non-episode slots
- **WEEK / MONTH** — inject `HABIT_SUMMARY` (score 0.92) + at most 10 recent episodes; no full granular episode load
- **NONE** — reserve min 15 `USER_FACT` when present above threshold

[`RecallContextFormatter`](../app/src/main/java/com/example/mydeskrobot/integration/context/RecallContextFormatter.kt) labels wide-range blocks (“questa settimana”, “questo mese”).

## 2.3 NICE — Safety pin Level 1

### Scope

Deterministic Italian health/safety keywords only. **No** `isPinned` column. **No** voice pin command (backlog).

### Solution

[`MemorySafetyPinDetector`](../app/src/main/java/com/example/mydeskrobot/memory/MemorySafetyPinDetector.kt):

- Categories: `IDENTITY`, `FACT`
- Keywords: allergia, intolleranza, celiachia, diabete, epipen, emergenza, 118, 112, ospedale, farmaco, …
- On match: `confidence` floor **0.95**
- `pruneIfNeeded`: excludes safety-pinned rows (re-checked on `value` at prune time)

Applied on: `upsertUserFacingFact`, `replaceUserFacingWithConsolidated`, extraction (`MemoryExtractionService` → unified upsert).

## Acceptance (verified by unit tests)

- Projection verify + retry + drift path (`MemoryProjectionGuardTest`)
- 50 episodes + user facts on single day → ≥3 `USER_FACT` in recall, total ≤ 60
- “Questa settimana” → `TemporalScope.WEEK` + `HABIT_SUMMARY` in context
- Safety allergy fact survives prune at cap 300
- Full memory test suite green (`memory.*`, `integration.memory.*`, `reasoning.memory.*`)

## Backlog (out of scope)

- Level 2 pinning: `isPinned` schema + voice “ricordalo sempre”
- H2 heartbeat `speak_confidence` threshold
