# LLM Memory Recall Planner

Lightweight **planning-only** LLM call each voice turn. Output is strict JSON (`MemoryRecallPlan`) that parameterizes deterministic RAG (`UnifiedMemoryRepository.recallForQuestion`). The planner does **not** answer the user.

## Flow

```
User voice → ReasoningEngineImpl.resolveRecallPlan()
  → LlmMemoryRecallPlanner (JSON only)
  → MemoryRecallPlan.toRequest()
  → UnifiedRecallMemoryContextProvider → MEMORIA block
  → dialog LLM
```

**One plan per turn**, computed in `ReasoningEngineImpl.buildPromptWithContext` and reused for memory + spatial localize flag.

## Exceptions (no planner LLM)

| Case | Plan source |
|------|-------------|
| `MemoryRetrievalProfile.VISION` or `freshVisionVerify` after `take_photo` | `MemoryRecallPlan.visionCatalog()` |
| Blank user text, `[SYSTEM_INPUT: …]` heartbeat | `recallPlan = null` → no MEMORIA from question |
| Planner returns `skip_recall: true` | No MEMORIA (greetings, farewells, small talk) — turn continues |
| No `MemoryContextProvider` | `recallPlan = null` |

## Failure policy (no fallback)

If the planner fails, the **voice turn aborts** with `ReasoningResult.Error` and an Italian user-safe message. There is **no** hidden rule-based recall.

| Failure | User message (summary) |
|---------|------------------------|
| `NotConfigured` | LLM non configurato |
| `LlmError` | Errore nel preparare il contesto memoria |
| `EmptyOutput` | Risposta vuota |
| `ParseError` | Piano non valido |

Log tag: `RECALL_PLANNER_FAILED` (reason + truncated user text).

## JSON schema

Prompt asset: [`memory_recall_planner_prompt.txt`](../app/src/main/assets/prompts/memory_recall_planner_prompt.txt)

```json
{
  "skip_recall": false,
  "temporal_scope": "NONE | SINGLE_DAY | WEEK | MONTH",
  "focus_day_key": "yyyy-MM-dd | null",
  "recall_focus": "USER_FACTS | EPISODIC | MESSAGES | PLANNING | SPATIAL | GENERAL",
  "search_queries": ["1-4 Italian strings"],
  "include_habit_summary": false,
  "localize_spatial": false
}
```

`skip_recall: true` — saluti, ringraziamenti, chiusure conversazione (“buona notte”, “ciao”, “grazie”): nessun retrieval, turno **non** in errore.

`focus_day_key` must be ISO date when `temporal_scope` is `SINGLE_DAY`. Relative dates (ieri, domani, weekday) are resolved by the planner using `{{CURRENT_DATETIME}}` in the prompt.

## Mapping plan → `MemoryRecallRequest`

| Plan field | Request field |
|------------|---------------|
| `temporal_scope`, `focus_day_key` | `temporalScope`, `focusDayKey` |
| `recall_focus` | `preferUserFacts`, `preferEpisodicDetail`, scope tweaks |
| `search_queries` | `searchQueries` (multi-query RAG merge, max score per doc) |
| `include_habit_summary` | `includeHabitSummary` (replaces automatic WEEK pin) |
| `localize_spatial` | `localizeQuery` |
| `skip_recall` | No `MemoryRecallRequest` — empty MEMORIA block |
| `visionCatalog()` | `includeVisionCatalog`, `excludeSpatialLandmarks` when fresh photo |

### `RecallFocus` → flags

| Focus | Effect |
|-------|--------|
| `USER_FACTS` | `preferUserFacts=true`; rank all user facts via `search_queries` |
| `EPISODIC` | `preferEpisodicDetail=true` |
| `MESSAGES` | If scope `NONE` → expand to `WEEK` |
| `PLANNING` | Uses plan temporal scope / day |
| `SPATIAL` | `localizeQuery=true`; spatial landmarks excluded from RAG pool |
| `GENERAL` | No prefer flags |

## RAG (unchanged role)

After the plan: `MemorySearchScorer` + `MemoryRecallBudget` (60) + `RecallContextFormatter`. Multiple `search_queries` each run rank; documents keep **max score** across queries.

`include_habit_summary` pins `HABIT_SUMMARY` only when the planner sets it `true` (not automatic on WEEK anymore).

## Key files

| Layer | File |
|-------|------|
| Model | `reasoning/memory/MemoryRecallPlan.kt`, `RecallFocus.kt`, `MemoryRecallPlanParser.kt` |
| Interface | `reasoning/memory/MemoryRecallPlanner.kt` |
| Failure | `reasoning/memory/RecallPlanFailure.kt`, `RecallPlanException.kt` |
| LLM impl | `integration/memory/LlmMemoryRecallPlanner.kt` |
| Orchestration | `reasoning/ReasoningEngineImpl.kt` |
| Context | `integration/memory/UnifiedRecallMemoryContextProvider.kt` |
| Recall | `memory/unified/UnifiedMemoryRepository.recallForQuestion()` |
| Wiring | `integration/ReasoningModule.kt` |

## Tests

- `MemoryRecallPlanParserTest` — JSON valid/invalid, fence stripping
- `MemoryRecallPlanMappingTest` — focus → request flags
- `LlmMemoryRecallPlannerTest` — stub LLM + golden fixtures in `app/src/test/resources/recall_planner/`
- `UnifiedRecallMemoryContextProviderTest` — plan fixtures + fake DAO
- `UnifiedMemoryRepositoryTest` — recall with `searchQueries` / `preferUserFacts`

## Removed (legacy)

- `MemoryRecallCueResolver` — Italian keyword rules
- `UserFactRecallScorer` — synonym expansion for user facts (replaced by planner `search_queries`)
