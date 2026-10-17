# My Desk Robot

**A voice companion that lives on your desk — not another generic chatbot.**

My Desk Robot is an Android app with animated eyes, a local-first memory, and an LLM that can *do* things: reminders, photos, web search, WhatsApp drafts, an optional ESP32 body, and calm proactive nudges when it makes sense. Silence is a valid answer. Presence matters more than productivity theatre.

```text
You speak  →  STT  →  LLM (+ tools / memory)  →  TTS + eyes (+ body)
                ↑
     notifications · habits · wellness · phone proxy
```

---

## Why it exists

Most assistants wait for a command and forget the room. This one is meant to sit beside you:

- **Eyes that feel alive** — emotions from the model, persistent mood, idle look-around when bored  
- **Memory that stays on device** — unified RAG, Log Day, preferences you can see and delete  
- **Tools with judgment** — chains of local and remote actions, not a wall of buttons  
- **Proactivity with brakes** — habits and wellness, gated so it does not nag all day  
- **Optional body** — ESP32 servos for nods, turns, and “look at me”  
- **Phone as sensor** — same APK as notification proxy + Health Connect / contacts bridge  

Italian in the UI; English in code and most technical docs.

---

## Highlights

| Area | What you get |
|------|----------------|
| **Voice** | Unified STT (Android / Vosk), TTS, optional continuous translation |
| **Mind** | LM Studio (local) or Gemini; JSON tool chains |
| **Memory** | On-device RAG, recall planner, lists, spatial places |
| **Day** | 7-day activity log, Health Connect exercises (device or paired phone) |
| **Care** | Predictivity + wellness (v1), reminders, commitment follow-up |
| **World** | Weather, web search, browser, Spotify search, phone / WhatsApp intents |
| **Home LAN** | Notification proxy (ROBOT ↔ PROXY), capability pull from the phone |
| **Body** | myDeskBody HTTP API — joints, home, look-at-user |

Full inventory: [`docs/FEATURES.md`](docs/FEATURES.md). Open roadmap: [`docs/TODO.md`](docs/TODO.md).

---

## Quick start

### Requirements

- Android Studio (recent stable)  
- Device or emulator with **minSdk 24**  
- An LLM: **LM Studio** on your LAN, or a **Gemini** API key  

### Build & run

```bash
git clone <your-repo-url>
cd RobotDeskMobile
cp local.properties.example local.properties
# Edit local.properties (SDK path, optional seed LLM values — never commit secrets)
```

Open the project in Android Studio, sync Gradle, run the `:app` configuration on a device.

### Configure the LLM (in-app)

1. Open **Settings** (gear, bottom-left).  
2. Choose **LM Studio** or **Gemini**.  
3. For LM Studio on a physical device, use your PC’s LAN URL (e.g. `http://192.168.x.x:1234/v1/`). Emulator often uses `http://10.0.2.2:1234/v1/`.  
4. Use **Test connection**, then enable the microphone.

Details: [`docs/LLM_SETTINGS.md`](docs/LLM_SETTINGS.md).

### First conversation

1. Grant mic (and camera if you use vision tools).  
2. Enable hotword / listening from the UI.  
3. Say something simple — eyes should react, TTS should answer.  
4. Try a tool: *“Che tempo fa?”*, *“Ricordami tra cinque minuti…”*, *“Scatta una foto”*.

---

## Architecture (short)

Three layers, one APK:

| Layer | Package | Role |
|-------|---------|------|
| **1 — Robot UI** | `presentation/`, `ui/`, speech wrappers | Compose eyes, ViewModel, STT/TTS |
| **2 — Reasoning** | `reasoning/` | Kotlin-first engine, tool orchestration, LLM client API |
| **3 — Integrations** | `integration/`, `data/` | LM Studio / Gemini, tools, presence, proxy, body |

Agent-oriented map: [`AGENTS.md`](AGENTS.md). Tool design: [`docs/TOOL_ARCHITECTURE.md`](docs/TOOL_ARCHITECTURE.md). Structure / debt snapshot: [`docs/ARCHITECTURE_HEALTH.md`](docs/ARCHITECTURE_HEALTH.md).

---

## Optional setup

| Feature | Where to look |
|---------|----------------|
| **ESP32 body** | Settings → Body · [`docs/BODY_INTEGRATION.md`](docs/BODY_INTEGRATION.md) |
| **Notification proxy** | Mode Bot · desk = ROBOT, phone = PROXY · [`docs/guides/PROXY_NOTIFICHE.md`](docs/guides/PROXY_NOTIFICHE.md) |
| **Phone capabilities** | Exercises / contacts / dual reminders · [`docs/PHONE_CAPABILITY_BRIDGE.md`](docs/PHONE_CAPABILITY_BRIDGE.md) |
| **Proactivity** | Settings · [`docs/guides/PROATTIVITA.md`](docs/guides/PROATTIVITA.md) |
| **Mood / eyes** | [`docs/guides/UMORE.md`](docs/guides/UMORE.md) |
| **Memory** | [`docs/guides/MEMORIA.md`](docs/guides/MEMORIA.md) |

Human guides (Italian): [`docs/guides/README.md`](docs/guides/README.md). Technical specs (mostly English): [`docs/`](docs/).

---

## Privacy & secrets

- LLM API keys live in **EncryptedSharedPreferences** (and optional seed in `local.properties` — **never commit real keys**).  
- Memory and Log Day stay **on device**.  
- Proxy / capability traffic is **LAN-only** (Wi‑Fi), Bearer token from QR pairing.  
- See `.cursor/rules` / project security notes if you contribute.

---

## Contributing

- Prefer **small, explicit scope** — one capability per change.  
- Follow existing packages and Kotlin style; UI copy in Italian, code/comments in English.  
- Update the relevant `docs/` (and guides if behaviour is user-visible).  
- For AI-assisted work, start from [`AGENTS.md`](AGENTS.md) and the Cursor rules under `.cursor/rules/`.

---

## License

Specify your license here when you publish the repository (e.g. MIT / Apache-2.0 / proprietary).

---

<p align="center">
  <em>Two eyes. A desk. A little more continuity than a chat box.</em>
</p>
