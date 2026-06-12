# Agent reasoning policy



How the desk robot LLM should solve tasks. Encoded in `app/src/main/assets/prompts/llm_system_prompt.txt` under **AUTONOMOUS PROBLEM SOLVING**.



## Two-level model



1. **Direct tool** — one catalog tool closes the task (`get_weather`, `set_reminder`, …).

2. **Composition** — macro-problem → steps → one tool per step (or LLM-only step via `reply`).



All user and system turns use a **single engine**: `ToolChainOrchestrator` (via `ReasoningEngineImpl`) — vision, agenda, web, body tools share the same loop.



## Chain speech policy (Kotlin + prompt)



| LLM JSON | Intermediate TTS | Final TTS |

|----------|------------------|-----------|

| `tool_call` + `chain_status: in_progress` | **Suppressed** (`ChainSpeechPolicy` → `suppressIntermediateSpeech`) | — |

| `action: none` (answer or clarifying question) | — | **Spoken** |

| `tool_call` + `chain_status: complete` | May speak if reply present | Often final via `Success` |



Clarifying questions (*"Le chiavi erano sul tavolo o sulla scrivania?"*) must use **`action: none`** — never inside `in_progress`.



Prompt: **CHAIN EXECUTION RULES** in `llm_system_prompt.txt`.



## Abstract → concrete



Playful or vague user phrases must be translated into observable steps, e.g. vision (`take_photo`), speech (`reply` + TTS), search (`web_search` → `fetch_url`).



### Persistent search & spatial verify



Find/locate/**cerca**/verify goals (people **and objects** like computers, finger, keys) are **multi-step**: photo → if target missing, body rotate toward user hint (dietro di me, a sinistra, …) + photo again → repeat until found or ~5–6 angles, then concede.



- Small/mobile targets need **more angles** than one head turn.

- If the user **asserts** something exists (*"c'è un computer dietro di me"*) or says **cerca**, never answer *"non c'è"* after one photo — wrong angle, not proof of absence.



Orchestrator: `chain_status: in_progress` (max 10 steps). Prompt: **PERSISTENT SEARCH & SPATIAL VERIFY** + body spatial hints.



**No continuation guard** in Kotlin (by design): premature `action: none` is mitigated via prompt done criteria only.



Not every abstract game is implementable today; the model should say what is missing instead of hallucinating tools.



## Manual QA (after prompt / chain-silence changes)



| Scenario | Pass | Fail |

|----------|------|------|

| *"Cerca il mio dito"* (hand out of frame) | Silent during chain; ≥2 move+photo; final found or concede | Step-by-step narration; stop after 1 photo |

| *"Computer dietro di me, è acceso?"* | Rotates before *non c'è* | *Non c'è* after 1 photo |

| *"Cosa devo fare oggi?"* | Normal agenda answer | Regression / loop |

| Clarifying question (`action: none`) | Spoken to user | Silenced |



Rebuild app for asset prompts; body ON for vision tests.



## Extensibility



New tools widen what can be composed. Prompt policy stays stable; tool list in `ReasoningModule` grows.



See also `docs/TOOL_ARCHITECTURE.md` (tool chaining) and `docs/WEB_SEARCH.md`.


