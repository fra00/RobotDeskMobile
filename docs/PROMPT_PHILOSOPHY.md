# Prompt philosophy — capability catalog, not playbook

LLM prompts in this project describe **what the robot can do** and **hard limits**, not step-by-step scripts the model must follow.

## Three layers in every prompt

| Layer | Purpose | Example |
|-------|---------|---------|
| **Capabilities** | Tool catalog + hardware semantics | Joint names, `save_memory` vs `add_list_item` roles |
| **Constraints** | Non-negotiable technical or product rules | One image per LLM turn; `reply: ""` for silent body moves; `think` = orientation (goal/strategy when world_change/know/unknown/success/now), no multi-step plans in `think`; FIRE_AND_CHECK classify before tools; never TTS; no OTP in voice |
| **Illustrative examples** | Show valid JSON shape and plausible chains | Few-shot JSON uses `<MARKER>` placeholders in `reply`, not fixed Italian phrases |

The planner (the LLM) combines tools freely to reach the user's goal or heartbeat objective. See [TOOL_ARCHITECTURE.md](TOOL_ARCHITECTURE.md) §11 (autonomous tool chains).

## Cognitive agent layer (persona)

[`docs/nextPromptv1.md`](nextPromptv1.md) defines **who the robot is** (companion, not assistant) and autonomous policies (heartbeat relevance, Fire-and-Check, boundaries). That content lives in `llm_system_prompt.txt` as narrative constraints and optional patterns — **not** as rigid playbooks. Persona rules use the same JSON contract (`reply`, `emotion`, `user_tone`, `speak_confidence`, `action.chain_status`). See the mapping table at the top of `nextPromptv1.md`.

## Rules for authors

1. **Examples are not scripts.** Numbered sequences, few-shot JSON, and pattern names (centering, room exploration) are suggestions. The model may invent different valid chains.
2. **Constraints stay imperative.** Technical limits (vision: one photo per turn → write text notes in history) and product policy (speak_confidence on heartbeat) are real boundaries.
3. **Capabilities stay neutral.** Tool descriptions say what a tool does and what it combines with — not "always call X before Y".
4. **Optional patterns are optional.** Strategies like subject centering, multi-angle exploration, or mood-informed body expression are listed under *you may* / *if useful*, with soft limits (e.g. ~3 correction cycles), not mandatory decision trees.
5. **Storage = semantics, not flowchart.** Memory, lists, and reminders are defined by what information *is*, not by the verb "ricorda" alone.
6. **Autonomy parsimony is explicit.** OBSERVATION/INTENT are robot-internal (`MEMORY.md`); save only when decision-changing; max 3 INTENTs (app-enforced); when in doubt on heartbeat: silence, do not save.

## SSOT labels (runtime prompt navigation)

Cross-references in prompts use **labels** + section numbers — never duplicate full paragraphs elsewhere.

| Label | Authority | Injected when |
|-------|-----------|---------------|
| `JSON_CONTRACT` | §2 `llm_system_prompt.txt` | always (base) |
| `PERSISTENT_SEARCH` | §4 STEP 3 | always |
| `FIRE_AND_CHECK` | §4 goal strategy gate (mandatory classify) + §5 execution | always |
| `STORAGE_CHANNEL` | §8 | always |
| `ROBOT_FACE` | §9 | always |
| `HUMAN_VOICE` | §6 + `HumanVoicePrompt.kt` | user turns (with STATO ROBOT) |
| `SYSTEM_INPUTS` | §10 | always |
| `HEARTBEAT_PLAYBOOK` | `heartbeat_playbook_prompt.txt` | custom heartbeat / weekly_reflection ticks only |
| `WELLNESS_CHECK` | `wellness_check_prompt.txt` *(future)* | `SYSTEM_INPUT: wellness_check` only |
| `HABIT_LABEL_NORMALIZE` | `habit_label_normalize_prompt.txt` | incremental predictivity mining (one batch per run) |
| `BODY_SEARCH` | `body_capabilities_prompt.txt` | ESP32 body configured |

### Audit matrix (removed text → where it lives)

| Removed / compressed | Preserved in |
|---------------------|--------------|
| Duplicate persistent-search JSON (nascondino, dito, computer ×N) | `PERSISTENT_SEARCH` (§4) + 2 canonical §7 examples + `BODY_SEARCH` |
| Duplicate set_reminder fire_and_check example | `FIRE_AND_CHECK` (§4 gate + §5 execution) |
| `IMPORTANT for X` routing prose | `STORAGE_CHANNEL` decision table (§8) + AVAILABLE TOOLS params |
| §11 heartbeat rules + examples | `HEARTBEAT_PLAYBOOK` asset (conditional inject) |
| Multiple play_spotify / open_browser / set_robot_context / make_light examples | One JSON example each + trigger table |
| Mood reply examples in §7 | `ROBOT_FACE` (§9) + injected STATO ROBOT |

## Where this applies

| Asset | Role |
|-------|------|
| `llm_system_prompt.txt` | Global planner, storage semantics, system inputs (base) |
| `heartbeat_playbook_prompt.txt` | Custom heartbeat + weekly_reflection |
| `wellness_check_prompt.txt` | Unified Wellness scoring + optional soft speak |
| `room_order_audit_prompt.txt` | Mandatory 3-angle room order audit (objective OBS) for Wellness |
| `habit_label_normalize_prompt.txt` | Batch canonical labels for predictivity mining *(future)* |
| `body_capabilities_prompt.txt` | Body + vision (injected when ESP32 configured) |
| Tool `description` fields | Short catalog entries in AVAILABLE TOOLS |
| `memory_extractor_prompt.txt` | **Exception:** DB extraction rules (not dialog planner freedom) |

## Anti-patterns (avoid in prompts)

- "If X then always Y" playbooks
- Mandatory fast paths that replace reasoning for complex goals
- Rigid ALIGN vs SCAN decision trees
- Prescriptive tables that read like a state machine

## Related docs

- [PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md) — Predictivity + Wellness target
- [TOOL_ARCHITECTURE.md](TOOL_ARCHITECTURE.md) — tool chains and orchestrator
- [BODY_INTEGRATION.md](BODY_INTEGRATION.md) — ESP32 body + prompt injection
- [MEMORY.md](MEMORY.md) — memory vs lists vs reminders
- [AGENT_REASONING.md](AGENT_REASONING.md) — direct tool vs chain policy
