# Phone dial tool (`dial_phone` + `resolve_phone_contact`)

Opens the system phone app with a pre-filled number via `Intent.ACTION_DIAL`. The user taps **Call** to start — the robot does not auto-dial.

## Voice flow

| User says | Expected |
|-----------|----------|
| "Chiama il 333 1234567" | `dial_phone` directly |
| "Chiama Marco" / "Telefona a mamma" | `resolve_phone_contact` → `dial_phone` |
| "mamma" in rubrica as "Madre Rossi" | Alias match (mamma ↔ madre) |
| Multiple matches | Ask which contact |
| No match | Ask for number |

No voice confirmation step — opening the dialer is the safety gate (user taps Call).

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_CONTACTS` | Rubrica lookup |
| `READ_PHONE_STATE` | Pause STT during active call |

Requested automatically at startup if missing; denial does not block the app.

## Implementation

| File | Role |
|------|------|
| `ResolvePhoneContactTool.kt` | Contact lookup |
| `DialPhoneTool.kt` | Open dialer |
| `PhoneContactResolver.kt` | Rubrica + memories |

See also [WHATSAPP.md](WHATSAPP.md) for messaging.
