# Doc-align reference

## Domain → SSOT files

| Domain | Spec / index | Guide (if any) | Code anchors (start here) |
|--------|--------------|----------------|---------------------------|
| `memory` | `docs/MEMORY.md`, `MEMORY_ACCESS.md`, `MEMORY_RECALL_PLANNER.md`, `MEMORY_EMBEDDING.md` | `guides/MEMORIA.md`, `MEMORIA_TECNICA.md` | `memory/unified/`, `integration/tool/local/SaveMemoryTool.kt`, `UserNameIdentityPolicy` |
| `proactivity` | `docs/PROACTIVE_ARCHITECTURE.md`, `HEARTBEAT_ARCHITECTURE.md` | `guides/PROATTIVITA.md` | `integration/wellness/`, `integration/predictivity/`, `ProactiveSpeakGate` |
| `proxy` | `docs/NOTIFICATION_PROXY.md`, `INPUT_ARCHITECTURE.md` | `guides/PROXY_NOTIFICHE.md` | `integration/input/notification/proxy/`, `data/proxy/` |
| `mood` | `docs/MOOD.md`, `ROBOT_EXPRESSIONS.md` | `guides/UMORE.md`, `UMORE_SMOKE.md` | `domain/mood/`, `MoodManager` |
| `translation` | `docs/TRANSLATION_MODE.md` | — | `SetTranslationModeTool`, `TranslationModeRepository`, TTS `languageTag` |
| `activity` | `docs/ACTIVITY_LOG.md` | — | `data/activitylog/`, `data/health/` |
| `tools` | `docs/TOOL_ARCHITECTURE.md`, `AGENTS.md` tool table | — | `integration/ReasoningModule.kt`, `integration/tool/**` |
| `todo` | `docs/TODO.md`, `AGENTS.md` Fatto/Backlog | — | recent git / Completato section |

## Severity

| Kind | Action |
|------|--------|
| Dead markdown link / missing cited file | Fail — fix path or restore file |
| Tool in code missing from AGENTS table | Fail — add row |
| Tool in AGENTS missing from code | Fail — remove row or restore tool |
| Retired draft cited as live SSOT | Fail — remove cite or mark removed |
| Prompt referenced but missing on disk | Fail |
| Orphan prompt on disk | Warn |
| Spec says X, code does Y | Semantic gap — fix doc or code per user intent |
| Guide contradicts spec | Prefer updating guide to match spec (or both if product change) |

## Script checks (structural)

`scripts/check_docs_alignment.py`:

1. Dead links in `AGENTS.md`, `docs/**/*.md`, `.cursor/rules/*.mdc`
2. Tool name parity: `override val name` vs AGENTS tool table
3. Retired draft citations without “rimosso/removed” wording
4. Prompt refs vs `assets/prompts`
5. Optional `--domain` file existence

## Roadmap SSOT

Always trust `docs/TODO.md` over draft vision checklists.
