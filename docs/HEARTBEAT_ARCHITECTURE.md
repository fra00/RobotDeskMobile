# Heartbeat Architecture

> **Retired (July 2026):** AlarmManager + `HeartbeatOrchestrator` LLM domain ticks are **removed**.  
> Care / custom attention domains run as **Wellness** toggles — see [PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md).  
> Silent idle look-around (ex MICRO tick) runs in the **ConversationViewModel mood loop** (~30 s).

## What remains from the old “heartbeat” package

| Piece | Role today |
|-------|------------|
| `HeartbeatSettings` / repository | Micro-tick **enabled**, look-around **cooldown** (`intervalMinutes`), proactive **time window**, `proactiveThreshold`, `lastInteractionMillis` |
| `IdleLookAroundEligibility` + `HeartbeatMicroTickPolicy` | Gates for silent eyes/body look-around (no LLM, **no** speak budget) |
| `HeartbeatPlaybookProviderImpl` | Still injects prompts for **weekly_reflection**, wellness, predictivity (name historical) |
| `ProactiveGatePolicy` constants | Cap/cooldown shared with `ProactiveSpeakGate` (predictivity speak) |
| `AttentionDomainRepository` | Wellness domain toggles only |
| `ProactiveTracker` | Intervention logging for wellness/predictivity |

## Idle look-around (ex MICRO tick)

While hotword session is active, every ~30 s mood poll may call `pollIdleLookAround()`:

1. `HeartbeatSettings.enabled` (UI: Micro-tick)
2. Active time window, not night, not robot-context silent
3. Desk presence allows interaction (ML Kit / policy)
4. `HeartbeatMicroTickPolicy` (idle ≥15 min + bored/drowsy, or idle ≥20)
5. Cooldown since last `SensingKind.LOOK_AROUND` ≥ `intervalMinutes`
6. Ephemeral bored/drowsy eyes + optional ESP32 `display_pan` sweep via `BodyExpressionMapper.resolveMicroTick`

Zero LLM. Does **not** use `ProactiveSpeakGate` (speak cap must not block silent fidget).

## Related

- **[PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md)** — Wellness + Predictivity SSOT
- **[BODY_INTEGRATION.md](BODY_INTEGRATION.md)** — body choreography
- `assets/prompts/heartbeat_playbook_prompt.txt` — still used for **weekly_reflection** injection
