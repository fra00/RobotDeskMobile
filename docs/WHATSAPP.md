# WhatsApp messaging (`send_whatsapp` + `resolve_whatsapp_target`)

Opens WhatsApp with a **pre-filled message** via `https://api.whatsapp.com/send`. The user taps **Send** — the robot does not auto-send (same model as `dial_phone`).

## Voice examples

| User says | Expected |
|-----------|----------|
| "Invia messaggio su siamo i migliori con scritto questa sera ci sono anche io" | `resolve_whatsapp_target` (group) → `send_whatsapp` |
| "Scrivi a Marco su WhatsApp che arrivo tardi" | `resolve_whatsapp_target` (contact) → `send_whatsapp` |

No voice confirmation — opening WhatsApp with the text ready is enough; user taps Send.

## How groups are found

1. **WhatsApp synced contacts** — groups saved in the phone rubrica (`vnd.android.cursor.item/vnd.com.whatsapp.group`)
2. **Memories** — e.g. `Gruppo WhatsApp siamo i migliori: 120363016464847264@g.us`
3. **1:1 contacts** — WhatsApp profile in contacts, or phone rubrica number via `resolve_phone_contact` fallback

If a group is not in contacts and not in memory, the robot asks for clarification or suggests saving the group JID in memory.

## Permissions

`READ_CONTACTS` — same as phone calls; requested at app startup if missing.

## Limitations

- Cannot send silently without user tapping Send (WhatsApp / OS policy)
- Group must be identifiable by name in contacts or memory JID — invite links alone do not pre-fill a message in a specific group chat
- Requires WhatsApp installed (`com.whatsapp`)

## Implementation

| File | Role |
|------|------|
| `ResolveWhatsAppTargetTool.kt` | Lookup contact/group |
| `SendWhatsAppTool.kt` | Open WhatsApp with `?text=` |
| `WhatsAppTargetResolver.kt` | Contacts + memories + phone fallback |
| `WhatsAppUriBuilder.kt` | Build send URI |
