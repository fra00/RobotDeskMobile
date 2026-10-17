# Phone Capability Bridge (LAN)

> Extensible **Robot ↔ Proxy** LAN bridge for phone-side capabilities.  
> Status: **v1**. Related: [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md) (notify push PROXY→ROBOT), [`SCHEDULED_TASKS.md`](SCHEDULED_TASKS.md) (reminder dual delivery).

---

## 1. Problem

The desk **ROBOT** device often lacks the personal phone’s Health Connect data and contacts rubrica. The **PROXY** phone already pairs for notification forward.

**Goal:** let the robot **pull** selected phone capabilities over the same LAN pair, and optionally **push** reminder notifications to the phone at fire time — without mirroring the full LLM tool catalog onto the proxy.

**Non-goals (v1):**

- Cloud / VPN / off-LAN
- Offline queue for capability or reminder push
- Second reminder DB / AlarmManager on the proxy
- TTS of reminders on the phone
- Dialer / WhatsApp / Spotify control **on the phone** from the robot
- Generic HTTP RPC for every robot tool
- LLM / reasoning on the PROXY

---

## 2. Directions

| Direction | Pattern | Examples |
|-----------|---------|----------|
| PROXY → ROBOT | Notify push (existing) | `POST /v1/notify` |
| ROBOT → PROXY | Capability **pull** | `GET /v1/capabilities/{id}` |
| ROBOT → PROXY | Reminder **push** | `POST /v1/capabilities/local_notification` |

Same Bearer token and Wi‑Fi-only rules as notification proxy.

---

## 3. Pairing (bidirectional addressing)

Existing QR still encodes **robot** `ip` / `port` / `token` for PROXY→ROBOT notify.

At `POST /v1/pair`, the proxy body also sends:

```json
{
  "protocolVersion": 1,
  "proxyDeviceId": "<id>",
  "proxyLabel": "Pixel…",
  "lanIp": "192.168.1.50",
  "capabilityPort": 8765
}
```

The robot stores `lanIp` + `capabilityPort` on `PairedProxyDevice` so the ROBOT can call the PROXY.

Capability HTTP listens on the **PROXY** on `PairingQrCodec.DEFAULT_PORT` (8765). Roles are exclusive → no port conflict with the robot notify server.

---

## 4. API

Base URL (ROBOT → PROXY): `http://{lanIp}:{capabilityPort}`

Auth: `Authorization: Bearer <pair-token>` (must match the token stored on the proxy’s `PairedRobotEndpoint`).

| Capability id | Method | Purpose |
|---------------|--------|---------|
| `ping` | GET | Health check |
| `exercises` | GET | Health Connect sessions (`fromMs`, `toMs` query) |
| `contacts` | GET | Rubrica match (`q` query) |
| `local_notification` | POST | Show system notification on the phone |

Unknown id → `404`. Bad/missing token → `401`. Not on Wi‑Fi → `403`.

### 4.1 Exercises response

```json
{
  "ok": true,
  "sessions": [
    {
      "metadataId": "…",
      "exerciseType": 79,
      "startTimeMs": 0,
      "endTimeMs": 0,
      "title": "optional"
    }
  ]
}
```

### 4.2 Contacts response

```json
{
  "ok": true,
  "matches": [
    { "displayName": "Anna", "number": "+39…", "score": 0.9 }
  ]
}
```

### 4.3 Local notification body

```json
{
  "title": "Promemoria",
  "text": "…",
  "notificationId": 6001
}
```

---

## 5. Capabilities ↔ robot wiring

| Capability | Robot consumer | LLM tools |
|------------|----------------|-----------|
| `exercises` | `RemotePreferringExerciseReader` → `HealthConnectExerciseSync` | none (background) |
| `contacts` | `PhoneContactResolver` (remote-first) | `resolve_phone_contact`, `resolve_whatsapp_target` |
| `local_notification` | `ReminderAlarmReceiver` when Mode Bot dual-reminder ON | `set_reminder` scheduling stays robot-only |

Fallback: if proxy unreachable / unpaired → local reader / local contacts (current behavior).

---

## 6. Dual reminder (opt-in)

- Scheduling SSOT: robot `scheduled_tasks` + AlarmManager only.
- Mode Bot (ROBOT): checkbox **«Invia anche i reminder al telefono collegato»** → `dualReminderEnabled` (default **false**). Enabled only when ≥1 proxy paired.
- On fire: robot voice + notification + input bus unchanged; if enabled and proxy reachable → best-effort `local_notification` on phone.
- No cancel path on phone (notification created only at fire). Offline → robot only.

---

## 7. Privacy

- Contacts: query string + match results transit LAN only; no cloud.
- Exercises: session metadata only (type, times, title).
- Proxy role still has no LLM / user memory store.

---

## 8. Package layout

```text
integration/proxy/capability/
  PhoneCapabilityId
  PhoneCapabilityHandler
  PhoneCapabilityRegistry
  PhoneCapabilityHttpServer   # PROXY
  PhoneCapabilityClient       # ROBOT
  handlers/ (Ping, Exercises, Contacts, LocalNotification)
```

**Add a capability:** 1 id + 1 handler + 1 doc row + unit test.

---

## 9. Smoke

1. Pair QR → robot stores proxy `lanIp` → `ping` OK.
2. HC exercise on phone → appears in robot Log Day after sync.
3. «Chiama Anna» with contacts only on phone → resolve works.
4. Dual reminder ON → short `set_reminder` → notif on robot **and** phone; OFF → robot only.

---

## 10. Doc map

| Audience | Doc |
|----------|-----|
| Agents | **This file** |
| Notify pair | [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md) |
| Reminders | [`SCHEDULED_TASKS.md`](SCHEDULED_TASKS.md) |
| Humans (IT) | [`guides/PROXY_NOTIFICHE.md`](guides/PROXY_NOTIFICHE.md) |
