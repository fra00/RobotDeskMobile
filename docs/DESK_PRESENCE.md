# Desk Presence (ML Kit)

Kotlin-only on-device presence detection and face offset for attention centering.

> **Wellness / unified proactive speak:** target architecture uses [`UserPresencePolicy`](PROACTIVE_ARCHITECTURE.md#userpresencepolicy) (recent user interaction **OR** recent face via `FaceGazeStateStore`) — **not** mandatory ML Kit `DeskPresenceGate`. See [PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md).

## Components

| Component | Role |
|-----------|------|
| `DeskPresenceMonitor` | CameraX ImageAnalysis + ML Kit face/pose while voice session active |
| `DeskPresenceStateStore` | Process-wide latest `DeskOccupancy` |
| `FaceGazeStateStore` | Latest face offset + `lastFaceSeenAtMs` (survives brief null gaze until reset) |
| `UserAttentionCentering` | Closed-loop body centering (tool `look_at_user` + idle re-acquire) |
| `IdleVisualReacquirePolicy` | After 5 min without face (mic on, WaitingForHotword) → one silent scan; 10 min cooldown |
| `DeskPresenceGate` | Pure rules: allows proactive only when `PRESENT` (or `UNCERTAIN` + recent interaction) |
| `DetectPresenceTool` (LLM) | Fallback for nuanced checks when ML Kit is `UNCERTAIN` |
| `look_at_user` (LLM) | On-request centering (“guardami”); not for turn left/right |

## Scope

- **Presence**: predictivity / idle look-around use `DeskPresenceGate` where applicable; Wellness uses `UserPresencePolicy`
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

**Not** on every voice turn (that conflicted with “girati a destra”).

### On request
LLM tool **`look_at_user`** → `UserAttentionCentering.tryCenterOnUser()` when the user asks to look at them / center. Directional moves stay `move_body_joint` from the current pose. First face in frame only (no speaker ID).

### Idle re-acquire (silent)
While voice session is active and phase is `WaitingForHotword` (mic on, no dialog turn):

1. If no face seen for **5 minutes** (or since session start if never seen) → one silent `tryCenterOnUser()`
2. Face in frame → closed-loop center; no face at start → scan ±14/28/42°, then stay or return to `base_pan` 0
3. **10 minute** cooldown between idle attempts
4. Suppressed when robot context is SILENT (work/call/meeting/focus) or body hardware busy

Requires **corpo ESP32** + **presenza scrivania** enabled.

If the first `getStatus()` fails (ESP32 offline / timeout), centering **aborts immediately** (`SkippedBodyUnreachable`) — no pan scan and no stacked move timeouts.

Neutral `base_pan` is always **0** (user may rotate the physical base by hand between sessions).

Does **not** require ML Kit `PRESENT` — centering/scan runs when the user is not in front of the camera.

## FaceGazeStateStore and UserPresencePolicy

`FaceGazeStateStore` holds the latest face-in-frame offset from `DeskPresenceMonitor` (updated on analysis frames, reset when monitor stops).

**Target use:** `lastFaceSeenWithin(W minutes)` leg of `UserPresencePolicy` for Wellness TTS — alongside `lastUserInteractionWithin(W minutes)`. Not continuous tracking; snapshot at last frame with a valid face.

## vs Heartbeat (legacy)

Consumed by idle look-around / predictivity presence paths via `DeskPresenceGate`. After Wellness migration, ML Kit remains for **centering** and optional face timestamp for presence OR; not the sole proactive gate.
