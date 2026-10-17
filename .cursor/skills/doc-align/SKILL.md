---
name: doc-align
description: >-
  Verifies My Desk Robot documentation alignment (AGENTS.md, docs/, guides,
  prompts, tool catalog). Runs structural script then semantic domain review.
  Use when the user asks to check docs, controllare la documentazione,
  allineare la doc, doc drift, docs out of date, or verify AGENTS/TODO vs code.
---

# Doc align — My Desk Robot

## When triggered

User asks to check/align documentation (IT or EN). Do **not** wait for confirmation — run the full workflow.

## Workflow (mandatory order)

```
Progress:
- [ ] 1. Structural script
- [ ] 2. Scope domains
- [ ] 3. Semantic spot-check
- [ ] 4. Report + optional fixes
```

### 1. Structural script

From repo root:

```bash
python scripts/check_docs_alignment.py
```

If the user named a domain (memory, proactivity, proxy, mood, translation, activity, tools, todo):

```bash
python scripts/check_docs_alignment.py --domain <name>
```

For machine parse:

```bash
python scripts/check_docs_alignment.py --json
```

**Treat every `[FAIL]` as must-fix** (or list as open debt if user said report-only).  
`[WARN]` = mention briefly; fix only if obvious (orphan prompt naming).

### 2. Scope domains

| User said / recent work | Domain keys |
|-------------------------|-------------|
| (none / “tutta la doc”) | `todo` + scan `docs/TODO.md` Completato vs backlog; spot-check 1–2 hot domains |
| memoria / recall / IDENTITY | `memory` |
| proattività / wellness / habit | `proactivity` |
| proxy / notifiche LAN | `proxy` |
| umore / occhi / emotion | `mood` |
| traduzione / translator | `translation` |
| Health Connect / Log Day | `activity` |
| tool table / AGENTS tools | `tools` |

Domain → file map: [reference.md](reference.md).

### 3. Semantic spot-check

For each scoped domain, read the SSOT doc(s) and verify against code (not vibes):

1. **Tools** named in doc exist in `integration/tool/**` (`override val name`).
2. **Behaviors** marked “implemented” / “v1” match runtime (no retired path still described as live).
3. **Prompt assets** cited exist under `app/src/main/assets/prompts/`.
4. **AGENTS.md** “Fatto / Backlog / Avvertenze” vs `docs/TODO.md` (TODO is roadmap SSOT).
5. **Guides** (`docs/guides/`) don’t contradict specs (`docs/`) on user-visible behavior.
6. **No keyword-gate regressions** documented as removed but still present in code (e.g. notification voice shortcut, name phrase routers).

Do **not** rewrite large docs unless the user asked to fix. Prefer a punchy gap list.

### 4. Report format

```markdown
## Doc align

**Structural:** OK | FAILED (N fails, M warns)

### Failures
- …

### Warnings
- …

### Semantic gaps
- [domain] …

### Suggested fixes
1. … (file paths)
```

If user said **fix** / **sistema** / **allinea**: apply minimal doc edits (and code only if doc is right and code is wrong — ask first when ambiguous). Re-run the script after fixes.

## Anti-patterns

- Do not invent missing features as “documented debt” without checking `docs/TODO.md`.
- Do not treat `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` as roadmap (banner: use `TODO.md`).
- Do not expand scope into unrelated refactors.

## Extra detail

- Domain file map & severity: [reference.md](reference.md)
