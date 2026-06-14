# Prompt philosophy — capability catalog, not playbook

LLM prompts in this project describe **what the robot can do** and **hard limits**, not step-by-step scripts the model must follow.

## Three layers in every prompt

| Layer | Purpose | Example |
|-------|---------|---------|
| **Capabilities** | Tool catalog + hardware semantics | Joint names, `save_memory` vs `add_list_item` roles |
| **Constraints** | Non-negotiable technical or product rules | One image per LLM turn; `reply: ""` for silent body moves; `think` = orientation (goal/know/unknown/success/now), no multi-step plans in `think`; never TTS; no OTP in voice |
| **Illustrative examples** | Show valid JSON shape and plausible chains | Few-shot JSON uses `<MARKER>` placeholders in `reply`, not fixed Italian phrases |

The planner (the LLM) combines tools freely to reach the user's goal or heartbeat objective. See [TOOL_ARCHITECTURE.md](TOOL_ARCHITECTURE.md) §11 (autonomous tool chains).

## Cognitive agent layer (persona)

[`docs/nextPromptv1.md`](nextPromptv1.md) defines **who the robot is** (companion, not assistant) and autonomous policies (heartbeat relevance, Fire-and-Check, boundaries). That content lives in `llm_system_prompt.txt` as narrative constraints and optional patterns — **not** as rigid playbooks. Persona rules use the same JSON contract (`reply`, `emotion`, `speak_confidence`, `action.chain_status`). See the mapping table at the top of `nextPromptv1.md`.

## Rules for authors

1. **Examples are not scripts.** Numbered sequences, few-shot JSON, and pattern names (centering, room exploration) are suggestions. The model may invent different valid chains.
2. **Constraints stay imperative.** Technical limits (vision: one photo per turn → write text notes in history) and product policy (speak_confidence on heartbeat) are real boundaries.
3. **Capabilities stay neutral.** Tool descriptions say what a tool does and what it combines with — not "always call X before Y".
4. **Optional patterns are optional.** Strategies like subject centering, multi-angle exploration, or mood-informed body expression are listed under *you may* / *if useful*, with soft limits (e.g. ~3 correction cycles), not mandatory decision trees.
5. **Storage = semantics, not flowchart.** Memory, lists, and reminders are defined by what information *is*, not by the verb "ricorda" alone.
6. **Autonomy parsimony is explicit.** OBSERVATION/INTENT are robot-internal (`MEMORY.md`); save only when decision-changing; max 3 INTENTs (app-enforced); when in doubt on heartbeat: silence, do not save.

## Where this applies

| Asset | Role |
|-------|------|
| `llm_system_prompt.txt` | Global planner, storage semantics, heartbeat |
| `body_capabilities_prompt.txt` | Body + vision (injected when ESP32 configured) |
| Tool `description` fields | Short catalog entries in AVAILABLE TOOLS |
| `memory_extractor_prompt.txt` | **Exception:** DB extraction rules (not dialog planner freedom) |

## Anti-patterns (avoid in prompts)

- "If X then always Y" playbooks
- Mandatory fast paths that replace reasoning for complex goals
- Rigid ALIGN vs SCAN decision trees
- Prescriptive tables that read like a state machine

## Related docs

- [TOOL_ARCHITECTURE.md](TOOL_ARCHITECTURE.md) — tool chains and orchestrator
- [BODY_INTEGRATION.md](BODY_INTEGRATION.md) — ESP32 body + prompt injection
- [MEMORY.md](MEMORY.md) — memory vs lists vs reminders
- [AGENT_REASONING.md](AGENT_REASONING.md) — direct tool vs chain policy
