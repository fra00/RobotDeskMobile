# Desk Presence (ML Kit gate)

Kotlin-only on-device presence detection before proactive LLM ticks.

## Components

| Component | Role |
|-----------|------|
| `DeskPresenceMonitor` | CameraX ImageAnalysis + ML Kit face/pose while voice session active |
| `DeskPresenceStateStore` | Process-wide latest `DeskOccupancy` |
| `FaceGazeStateStore` | Latest face offset in frame (for attention centering, not continuous tracking) |
| `UserAttentionCentering` | Closed-loop body centering on every user voice turn (scan if no face, pan flip if error worsens) |
| `AttentionTriggerMatcher` | Every user voice utterance (non-blank phrase) |
| `DeskPresenceGate` | Pure rules: allows proactive only when `PRESENT` (or `UNCERTAIN` + recent interaction) |
| `DetectPresenceTool` (LLM) | Fallback for nuanced checks when ML Kit is `UNCERTAIN` |

## Scope

- **Blocks**: heartbeat / proactive bot initiatives
- **Does not block**: user speech, STT, DEFERRED notifications/reminders

## Settings

Impostazioni → **Presenza scrivania**: enable, fps (2–10), face threshold.

Device is expected **always on charger** — default 5 fps, accurate ML Kit models.

## Attention centering (conversational)

On **every user voice turn** (**20s cooldown**):

1. If face in frame → read `FaceGazeSnapshot` from ML Kit (max 2.5s old)
2. If **no face** (user off-camera) → sweep `base_pan` up to 4 steps (14° each) to find them
3. Up to **5 horizontal centering steps** (`display_pan` / `base_pan`), speed **16** — pan sign inverted vs image offset
4. After each step: wait ~550ms, re-read face; if offset **worsens** → flip pan sign once and correct
5. Final optional `head_tilt` vertical adjust (speed **14**)
6. Then LLM responds

Does **not** require ML Kit `PRESENT` — centering runs precisely when the user is not in front of the camera.

Requires **corpo ESP32** + **presenza scrivania** enabled.

## vs Heartbeat

Separate module; consumed by `ProactiveGatePolicy` and `HeartbeatOrchestrator`.
