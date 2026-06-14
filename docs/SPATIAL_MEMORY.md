# Spatial memory (room localization)

Desk robot knows **which room** the phone/body sits in — not mobile navigation. Identity comes from **visual landmarks** (bed → bedroom, desk+PC → study) stored in Room DB, with runtime SSOT in DataStore.

## Architecture

| Layer | Components |
|-------|------------|
| Domain | `SpatialPlace`, `RoomLandmarks`, `PlaceMatcher`, `SpatialIntentDetector`, `SpatialPromptFormatter` |
| Data | `spatial_places` (Room), `SpatialPlaceRepository`, `SpatialContextRepository` (DataStore) |
| SSOT | `SpatialContextManager` — only `set_current_place` / invalidate writes current room |
| Integration | `RoomSceneAnalyzer`, spatial tools, `SpatialPromptProviderImpl` |

## Tools

| Tool | Role |
|------|------|
| `analyze_room_scene` | One photo → landmark JSON (vision prompt) |
| `match_place` | Landmarks → best match + confidence + candidates |
| `save_place` | Create/update memorized room |
| `list_places` | Active places for disambiguation |
| `set_current_place` | Confirm SSOT (`place_id` or unknown) |

Multi-angle scan: `move_body_joint` + `analyze_room_scene` per angolo (merge landmark in think), poi `match_place` o `save_place`.

## Scan policy (prompt)

| Goal | Body available | Body absent |
|------|----------------|-------------|
| **Memorize** new place | Multi-angle required before `save_place` | Single photo + admit limits |
| **Match** known places | One photo if match ≥0.55; else multi-angle + re-match | One photo; ask if uncertain |

## Matching thresholds

- High ≥ 0.55 — declare autonomously (match mode: one photo OK)
- Medium 0.35–0.55 — ask confirmation or multi-angle re-scan
- Low &lt; 0.35 — propose new place or more scans

## Prompt injection

`DOVE SONO (autoritativo)` block from `SpatialPromptProviderImpl` — includes match vs memorize rules.

## User triggers

- **Invalidate** (before LLM turn): «sei in un'altra stanza», «nuova stanza», … → `SpatialContextManager.invalidateCurrentPlace()`
- **SPATIAL** memory profile: stanza, dove siamo, memorizza questa stanza, guarda intorno, …

## Heartbeat

`HeartbeatContextBuilder` adds `currentPlaceLabel`, `placeConfidence`, `knownPlaces` to heartbeat payload.

## Settings UI

Impostazioni → **Stanze / luoghi** — edit label/landmarks, delete places (`SpatialSettingsDialog`).

## Manual QA checklist

1. «Memorizza questa stanza» in studio → scan → `save_place` → visible in Settings
2. Move setup / «sei in un'altra stanza» → scan → correct match or new place proposal
3. «Dove siamo?» without prior name → autonomous answer or question if medium confidence
4. «Sono in camera» but landmarks from studio → confirmation or update
5. Heartbeat with known room + absent user → contextual OBSERVATION in log
6. Without ESP32 body → single-photo flow still works (degraded)

## Out of scope (phase 2)

- Night reminder policy per room
- Visual embeddings / image fingerprint
- Phone movement via sensors
- Structured `analyze_environment` (light, clutter)
