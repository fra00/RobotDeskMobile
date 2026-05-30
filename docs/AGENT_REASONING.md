# Agent reasoning policy

How the desk robot LLM should solve tasks. Encoded in `app/src/main/assets/prompts/llm_system_prompt.txt` under **AUTONOMOUS PROBLEM SOLVING**.

## Two-level model

1. **Direct tool** — one catalog tool closes the task (`get_weather`, `set_reminder`, …).
2. **Composition** — macro-problem → steps → one tool per step (or LLM-only step via `reply`).

## Abstract → concrete

Playful or vague user phrases must be translated into observable steps, e.g. vision (`take_photo`), speech (`reply` + TTS), search (`web_search` → `fetch_url`).

Not every abstract game is implementable today; the model should say what is missing instead of hallucinating tools.

## Extensibility

New tools widen what can be composed. Prompt policy stays stable; tool list in `ReasoningModule` grows.

See also `docs/TOOL_ARCHITECTURE.md` (tool chaining) and `docs/WEB_SEARCH.md`.
