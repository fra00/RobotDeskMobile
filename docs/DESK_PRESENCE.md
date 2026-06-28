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

**Debug overlay:** con microfono attivo e presenza abilitata, icona occhio in alto a destra → schema ROI, volti, pose e stato fusione dell'ultimo frame ML Kit (non include la voce). Volti **rossi** = scartati (troppo piccoli o statici ≥5s); `PRESENT` richiede 3 frame consecutivi stabili.

Filtri volto (on-device): dimensione minima 5% frame, esclusione box statici (poster/foto), smoothing fusione 3 frame.

Device is expected **always on charger** — default 5 fps, accurate ML Kit models.

## Attention centering (conversational)

On **every user voice turn** (**20s cooldown**):

1. If face in frame → closed-loop centering (min **2** moves, max 5); longer settle (**1 s**) + gaze wait (**1.8 s**) between steps
2. **No scan / no return to 0** if the face was visible at turn start — hold last pose and speak
3. If **no face at turn start** → `base_pan` to **0**, symmetric scan **±14°, ±28°, ±42°**; if found, center and **stay there**
4. Return to **0** only when scan fails (never saw a face this turn)
5. Pan sign flip once if horizontal error worsens while face still visible
6. Final optional `head_tilt`; then LLM responds

Neutral `base_pan` is always **0** (user may rotate the physical base by hand between sessions).

Does **not** require ML Kit `PRESENT` — centering runs precisely when the user is not in front of the camera.

Requires **corpo ESP32** + **presenza scrivania** enabled.

## vs Heartbeat

Separate module; consumed by `ProactiveGatePolicy` and `HeartbeatOrchestrator`.
