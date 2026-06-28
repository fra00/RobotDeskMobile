# Integrazione corpo fisico (myDeskBody)

App Android ↔ ESP32-C3 **myDeskBody** sulla LAN via HTTP REST.

## Setup

1. Flash firmware myDeskBody e collega ESP32 alla stessa rete Wi‑Fi del telefono.
2. In **Impostazioni → Corpo robot**: abilita il corpo, inserisci URL base (es. `http://192.168.1.42`).
3. **Prova connessione** (`GET /status`) — mostra IP, RSSI, stato movimento.
4. **Test movimento** (`POST /test`) — sequenza di test sul firmware (non esposto al LLM).
5. Salva: tool body e sezione prompt **TOOL PLANNER — BODY** compaiono al turno LLM successivo (reload engine automatico).

Se URL vuoto o corpo disabilitato: **nessun tool body** nel catalogo e **nessuna** sezione corpo nel prompt.

## Filosofia prompt

Il LLM è un **planner libero**: sceglie ordine e combinazione di tool body + `take_photo` per raggiungere l'obiettivo. Il prompt descrive capacità hardware, vincoli tecnici e pattern opzionali — non script obbligatori.

Vedi [PROMPT_PHILOSOPHY.md](PROMPT_PHILOSOPHY.md).

## Prompt dinamico

| Componente | File |
|------------|------|
| Prompt base (senza corpo) | `app/src/main/assets/prompts/llm_system_prompt.txt` |
| Prompt corpo (solo se attivo) | `app/src/main/assets/prompts/body_capabilities_prompt.txt` |
| Provider | `BodyPromptProviderImpl` → `BodyCapabilitiesProvider` |
| Iniezione | `ReasoningEngineImpl.buildPromptWithContext()` dopo AVAILABLE TOOLS |

### Capability summary (prompt corpo)

- **Planner**: `move_body_joint`, `move_body_joints`, `body_home`, `body_status`, `take_photo` — combinabili liberamente.
- **Vincoli**: gesti fisici → `reply: ""`; occhi Compose ≠ corpo; ±45°; una foto per turno LLM (note testuali in history se catena multi-foto).
- **Joint**: `base_pan` (intero robot), `display_pan` (solo testa/display), `head_tilt` (sì/no su-giù), `head_roll` (scuoti no).
- **Hint italiano**: *"gira la testa"* → spesso `base_pan`; *"solo la testa"* → `display_pan` (non obbligatorio).
- **Pattern opzionali** (nel prompt, non in codice):
  - *Centering*: rifocare un soggetto specifico con piccoli delta + rifoto (~3 cicli suggeriti).
  - *Persistent search / spatial verify*: nascondino, cercami, *cerca …*, *c'è X dietro di me* — ruota verso l'indizio spaziale + foto; mai *"non c'è"* dopo una sola immagine se l'utente ha indicato posizione o esistenza.
  - *Room exploration*: più angoli + note `SCAN_*` + sintesi finale.
  - *Gestures*: comandi espliciti utente (cenno sì) via LLM `move_body_joint` con `position` assoluta.
  - *Espressività automatica*: Kotlin esegue coreografie da campo `emotion` — il LLM non duplica nod/testa giù per puro mood.

## Espressività automatica (coreografie Kotlin)

Quando il corpo ESP32 è configurato, l'app esegue gesti **chiusi** (neutro testa → picco → neutro) in parallelo agli occhi:

| Componente | Ruolo |
|------------|--------|
| `EmotionGestureMapper` | `RobotEmotion` + intensità → `BodyChoreography` |
| `BodyExpressionMapper` | Transizione `RobotMood` → coreografia (poke, sonno, idle, …) |
| `HeadNeutralizer` | Centra `head_tilt` / `head_roll` / `display_pan` prima dei gesti |
| `BodyChoreographyExecutor` | Esecuzione sequenza su ESP32 |
| `BodyExpressionController` | Orchestrazione da VM (mood, ephemeral, micro-tick) |
| `BodyExpressionContext` | Guard: visione, LLM busy, `BodyHardwareBusyGate` |
| `BodyHardwareBusyGate` | Blocca gesti Kotlin durante tool LLM body |

### Trigger

| Evento | Gesto |
|--------|-------|
| Cambio `EphemeralExpression` (emotion LLM) | `EmotionGestureMapper` — anche durante dialogo (`Speaking` / `ActiveListening`) |
| TTS attivo (`Speaking`) | Micro-oscillazioni testa (`SpeakingMicroMoves`) — ritorno neutro a fine risposta |
| Transizione `RobotMood` | `BodyExpressionMapper` |
| Scadenza TTL ephemeral | Ritorno testa neutra (salvo `SLEEPING`) |
| Heartbeat micro-tick bored | Look-around silenzioso |

### Mappa emozione → corpo (ESP32)

| `emotion` | Gesto tipico |
|-----------|----------------|
| `sad` | `head_tilt` giù breve → ritorno 0 |
| `happy` / `loving` | cenno sì (`head_tilt`) |
| `surprised` | look-around `display_pan` |
| `confused` | `head_roll` breve → 0 |
| `angry` | `display_pan` gira via |
| `bored` | micro pan chiuso |
| `sleeping` | `body_home` + `head_tilt` ≈ −10° |

Priorità conflitti: **tool LLM body > visione > gesto ephemeral Kotlin > fidget standby**.

## Espressione corporea da umore (SSOT)

`MoodManager` guida anche il corpo fisico, non solo occhi e prompt:

| Componente | Ruolo |
|------------|--------|
| `BodyExpressionMapper` | Transizione `RobotMood` → `BodyChoreography` |
| `BodyExpressionController` | Esegue preset su ESP32 |
| `BodyExpressionContext` | Evita conflitti con turni LLM / visione / tool body |

Preset principali (Kotlin, immediati):

| Motivo mood | Movimento tipico |
|-------------|------------------|
| `EYE_POKE` angry | `display_pan` −12/−15 (gira via) |
| `EYE_POKE` confused | `head_roll` tilt → ritorno 0 |
| `USER_APOLOGY` | `body_home` o leggero ritorno verso utente |
| `IDLE_LONG` → bored | micro `display_pan` → 0 |
| Decay da annoyance | `body_home` |
| Entrata `SLEEPING` (notte) | Se non centrato → `body_home`; poi `head_tilt` ≈ −10° |

Il poke occhi **muove subito** il corpo (anche in dialogo). Obiettivi complessi (visione, scan) restano al planner LLM.

## Codice

- Client: `integration/body/BodyApiClient.kt`
- Coreografie: `BodyChoreography.kt`, `BodyChoreographyExecutor.kt`, `HeadNeutralizer.kt`, `EmotionGestureMapper.kt`, `SpeakingMicroMoves.kt`
- Espressione: `BodyExpressionController.kt`, `BodyExpressionMapper.kt`, `BodyHardwareBusyGate.kt`
- Impostazioni: `data/body/BodySettingsRepository.kt`
- Prompt: `integration/body/BodyPromptProviderImpl.kt`
- Tool: `integration/tool/hardware/*`
- Engine: `ReasoningModule.kt`, `ConversationViewModel.refreshReasoningEngineIfBodySettingsChanged()`

## API firmware

| Endpoint | Metodo | Tool LLM |
|----------|--------|----------|
| `/status` | GET | `body_status` |
| `/joint/{name}` | POST | `move_body_joint` |
| `/joints` | POST | `move_body_joints` |
| `/home` | POST | `body_home` |
| `/test` | POST | Solo UI Impostazioni |

Joint: `base_pan`, `head_roll`, `head_tilt`, `display_pan` (±45°).

## Tool LLM

| Tool | Località | Descrizione |
|------|----------|-------------|
| `move_body_joint` | HARDWARE | Un joint (`delta` o `position`) |
| `move_body_joints` | HARDWARE | Più joint in un comando |
| `body_home` | HARDWARE | Posizione neutra |
| `body_status` | HARDWARE | Stato motori e rete |

Registrazione: solo se `BodySettings.isConfigured()` (`enabled` + URL).

## Test manuale (checklist)

| Comando | Atteso (comportamento, non script fisso) |
|---------|------------------------------------------|
| Corpo disabilitato | Nessun tool body; nessun gesto Kotlin; solo occhi |
| *"Adesso sei triste"* | Occhi sad + testa giù breve (Kotlin) + ritorno neutro |
| *"Sii felice"* | Occhi happy + cenno sì (Kotlin) |
| *"Fai sì con la testa"* | LLM: nod con `position` 12→0; testa ancora centrata dopo ripetizioni |
| *"Gira la testa"* | Probabilmente `base_pan` |
| *"Gira solo la testa"* | Probabilmente `display_pan` |
| *"Guardati intorno e dimmi cosa vedi"* | Esplorazione multi-angolo ragionata + sintesi |
| *"Guarda a destra"* | `base_pan`, reply vuota |
| *"Guarda il mio cane"* | Foto + eventuale movimento funzionale + risposta |
| Heartbeat bored, idle 15+ min | Look-around silenzioso Kotlin (non LLM) |
| *"Vai a dormire"* | Pose sonno testa ~−10°, non resettata da ephemeral |

## Limiti (v1)

- Nessun tool `body_gesture` dedicato (gesti espliciti via catene LLM `move_body_joint`).
- Centering / room exploration: pattern LLM — nessun controller automatico Kotlin.
- Nessuna sincronizzazione gesto ↔ fonemi TTS.
- Espressività mood: coreografie deterministiche Kotlin + LLM per compiti fisici.

## Riferimento firmware

`myDeskBody/docs/DOCUMENTAZIONE_UTILIZZO.md`
