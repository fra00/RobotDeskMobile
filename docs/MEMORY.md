# User memory

> **Human-first guide (IT):** [`guides/MEMORIA.md`](guides/MEMORIA.md) — functional overview; [`guides/MEMORIA_TECNICA.md`](guides/MEMORIA_TECNICA.md) — cognitive agent wiring.

Durable facts about the user (name, preferences, routines) stored in the unified index `memory_documents.db` (SSOT). Legacy `user_memory.db` remains on disk for migration only; all runtime reads and writes use unified. Runtime access: [`UnifiedMemoryFactory`](../app/src/main/java/com/example/mydeskrobot/memory/unified/UnifiedMemoryFactory.kt) (process-scoped singleton repository + projection sync).

**Read path (voice):** unified index via [`UnifiedMemoryRepository`](../app/src/main/java/com/example/mydeskrobot/memory/unified/UnifiedMemoryRepository.kt). Single `recallForQuestion()` → `MEMORIA` block each voice turn (`UnifiedRecallMemoryContextProvider`).

**Write path:** tools, extraction, settings editor, and consolidation write directly to unified (`memory_documents.db`). Heartbeat and contact resolvers read unified too.

## Channels

| Channel | When |
|---------|------|
| **Automatic extraction** | Standby + interval; LLM scans `conversationLog` (`MemoryExtractionScheduler`) |
| **LLM tools** | Explicit save/list/delete during dialogue |
| **Unified recall** | Single `recallForQuestion()` → `MEMORIA` block each voice turn (`UnifiedRecallMemoryContextProvider`) |
| **Spatial identity** | `DOVE SONO` block on localize queries (`SpatialContextProvider`); separate from RAG |
| **Settings editor** | Impostazioni → Memoria: edit, delete each row, reset all user-facing memory, Riorganizza |

## Unified recall (voice dialog)

Each voice turn: [`LlmMemoryRecallPlanner`](../app/src/main/java/com/example/mydeskrobot/integration/memory/LlmMemoryRecallPlanner.kt) (lightweight LLM, JSON only) produces a [`MemoryRecallPlan`](../app/src/main/java/com/example/mydeskrobot/reasoning/memory/MemoryRecallPlan.kt). [`UnifiedRecallMemoryContextProvider`](../app/src/main/java/com/example/mydeskrobot/integration/memory/UnifiedRecallMemoryContextProvider.kt) maps it to [`UnifiedMemoryRepository.recallForQuestion()`](../app/src/main/java/com/example/mydeskrobot/memory/unified/UnifiedMemoryRepository.kt) on index `memory_documents.db` and injects one **`MEMORIA`** block. Details: [`MEMORY_RECALL_PLANNER.md`](MEMORY_RECALL_PLANNER.md).

| Plan field | Examples | Recall behaviour |
|------------|----------|------------------|
| **Temporal (day)** | `SINGLE_DAY` + `focus_day_key` | All `EPISODE` + `REMINDER` for that `dayKey` (scope-linked); budget reserves min 20 non-episode rows |
| **Temporal (range)** | `WEEK`, `MONTH` | Recent episodes; `include_habit_summary` only when planner sets it |
| **recall_focus** | USER_FACTS, EPISODIC, MESSAGES, … | Adjusts ranking pool and prefer flags (see planner doc) |
| **search_queries** | 1–4 Italian phrases | Multi-query semantic merge (max score per document) |
| **Localize** | `localize_spatial` | Spatial docs excluded from RAG; identity via **`DOVE SONO`** block |
| **Vision refresh** | after `take_photo` in chain | Deterministic `visionCatalog()` — no planner LLM |

**Recall budget** (`MemoryRecallBudget`): max **60** rows in `MEMORIA`. Single-day queries cap episodes at **40** and guarantee **20** slots for reminders, user facts, habit summary, etc. Default (no day focus) reserves **15** `USER_FACT` rows when above score threshold.
**Semantic search (Phase 2):** ONNX embedder with automatic first-run download (~118 MB). Hybrid score = 0.7 cosine + 0.3 token; `minScore` 0.25 token-only / 0.40 hybrid. Details: [`MEMORY_EMBEDDING.md`](MEMORY_EMBEDDING.md). Without model → token fallback until download completes.

No per-profile caps (`QUERY`/`PLAN`/`VISION` gates removed from read path). `MemoryRetrievalProfile.VISION` still used for mid-chain photo refresh only.

### Usage counter (`useCount`)

Each time a document is **injected into the dialog prompt** (`UnifiedRecallMemoryContextProvider` → `markUsed`), `useCount` increments and `lastUsedAt` updates.

When the store exceeds the cap (`USER_FACING_MAX_ITEMS` = 300), `pruneIfNeeded` removes exactly the overflow count among **non-pinned** rows, ordered by **`useCount ASC`**, then `lastUsedAt ASC`, then `confidence ASC`. Rows with `isPinned = true` are never pruned.

**Under the cap:** no automatic deletion from background cleanup. Facts persist until explicit delete, reset, Riorganizza (LLM consolidation when gated), or prune at cap.

**Pinning (`isPinned`):** Room column on `memory_documents` (migration 2→3). Set explicitly by the LLM via `save_memory` / extractor (`pinned: true`) for user name (IDENTITY), allergies/emergencies, or “ricordalo sempre”. **Not** keyword-based. Legacy `MemorySafetyPinDetector` (Level 1) is **removed from runtime** write/prune paths; consolidation may still use semantic duplicate detection internally (`MemoryConsolidationApplicator`, `MemoryConsolidationCoverage`).

### Cognitive projections (operational → unified index)

Operational SSOT (alarms, lists, activity log, spatial) stays in legacy stores. Projections into `memory_documents.db` are **sync `suspend`** writes via [`UnifiedMemoryWriter`](../app/src/main/java/com/example/mydeskrobot/memory/unified/UnifiedMemoryWriter.kt), wrapped by [`MemoryProjectionGuard`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionGuard.kt) (verify + one retry + drift counter). See [`MEMORY_ACCESS.md`](MEMORY_ACCESS.md).

Weekly [`MemoryProjectionBootstrap`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionBootstrap.kt) runs [`MemoryProjectionReconciler`](../app/src/main/java/com/example/mydeskrobot/memory/unified/MemoryProjectionReconciler.kt) to repair missing projections. See [`MEMORY_REVIEW_FOLLOWUP.md`](MEMORY_REVIEW_FOLLOWUP.md).

**Consolidation** (`MemoryConsolidationService` / `replaceUserFacingWithConsolidated`): invoked from **Riorganizza** (automatic in standby when enabled + manual button), gated by [`MemoryReorganizePolicy`](../app/src/main/java/com/example/mydeskrobot/memory/MemoryReorganizePolicy.kt) / [`MemoryReorganizeService`](../app/src/main/java/com/example/mydeskrobot/memory/MemoryReorganizeService.kt). Pinned rows are excluded from LLM input and never deactivated. Merged rows keep `useCount` on unchanged ids via applicator.

### Vision mid-chain refresh

In multi-step tool chains, when `take_photo` returns an image the system prompt is **refreshed** with VISION catalog + **`VERIFICA VISIVA ISTANTANEA`** before the next LLM call (`ToolChainOrchestrator.onBeforeLlmTurn`). Memorized spatial landmarks are **not** valid for describing the current frame.

### Voice vs vision

- **Voice only** (no photo): answer from injected `MEMORIA` / `DOVE SONO` or call `list_memories` with topic `query`.
- **Photo in chain**: describe only what is visible; memory spatial landmarks overridden.

## Tools

| Tool | Role |
|------|------|
| `save_memory` | Upsert fact (`value`, optional `category`, `confidence`, optional `pinned`, optional `ttl_days` for autonomy) |
| `list_memories` | List/filter/search active memories (default: user-facing only; use `category` for OBSERVATION/INTENT/PATTERN) |
| `delete_memory` | By `memory_id` or by **topic** `query` (fuzzy match; removes all related memories) |

### Forgetting by topic

The user does **not** need the exact stored sentence. `delete_memory` with `query` set to topic keywords (e.g. `cane Brina`) uses `MemoryTopicMatcher` to soft-delete every active memory that scores above the threshold. The LLM should pass keywords extracted from the user request, not the full command phrase. Natural phrasing ("va bene allora dimentica il cane") goes through the dialog LLM → `delete_memory` tool.

## Settings

Impostazioni → Memoria: enable extraction, interval, **auto Riorganizza** (on/off), **min rows** and **cooldown days** for consolidation, **editable list** of all memories (Salva / Elimina per riga), reset all, **Riorganizza ora** (same gates as auto).

| Setting (DataStore) | Default | Role |
|---------------------|---------|------|
| `auto_reorganize_enabled` | true | Standby tick runs `MemoryReorganizeService.runAutoIfDue()` |
| `reorganize_min_rows` | 100 | Min user-facing rows before LLM consolidation |
| `reorganize_cooldown_days` | 7 | Min days since last successful reorganize |
| `last_manual_reorganize_at_ms` | — | Timestamp of last successful run (auto or manual) |

### Duplicate handling

- **On save** (`upsertUserFacingFact`): merges into an existing row only on **exact** match (same category + normalized value). Paraphrases and homonyms (e.g. user name vs contact) stay separate rows.
- **Riorganizza** (automatica in standby + manuale): richiede almeno **N** righe user-facing (default **100**, configurabile) e **D** giorni dall’ultima riorganizzazione riuscita (default **7**, configurabile). LLM configurato. Esegue `MemoryConsolidationService` (force se manuale), poi `pruneIfNeeded` (cap 300). L’auto-run è controllata da **Riorganizza automaticamente** in Impostazioni → Memoria; viene valutata a ogni ciclo standby dello scheduler estrazione.
- **Auto after extraction / save_memory**: `pruneIfNeeded` only when over cap — no fuzzy dedup on write.
- **LLM consolidation**: pinned rows excluded from input; semantic merge only inside consolidation applicator/coverage (not on write path).

## Categories

**User-facing** (visible in settings, `list_memories`, prompt injection): `IDENTITY`, `PREFERENCE`, `ROUTINE`, `FACT`

**Robot-internal** (heartbeat autonomy only; hidden from user UI and voice shortcuts):

| Category | Default TTL | Purpose |
|----------|-------------|---------|
| `OBSERVATION` | 7 days | Dated contextual notes across heartbeats |
| `INTENT` | 1 day | Active autonomous monitoring goal (max 3) |
| `PATTERN` | 30 days | Emerging pattern not yet ROUTINE |

Rows with `expiresAt` are pruned automatically before each heartbeat tick. Settings **reset all** clears **user-facing** rows only; robot-internal INTENT/OBSERVATION remain until TTL or explicit `delete_memory`. Full wipe is not exposed as a voice command.

### Heartbeat injection

`HeartbeatContextBuilder` injects active INTENT, recent OBSERVATION, and active PATTERN into `[SYSTEM_INPUT: heartbeat]` as `OBIETTIVI ATTIVI`, `OSSERVAZIONI RECENTI`, and `PATTERN EMERGENTI` — the LLM should read these blocks without calling `list_memories` every tick.

`weekly_reflection` should save proactivity tuning as `PATTERN` (robot-internal), not `PREFERENCE`. Reserve `PREFERENCE` for genuine lasting user tastes.

## Storage channel semantics (when the user says "ricorda")

Choose the tool by what the information **is**, not by the verb alone. See [PROMPT_PHILOSOPHY.md](PROMPT_PHILOSOPHY.md).

| Channel | Tool | Semantics |
|---------|------|-----------|
| Long-term user knowledge | `save_memory` | Still relevant weeks later (identity, tastes, routines) |
| Actionable / written items | `add_list_item` | TODO, NOTE, SHOPPING — day-to-day, checkable |
| Timed alert | `set_reminder` | Robot speaks/notifies at a concrete time |

| Kind of information | Tool | Why |
|---------------------|------|-----|
| Stable facts about the user (name, tastes, pet name) | `save_memory` | Still relevant weeks later |
| Single task, even with "domani" | `add_list_item` TODO | Actionable, not profile |
| Free text / appointment note | `add_list_item` NOTE | Written record, not long-term memory |
| Shopping | `add_list_item` SHOPPING | Checkable list |
| Alert at a specific time | `set_reminder` | Robot must speak/notify then |

Constraints (not a flowchart): one-off *"domani devo fare X"* → **not** `save_memory`; use TODO unless the user wants an alarm (`set_reminder`). Recurring *"ogni martedì …"* → `save_memory` category `ROUTINE`.

### Relative dates at save time

When list items or memories are persisted, [`RelativeDateNormalizer`](../app/src/main/java/com/example/mydeskrobot/domain/time/RelativeDateNormalizer.kt) resolves `oggi`, `domani`, `dopodomani`, and **bare** weekday names to an absolute Italian date (e.g. `il 3 giugno 2026`). Recurring routines keep weekday as subject: `ogni martedì`, **`il venerdì`**, **`di venerdì`** (every that day), `venerdì di solito`, **weekday ranges** (`dal lunedì al sabato`, `da martedì a venerdì`, `tra/fra … e …`, `fino al …` when another weekday precedes). Example: "il venerdì esco" and "dal lunedì al sabato lavora" stay as-is; bare "venerdì fai questo" becomes `il 12 giugno 2026 fai questo` if today is that Friday.

Prompt details: `llm_system_prompt.txt` section **STORAGE CHANNEL SEMANTICS**.

## Manual QA checklist

1. "Come si chiama il mio cane?" (no photo) → name from MEMORIA recall or `list_memories`
2. "Controlla la memoria sul cane" → states facts, not only count
3. "Fai una foto" → describes scene from image; uses entity names if in catalog
4. "Cosa devo fare oggi/domani" → reminders, todos, episodes for resolved day in MEMORIA
5. "Cosa ho fatto ieri" → all episodes for yesterday (including extractor), not capped slice
6. "Dove siamo?" (no photo) → studio from DOVE SONO SSOT
7. "Dove siamo?" + photo (phone face-up) → describes ceiling, not memorized landmarks
8. Parafrase same fact (IT + EN) → **two** USER_FACT rows until Riorganizza compacts
9. Allergy / name with `pinned: true` → survives simulated prune over cap 300
10. Auto Riorganizza: standby + ≥ min rows + cooldown elapsed → consolidation log; disabled toggle skips run

See also: `app/src/main/assets/prompts/memory_extractor_prompt.txt`, `memory_consolidation_prompt.txt`.
