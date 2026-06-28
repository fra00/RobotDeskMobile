# Heartbeat Architecture

SSOT for proactive autonomy (heartbeat).

## Flow

1. `HeartbeatScheduler` alarm → `HeartbeatOrchestrator.onAlarmTick()`
2. `ProactiveGatePolicy` (mic session, time window, desk presence ML Kit, robot context, caps)
3. `DomainScheduler` picks one enabled due domain (round-robin)
4. `HeartbeatContextBuilder` + `EnvironmentFreshnessProvider` → `RobotInput.Heartbeat`
5. `SystemInputDispatcher` → `ConversationViewModel` → `ReasoningEngine.processSystemInput`
6. Domain prompt from `assets/prompts/domains/{id}.txt` + global `heartbeat_playbook_prompt.txt`
7. `ProactiveTracker` records speak / suppress / ignored
8. **Critic pass** (domains `HIGH` + voice proposed): `processCriticPass` → approve / modify / block

## Tick MICRO (no LLM)

When no attention domain is due but idle ≥ 15 min and user is present:
- Orchestrator emits `SystemInputEvent.MicroTick`
- VM refreshes bored/drowsy eyes; optional ESP32 look-around (DISPLAY_PAN sweep)
- Logged in `SensingLogRepository` as `LOOK_AROUND`
- Zero token cost

## Critic pass

- Prompt: `assets/prompts/heartbeat_critic_prompt.txt`
- Second LLM call, no tools; runs only for `activeDomainSensitivity=HIGH`
- `block` → silence; `modify` → revised text before TTS

## Desk presence gate

See [DESK_PRESENCE.md](DESK_PRESENCE.md). ML Kit runs in parallel; heartbeat never spends tokens when `ABSENT`.

## Domains (v1)

Built-in catalog in `AttentionDomainRepository`; user toggles in Impostazioni → Proattività → Gestisci domini.

## Kotlin vs LLM

| Kotlin | LLM |
|--------|-----|
| When to tick, which domain, gates, intervention log | What to say, tools, speak_confidence |
| Environment freshness timestamps | `detect_presence`, `take_photo`, `analyze_room_scene` when needed |

## Related

- `docs/PROMPT_PHILOSOPHY.md`
- `assets/prompts/heartbeat_playbook_prompt.txt`
