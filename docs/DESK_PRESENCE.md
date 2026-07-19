# Desk Presence (ML Kit)

Kotlin-only on-device presence detection and face offset for attention centering.

> **Wellness / unified proactive speak:** target architecture uses [`UserPresencePolicy`](PROACTIVE_ARCHITECTURE.md#userpresencepolicy) (recent user interaction **OR** recent face via `FaceGazeStateStore`) — **not** mandatory ML Kit `DeskPresenceGate`. See [PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md).

## Components

| Component | Role |
|-----------|------|
| `DeskPresenceMonitor` | CameraX ImageAnalysis + ML Kit face/pose while voice session active |
| `DeskPresenceStateStore` | Process-wide latest `DeskOccupancy` |
| `FaceGazeStateStore` | Latest face offset in frame (for attention centering, not continuous tracking) |
| `UserAttentionCentering` | Closed-loop body centering on every user voice turn while session active |
| `AttentionTriggerMatcher` | Every user voice utterance (non-blank phrase) |
| `DeskPresenceGate` | Pure rules: allows proactive only when `PRESENT` (or `UNCERTAIN` + recent interaction) |
| `DetectPresenceTool` (LLM) | Fallback for nuanced checks when ML Kit is `UNCERTAIN` |

## Scope

- **Legacy blocks**: heartbeat / proactive bot initiatives via `ProactiveGatePolicy` + `DeskPresenceGate` (until H7 migration)
- **Target Wellness speak**: `UserPresencePolicy` — interaction within W min OR face seen within W min; body connected alone does **not** count as presence
- **Room order capture (Wellness phase 2)**: does **not** require ML Kit presence; requires ESP32 body configured and reachable
- **Does not block**: user speech, STT, DEFERRED notifications/reminders

## Settings

Impostazioni → **Presenza scrivania**: enable, fps (2–10), face threshold.

**Debug overlay:** con microfono attivo e presenza abilitata, icona occhio in alto a destra → schema ROI, volti, pose e stato fusione dell'ultimo frame ML Kit (non include la voce). Volti **rossi** = scartati (troppo piccoli o statici ≥5s); `PRESENT` richiede 3 frame consecutivi stabili.

Filtri volto (on-device): dimensione minima 5% frame, esclusione box statici (poster/foto), smoothing fusione 3 frame.

Device is expected **always on charger** — default 5 fps, accurate ML Kit models.

## Camera sharing with vision tools

`take_photo`, `detect_presence`, and `analyze_room_scene` use the same `ProcessCameraProvider` as the monitor. After each capture, `VisionCameraLifecycleCoordinator` rebinds `ImageAnalysis`. If frames stall >4s, the watchdog marks `UNCERTAIN`, clears the debug overlay, and retries bind.

## Attention centering (conversational)

On **every user voice turn** during an active voice session (no cooldown between turns):

1. If face in frame → closed-loop centering (min **2** moves, max 5); longer settle (**1 s**) + gaze wait (**1.8 s**) between steps
2. **No scan / no return to 0** if the face was visible at turn start — hold last pose and speak
3. If **no face at turn start** → `base_pan` to **0**, symmetric scan **±14°, ±28°, ±42°**; if found, center and **stay there**
4. Return to **0** only when scan fails (never saw a face this turn)
5. Pan sign flip once if horizontal error worsens while face still visible
6. Final optional `head_tilt`; then LLM responds

Neutral `base_pan` is always **0** (user may rotate the physical base by hand between sessions).

Does **not** require ML Kit `PRESENT` — centering runs precisely when the user is not in front of the camera.

Requires **corpo ESP32** + **presenza scrivania** enabled.

If the first `getStatus()` fails (ESP32 offline / timeout), centering **aborts immediately** (`SkippedBodyUnreachable`) — no pan scan and no stacked move timeouts — so the LLM turn is not delayed by tens of seconds.

## FaceGazeStateStore and UserPresencePolicy

`FaceGazeStateStore` holds the latest face-in-frame offset from `DeskPresenceMonitor` (updated on analysis frames, reset when monitor stops).

**Target use:** `lastFaceSeenWithin(W minutes)` leg of `UserPresencePolicy` for Wellness TTS — alongside `lastUserInteractionWithin(W minutes)`. Not continuous tracking; snapshot at last frame with a valid face.

## vs Heartbeat (legacy)

Consumed by `ProactiveGatePolicy` and `HeartbeatOrchestrator` today. After Wellness migration, ML Kit remains for **centering** and optional face timestamp for presence OR; not the sole proactive gate.
