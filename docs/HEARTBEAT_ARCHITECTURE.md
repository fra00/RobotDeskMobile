# Heartbeat Architecture

> **Care domains and Wellness:** The **target** proactive model for pasti / lavoro / movimento / contatti / ordine is **[PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md)** (Predictivity + unified Wellness).  
> **H7:** Care and **custom** attention domains are **toggleable scopes** for Wellness — not `TimeDaily` / event-driven heartbeat ticks. Heartbeat alarm remains for **micro-tick** only. Room identity uses spatial SSOT (`set_current_place`), not an attention-domain event.

SSOT for **heartbeat shell** (alarm + micro-tick).

## Flow (current runtime)

1. `HeartbeatScheduler` alarm → `HeartbeatOrchestrator.onAlarmTick()`
2. `ProactiveGatePolicy` (mic session, time window, desk presence ML Kit, robot context, caps)
3. Attention domains are **not** selected here (`enabledHeartbeatDomains()` is empty) — care + custom run on Wellness
4. If no domain due → optional **MICRO** tick (no LLM)

## Tick MICRO (no LLM)

When no attention domain is due (always, for care/custom) but idle ≥ 15 min and user is present:
- Orchestrator emits `SystemInputEvent.MicroTick`
- VM refreshes bored/drowsy eyes; optional ESP32 look-around (DISPLAY_PAN sweep)
- Logged in `SensingLogRepository` as `LOOK_AROUND`
- Zero token cost

**Retained** in target architecture — not replaced by Wellness.

## Desk presence gate (legacy proactive)

See [DESK_PRESENCE.md](DESK_PRESENCE.md). ML Kit runs in parallel; heartbeat blocks proactive LLM when `ABSENT`.

**Target:** Wellness speak uses [`UserPresencePolicy`](PROACTIVE_ARCHITECTURE.md#userpresencepolicy) (interaction OR recent face), not mandatory ML Kit gate.

## Domains

| Kind | Scheduling | Settings |
|------|------------|----------|
| Care (`pasti`, `attivita_fisica`, `carico_lavoro`, `contatti_sociali`, `ordine_ambiente`) | Unified Wellness tick only | Toggle on/off in Impostazioni → Proattività → Gestisci domini |
| Custom (user-defined prompt) | Same Wellness tick as care domains (no separate schedule) | Same dialog; name + description |
| Spatial / room identity | Not an attention domain | Spatial tools + `set_current_place` |

## Kotlin vs LLM

| Kotlin | LLM |
|--------|-----|
| When to tick Wellness / custom / micro, gates, intervention log | What to say, tools, speak_confidence |
| Environment freshness timestamps | `detect_presence`, `take_photo`, `analyze_room_scene` when needed |

## Related

- **[PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md)** — Wellness + Predictivity SSOT
- `docs/PROMPT_PHILOSOPHY.md`
- `assets/prompts/heartbeat_playbook_prompt.txt`
