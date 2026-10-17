# Desk Presence (ML Kit)

Kotlin-only on-device presence detection and face offset for attention centering.

> **Power:** camera is **off at idle**. ML Kit ImageAnalysis runs only during short on-demand sessions (look-around gate, idle re-acquire / sample, `look_at_user`, body locate) or when vision tools bind the camera for a photo. Do **not** assume “always on charger ⇒ continuous analysis”.

> **Wellness / unified proactive speak:** uses [`UserPresencePolicy`](PROACTIVE_ARCHITECTURE.md#userpresencepolicy) (recent user interaction **OR** body locate with a brief camera wake) — **not** continuous ML Kit `DeskPresenceGate`. See [PROACTIVE_ARCHITECTURE.md](PROACTIVE_ARCHITECTURE.md).

## Components

| Component | Role |
|-----------|------|
| `DeskPresenceMonitor` | CameraX ImageAnalysis + ML Kit face/pose **on demand** via `withCamera` |
| `DeskPresenceCameraCoordinator` | Process-wide bind so centering/tools can wake analysis without owning the monitor |
| `DeskPresenceStateStore` | Latest `DeskOccupancy` (UNKNOWN when camera off) |
| `FaceGazeStateStore` | Live gaze + `lastFaceSeenAtMs` (survives session stop; cleared on hotword off / `reset`) |
| `UserAttentionCentering` | Closed-loop body centering; wraps `withCamera` for `look_at_user` + idle re-acquire + locate |
| `IdleVisualReacquirePolicy` | After 5 min without face (or since session start) → one silent center/scan; 10 min cooldown |
| `DeskPresenceGate` | Pure rules for look-around after an occupancy **sample** |
| `DetectPresenceTool` / vision tools | One-shot capture (`take_photo`, `analyze_room_scene`, …) — separate bind |
| `look_at_user` (LLM) | On-request centering (“guardami”) |

## When the camera turns on

| Trigger | Path |
|---------|------|
| Idle look-around (soft gates OK) | Soft eligibility → `withCamera` → occupancy sample → `DeskPresenceGate` → mimica |
| Idle visual re-acquire | Policy timer → `tryCenterOnUser()` → `withCamera` |
| Tool `look_at_user` | Same centering path |
| Body locate (Wellness / Predictivity) | `locateUserNow()` → `withCamera` |
| Vision tools | `CameraXVisionImageCapture` one-shot (not ML Kit stream) |
| User / LLM needs to observe | Tool chain (`take_photo`, `analyze_room_scene`, …) — explicit or implicit in the plan |

**Idle with no gate/action → camera off.**

## Scope

- **Look-around:** samples presence only when soft gates pass; does not keep the stream open afterward
- **Re-acquire + `lastFaceSeen`:** one periodic sample/centering job (not a continuous face clock). Stopping a session clears live gaze but **keeps** `lastFaceSeenAtMs` until the next face or session `reset`
- **Wellness speak:** `UserPresencePolicy` — interaction window and/or body locate (brief camera)
- **Room order (Wellness `VISUAL_ORDER`):** photos via vision tools / body pans — not continuous presence
- **Does not block:** user speech, STT, DEFERRED notifications/reminders

## Settings

Impostazioni → **Presenza scrivania**: enable, fps (2–10) during on-demand sessions, face threshold.

**Debug overlay:** icona occhio (mic on + feature enabled). All’apertura dialog la camera ML Kit si accende per lo stream di debug; alla chiusura si spegne di nuovo. Occupancy indicator in UI only while an analysis session is active. Volti **rossi** = scartati; `PRESENT` richiede 3 frame consecutivi stabili.

Filtri volto (on-device): dimensione minima 5% frame, esclusione box statici, smoothing 3 frame.

## Camera sharing with vision tools

`take_photo`, `detect_presence`, and `analyze_room_scene` use the same `ProcessCameraProvider`. After each capture, `VisionCameraLifecycleCoordinator` rebinds `ImageAnalysis` **only if** a presence session is still active. Multi-photo chains should keep the session open for the chain when presence is also needed; otherwise each capture is independent.

## Attention centering (conversational)

**Not** on every voice turn (that conflicted with “girati a destra”).

### On request
LLM tool **`look_at_user`** → `UserAttentionCentering.tryCenterOnUser()` (wakes camera for the pass).

### Idle re-acquire (silent)
While voice session is active and phase is `WaitingForHotword`:

1. If no face seen for **5 minutes** (or since session start if never seen) → one silent `tryCenterOnUser()` (wakes camera)
2. Face in frame → closed-loop center; no face → scan ±14/28/42°, then stay or return to `base_pan` 0
3. **10 minute** cooldown between idle attempts
4. **Not** suppressed by robot WORK/SILENT (body only, no TTS); still skipped when body hardware busy

Requires **corpo ESP32** + **presenza scrivania** enabled.

If the first `getStatus()` fails (ESP32 offline / timeout), centering **aborts immediately** (`SkippedBodyUnreachable`).

Neutral `base_pan` is always **0**.

## FaceGazeStateStore and UserPresencePolicy

`lastFaceSeenAtMs` is updated only while an on-demand analysis session sees a face. It is the scheduling input for idle re-acquire, not a continuous surveillance clock.

**Target Wellness speak:** still prefers interaction window / body locate; face timestamp is optional enrichment when a recent sample exists.

## vs continuous analysis (removed)

Previously the monitor started for the whole hotword session (~5 fps). That drained battery even on charger. Runtime now defaults to **camera off**; use settings toggle to disable the feature entirely if centering/look-around gates are not needed.
