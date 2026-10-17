# Notification Proxy — Device roles (spec)

> Same APK, two exclusive roles: **ROBOT** (desk device) and **PROXY** (personal phone that forwards notifications).  
> Status: **implemented (v1)**.  
> Related: [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md) (notification pipeline SSOT).  
> **Capability pull / dual reminder** (exercises, contacts, reminder fan-out): [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md).

---

## 1. Problem

The desk robot needs an Android device (camera, STT/TTS, eyes). That device is often the user’s daily phone. When the user leaves home or moves rooms with the phone, the robot loses the personal notification stream (WhatsApp, Teams, SMS, …).

**Goal:** keep a dedicated (possibly older) Android on the robot, and use the personal phone only as a **notification proxy** on the home LAN.

**Non-goals (v1):**

- Sync when away from home / no shared Wi‑Fi
- Cloud relay or VPN
- Remote control of phone hardware from the robot (dial, WhatsApp send, Spotify on the phone, …)
- Autostart proxy after phone reboot (user must open the app)
- mDNS auto-discovery (IP comes from pairing QR)
- **Outbound queue on the proxy** (no backlog / drain / mic-aware client)

---

## 2. Design principle: same pipeline, new entry point

The proxy changes **only where the notification enters** the robot.

```text
Today:   NotificationListener (on robot) → SystemInputDispatcher → InputPolicyEngine → …
v1:      NotificationListener (on proxy) → HTTP POST → same Dispatcher / Policy / LLM path
```

**Do not** invent parallel mic / night / SILENT / dedup logic on the proxy.  
**Do not** change post-dispatch behavior on the robot for remote vs local (once the envelope exists).

| Concern | Where it lives |
|---------|----------------|
| App whitelist / enable source | **PROXY** (before POST) |
| HTTP reachability | PROXY tries POST; on failure → **drop** (no queue) |
| Mic off / hotword inactive | **ROBOT** — drop (as today) |
| Night mode | **ROBOT** — drop/suppress DEFERRED (as today) |
| Busy → deferred queue | **ROBOT** — `DeferredInputQueue` (as today) |
| SILENT robot context | **ROBOT** — process, no TTS (as today) |
| Dedup | **ROBOT** — same keys as today |

The proxy **does not know** and **does not ask** whether the robot mic is on.

---

## 3. Roles (same application)

| Role | Device | Responsibility |
|------|--------|----------------|
| **ROBOT** | Desk / secondary Android | Full product: voice, eyes, LLM, tools, body, memory, … |
| **PROXY** | Personal phone | Listen to notifications and forward them (best-effort POST); also serve LAN **capability** endpoints for the robot (exercises, contacts, local notification) — see [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md). Minimal runtime (no LLM). |

Roles are mutually exclusive: proxy **or** robot, not both. Changeable via **Mode Bot** settings (see §9).

### 3.1 Persistent mode + one-shot restart flag

| Store key | Meaning |
|-----------|---------|
| `deviceMode` | Persistent: `ROBOT` \| `PROXY` — survives process death and (when user opens the app) cold start |
| `applyModeRestart` | **One-shot** boolean: set when the user switches mode so the next process start bootstraps cleanly |

**Switch flow:**

1. User selects Robot or Proxy in Mode Bot dialog → save `deviceMode`.
2. Set `applyModeRestart = true` → restart process (`System.exit` / alarm / activity recreate pattern used by the project).
3. On startup: read `deviceMode`; if PROXY, start **proxy bootstrap** (no LLM/eyes/…); if ROBOT, normal app.
4. Clear `applyModeRestart` after bootstrap completes.

So: the **mode persists**; the **restart flag does not**. Next intentional launch of the app still respects `deviceMode` (e.g. still PROXY until the user switches back). After **phone reboot**, the app does not auto-start the FGS (non-goal); when the user opens the app, if `deviceMode == PROXY`, enter proxy mode again.

### 3.2 Proxy runtime (minimal)

When `deviceMode == PROXY`:

- No conversation UI / eyes as primary UX (proxy status UI)
- No STT / TTS / LLM / mood / presence / heartbeat / wellness / predictivity
- Yes: `NotificationListenerService`, whitelist, HTTP client (no delivery queue), pairing/scanner, FGS, battery helper

### 3.3 Robot behavior vs pairing

| State | Local `NotificationListener` | Remote notifications |
|-------|------------------------------|----------------------|
| ROBOT, **no** proxy paired (or after unpair all) | **On** — current behavior | N/A |
| ROBOT, **≥1** proxy paired | **Off** — ignore tablet notifications | **On** — forwarded only |

---

## 4. Architecture (v1)

```text
[Personal phone — PROXY]                    [Desk device — ROBOT]
 NotificationListenerService
   → whitelist filters
   → POST /v1/notify (best effort) ───────►  HTTP server (Wi‑Fi only)
   → failure → DROP                             → NotificationData → envelope
                                                → SystemInputDispatcher
                                                → InputPolicyEngine (unchanged)
```

---

## 5. Pairing (QR)

1. **ROBOT** shows QR (see §6.2).
2. **PROXY** scans QR and stores host/port/token.
3. PROXY POSTs only to that endpoint with the token.

**IP changes:** re-scan QR.

### 5.1 Multi-proxy (clarified)

**Meaning:** more than one personal phone can be paired to the **same** robot (e.g. phone + work phone).

**v1:** yes — robot keeps a **list** of paired proxy tokens/device ids with **revoke** per entry.  
A single proxy device pairs to **one** robot (enough).

---

## 6. Wire format (normative for v1)

### 6.1 Defaults

| Item | Value |
|------|--------|
| Listen port | `8765` |
| Scheme | `http` (LAN only; TLS later) |
| Notify path | `POST /v1/notify` |
| Content-Type | `application/json; charset=utf-8` |
| Auth | Header `Authorization: Bearer <token>` **or** body field `token` (header preferred) |

### 6.2 QR payload

UTF-8 JSON (compact), encoded as QR content:

```json
{
  "v": 1,
  "name": "MyDeskRobot",
  "ip": "192.168.1.42",
  "port": 8765,
  "token": "<opaque-secret>",
  "robotId": "<stable-robot-id>"
}
```

Optional alternate: same fields as query in  
`mydeskrobot://pair?v=1&ip=…&port=8765&token=…&robotId=…&name=…`  
Implementer picks QR-as-JSON **or** deep-link form; both must carry the same fields. **Prefer JSON** for simpler camera scanning libraries.

### 6.2b `POST /v1/pair` body (proxy → robot)

```json
{
  "protocolVersion": 1,
  "proxyDeviceId": "<stable-id-on-proxy>",
  "proxyLabel": "Pixel…",
  "lanIp": "192.168.1.50",
  "capabilityPort": 8765
}
```

`lanIp` / `capabilityPort` let the robot call the phone for capabilities ([`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md)).

### 6.3 `POST /v1/notify` body

```json
{
  "protocolVersion": 1,
  "packageName": "com.whatsapp",
  "notificationKey": "<android-key>",
  "title": "…",
  "text": "…",
  "postTime": 1730000000000,
  "proxyDeviceId": "<stable-id-on-proxy>"
}
```

| HTTP | Meaning |
|------|---------|
| `200` | Accepted into robot input bus (policy may still drop after — same as local) |
| `400` | Malformed body |
| `401` | Bad/missing token |
| `403` | Token revoked / mode not ROBOT / not on Wi‑Fi |
| `503` | Server not listening (should be rare) |

Proxy: any non-2xx / timeout / no route → **drop** (log only).

### 6.4 Dedup & mapping

- Robot maps JSON → `NotificationData` → existing `normalize` / `toEnvelope`.
- Dedup **only on robot**, same key: `notif:{packageName}:{notificationKey}`.
- `appLabel`: resolve on robot if package present; else package name.

---

## 7. Policy matrix

| Situation | PROXY | ROBOT |
|-----------|-------|-------|
| App not in whitelist | Do not POST | — |
| POST fails | **Drop** | — |
| Mic / hotword inactive | May POST | **Drop** (as today) |
| Night mode | May POST | **Drop/suppress** (as today) |
| Busy | May POST | **DeferredInputQueue** (as today) |
| SILENT context | May POST | Process, no TTS (as today) |

---

## 8. Android operational constraints (PROXY)

### 8.1 Foreground service

Persistent notification: “My Desk Robot — notification proxy active”.

### 8.2 No autostart on reboot

After phone reboot, open the app manually; if `deviceMode == PROXY`, proxy bootstrap runs.

### 8.3 Battery helper UI

Deep-link + short OEM hints. Best-effort.

### 8.4 Notification access

Required on the proxy device (same as today’s listener).

### 8.5 Wi‑Fi only (required)

- Robot HTTP server: bind / accept **only on Wi‑Fi** (not mobile data, not soft-ap unless Wi‑Fi). If not on Wi‑Fi → do not listen (or reject with `403`).
- Proxy client: POST only when on Wi‑Fi; otherwise drop.

---

## 9. Settings UX — **Mode Bot**

Settings entry: **Mode Bot** → dialog:

| Control | Behavior |
|---------|----------|
| Mode **Robot** | Full app; show pairing panel: **show QR**, list paired proxies, **revoke**; opt-in **dual reminder** checkbox (fan-out to phone) |
| Mode **Proxy** | Proxy-only runtime; show: **scan QR**, unpair, notification whitelist (reuse existing notification settings), battery helper, last forward status |

Switching mode uses §3.1 restart flag.

---

## 10. Security

- Bearer token on every POST; revoke removes that proxy
- No LLM / user memory on proxy role
- **Wi‑Fi only** listen/send (§8.5)

---

## 11. Acceptance criteria (v1)

1. QR pair on same Wi‑Fi works.
2. Whitelisted notify on proxy → robot handles like local.
3. Non-whitelist → not forwarded.
4. Paired robot ignores local tablet notifications.
5. Unpair all → local listener back.
6. Mic off / night on robot → drop on robot (proxy may have POSTed).
7. Robot unreachable / not on Wi‑Fi → proxy drops.
8. Mode Bot switch Robot↔Proxy restarts once via one-shot flag; mode persists.
9. FGS visible in proxy mode; brain not started.
10. After phone reboot, no silent FGS until user opens app; then PROXY mode if still selected.
11. Second proxy phone can pair; revoke one does not drop the other.

---

## 12. Implementation sketch

| Piece | Location (indicative) |
|-------|------------------------|
| `deviceMode` / `applyModeRestart` | DataStore |
| HTTP server + Wi‑Fi gate | `integration/input/notification/proxy/` |
| Proxy client | same |
| Mode Bot dialog | `ui/components/` (pattern like other settings dialogs) |
| Gate local listener | `RobotNotificationListenerService` |

---

## 13. Documentation map

| Audience | Doc |
|----------|-----|
| Agents / implementers | **This file** |
| Capability bridge | [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) |
| Humans (IT) | [`guides/PROXY_NOTIFICHE.md`](guides/PROXY_NOTIFICHE.md) |
| Notify bus | [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md) |

When shipped: update `INPUT_ARCHITECTURE.md` §6 and mark this doc implemented.
