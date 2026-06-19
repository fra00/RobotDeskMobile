# User memory

Durable facts about the user (name, preferences, routines) stored in Room (`user_memory.db`).

## Channels

| Channel | When |
|---------|------|
| **Automatic extraction** | Standby + interval; LLM scans `conversationLog` (`MemoryExtractionScheduler`) |
| **LLM tools** | Explicit save/list/delete during dialogue |
| **Prompt injection** | Context-aware profiles each turn (`MemoryPromptContextProviderImpl`) |
| **Day context** | Promemoria oggi + todo/note aperti when planning intent (`DayContextPromptProviderImpl`) |
| **Voice shortcuts** | "cosa sai di me", "dimentica …", "reset memoria" (no LLM) |
| **Settings editor** | Impostazioni → Memoria: edit or delete each row manually |

## Contextual retrieval profiles

Each voice turn runs [`MemoryIntentDetector`](../app/src/main/java/com/example/mydeskrobot/reasoning/memory/MemoryIntentDetector.kt) on the user phrase (no extra LLM call). [`MemoryPromptContextProviderImpl`](../app/src/main/java/com/example/mydeskrobot/integration/memory/MemoryPromptContextProviderImpl.kt) injects a different memory block:

| Profile | Trigger examples | Injected content |
|---------|------------------|------------------|
| **QUERY** | "come si chiama il mio cane", "ricordi", "controlla la memoria" | IDENTITY + expanded fuzzy search on phrase |
| **VISION** | "fai una foto", "guarda", "cosa vedi" | FACT + ROUTINE entity catalog for labeling photos |
| **PLAN** | "cosa devo fare oggi", "agenda", "domani", "riunioni" | ROUTINE memories + **CONTESTO GIORNO** (reminders, todos, notes) + **EPISODI PROSSIMI** (Log Day) |
| **LEISURE** | "cosa posso guardare", "tempo libero" | PREFERENCE + phrase search |
| **DEFAULT** | general chat | IDENTITY + fuzzy search (max 10) |

Mixed intents merge blocks (e.g. photo + dog name → VISION catalog + QUERY search).

### Usage counter (`useCount`)

Each time a memory row is **injected into the dialog prompt** (`MemoryPromptContextProviderImpl` → `markUsed`), `useCount` increments and `lastUsedAt` updates. Extraction and manual saves do **not** bump the counter.

When the store exceeds the cap, `pruneIfNeeded` removes low-priority rows ordered by `confidence ASC`, then `useCount ASC`, then `lastUsedAt ASC` — rarely injected memories are pruned first.

**Consolidation** (`replaceUserFacingWithConsolidated`) creates fresh rows with `useCount = 0`; merging source counts is deferred.

### Vision mid-chain refresh

In multi-step tool chains, when `take_photo` returns an image the system prompt is **refreshed** with the VISION profile before the next LLM call (`ToolChainOrchestrator.onBeforeLlmTurn`).

### Voice vs vision

- **Voice only** (no photo): entity questions use QUERY profile; LLM must answer from injected `value` fields or call `list_memories` with a topic `query` — not report `count` alone.
- **Photo**: use `KNOWN ENTITIES FOR VISION` to name recognized objects (e.g. dog name from memory).

## Tools

| Tool | Role |
|------|------|
| `save_memory` | Upsert fact (`value`, optional `category`, `confidence`, optional `ttl_days` for autonomy) |
| `list_memories` | List/filter/search active memories (default: user-facing only; use `category` for OBSERVATION/INTENT/PATTERN) |
| `delete_memory` | By `memory_id` or by **topic** `query` (fuzzy match; removes all related memories) |

### Forgetting by topic

The user does **not** need the exact stored sentence. `delete_memory` with `query` set to topic keywords (e.g. `cane Brina`) uses `MemoryTopicMatcher` to soft-delete every active memory that scores above the threshold. The LLM should pass keywords extracted from the user request, not the full command phrase.

Voice `dimentica …` uses the same matcher.

## Settings

Impostazioni → Memoria: enable extraction, interval, **editable list** of all memories (Salva / Elimina per riga), reset all, reorganize duplicates.

### Duplicate handling

- **On save** (`upsert`): merges into an existing row when the value is semantically duplicate (same category), not only exact text match.
- **Riorganizza ora**: runs Kotlin dedup (`reorganize`) then **LLM compaction** of all user-facing rows (`MemoryConsolidationService`) — merges fragments and cross-category near-duplicates into canonical `(CATEGORY) value` lines.
- **Automatic**: after each memory extraction cycle in standby, compaction runs if content hash changed and row count > 3 (skips unchanged memory).
- **Legacy dedup**: `reorganize()` still runs before compaction as a lightweight safety net.

## Categories

**User-facing** (visible in settings, "cosa sai di me", prompt injection): `IDENTITY`, `PREFERENCE`, `ROUTINE`, `FACT`

**Robot-internal** (heartbeat autonomy only; hidden from user UI and voice shortcuts):

| Category | Default TTL | Purpose |
|----------|-------------|---------|
| `OBSERVATION` | 7 days | Dated contextual notes across heartbeats |
| `INTENT` | 1 day | Active autonomous monitoring goal (max 3) |
| `PATTERN` | 30 days | Emerging pattern not yet ROUTINE |

Rows with `expiresAt` are pruned automatically before each heartbeat tick. `reset memoria` / settings reset clears **user-facing** rows only; robot-internal INTENT/OBSERVATION remain until TTL or explicit `delete_memory`.

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

1. "Come si chiama il mio cane?" (no photo) → name from memory or `list_memories`
2. "Controlla la memoria sul cane" → states facts, not only count
3. "Fai una foto" → describes scene; uses entity names if in catalog
4. "Cosa devo fare oggi" → cites CONTESTO GIORNO + EPISODI PROSSIMI (reminders + todos + Log Day)
5. "Cosa posso guardare oggi" (MotoGP in PREFERENCE) → leisure suggestion, not agenda
6. Multi-angle scan: after each `take_photo`, vision entities available in prompt

See also: `app/src/main/assets/prompts/memory_extractor_prompt.txt`.
