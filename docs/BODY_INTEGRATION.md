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
  - *Gestures*: es. cenno sì con catena `head_tilt`.
  - *Mood-informed body*: `STATO ROBOT` può suggerire espressione fisica opzionale (bored → look-around silenzioso, angry → stillness/turn away, happy → cenno leggero) — planner libero, non ogni turno.

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

## Espressione corporea da umore (SSOT)

`MoodManager` guida anche il corpo fisico, non solo occhi e prompt:

| Componente | Ruolo |
|------------|--------|
| `BodyExpressionMapper` | Transizione `RobotMood` → preset (`display_pan`, `head_roll`, `body_home`, …) |
| `BodyExpressionController` | Esegue preset su ESP32 quando il mood cambia |
| `BodyExpressionContext` | Evita conflitti con turni LLM / visione |

Preset principali (Kotlin, immediati):

| Motivo mood | Movimento tipico |
|-------------|------------------|
| `EYE_POKE` angry | `display_pan` −12/−15 (gira via) |
| `EYE_POKE` confused | `head_roll` +8 |
| `USER_APOLOGY` | `body_home` o leggero ritorno verso utente |
| `IDLE_LONG` → bored | micro `display_pan` |
| Decay da annoyance | `body_home` |
| Entrata `SLEEPING` (notte) | Se non centrato → `body_home`; poi `head_tilt` ≈ −10° (testa leggermente abbassata) |

Il poke occhi **muove subito** il corpo (anche in standby). Obiettivi complessi (visione, scan) restano al planner LLM.

## Codice

- Client: `integration/body/BodyApiClient.kt`
- Espressione mood: `integration/body/BodyExpressionController.kt`, `BodyExpressionMapper.kt`
- Impostazioni: `data/body/BodySettingsRepository.kt`
- Prompt: `integration/body/BodyPromptProviderImpl.kt`
- Tool: `integration/tool/hardware/*`
- Engine: `ReasoningModule.kt`, `ConversationViewModel.refreshReasoningEngineIfBodySettingsChanged()`

## Test manuale (checklist)

| Comando | Atteso (comportamento, non script fisso) |
|---------|------------------------------------------|
| Corpo disabilitato | Nessun tool body; LLM non chiama `move_body_joint` |
| *"Fai sì con la testa"* | Gesto `head_tilt` (LLM può variare delta), risposta vuota |
| *"Gira la testa"* | Probabilmente `base_pan` |
| *"Gira solo la testa"* | Probabilmente `display_pan` |
| *"Guardati intorno e dimmi cosa vedi"* | Esplorazione multi-angolo ragionata + sintesi (angoli non fissi) |
| *"Guarda a destra"* | `base_pan`, reply vuota |
| *"Guarda il mio cane"* | Foto + eventuale movimento se LLM giudica utile + risposta |
| *"C'è qualcuno alla scrivania?"* | Foto → eventuale esplorazione → risposta |
| Heartbeat bored, idle | Può tacere O muoversi silenziosamente — scelta del planner |
| Corpo disabilitato + focus soggetto | Solo `take_photo` |

## Limiti (v1)

- Nessun tool `body_gesture` (gesti via catene `move_body_joint`).
- Centering / room exploration: **pattern suggeriti nel prompt**, closed-loop guidato LLM — nessun controller automatico in codice Kotlin.
- Nessun closed-loop automatico post-promemoria.
- Umore → corpo: preset deterministici in `BodyExpressionController` (SSOT con `MoodManager`); LLM resta libero per catene goal-driven.

## Riferimento firmware

`myDeskBody/docs/DOCUMENTAZIONE_UTILIZZO.md`
