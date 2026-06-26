# My Desk Robot — Istruzioni per agenti AI

App Android (Kotlin + Jetpack Compose) per dialogo vocale con LLM e interfaccia a **due occhi animati**.

## Funzionalità

1. Speech-to-Text (input vocale)
2. Invio domanda a LLM e ricezione risposta
3. Text-to-Speech (lettura risposta)
4. UI occhi con emozioni animate (neutral, happy, angry, bored, …)
5. **Tool Architecture**: esecuzione di tool locali e remoti orchestrati dal LLM
6. **Input Architecture**: input esterni paralleli alla voce (notifiche, sensori, hardware)

## Architettura a 3 Layer

```
Layer 1 (Robot UI)     → Android-specific: STT, TTS, UI, ViewModel
Layer 2 (Reasoning)    → Kotlin puro, riutilizzabile: reasoning/, tool chain
Layer 3 (Integrations) → LLM providers, tool implementations: integration/
```

Per dettagli vedere `docs/TOOL_ARCHITECTURE.md`.

### Package Structure

```
com.example.mydeskrobot/
├── reasoning/           # Layer 2 - Kotlin puro, NO Android
│   ├── ReasoningEngine.kt
│   ├── ToolChainOrchestrator.kt
│   ├── llm/LlmClient.kt
│   ├── tool/ToolExecutor.kt
│   └── model/           # LlmAction, ToolInvocation, etc.
│
├── integration/         # Layer 3 - Implementations
│   ├── llm/LmStudioClient.kt
│   └── tool/
│       ├── ToolRouter.kt
│       ├── local/       # BrowserTool, CameraTool, etc.
│       └── remote/      # WeatherTool, etc.
│
└── presentation/        # Layer 1 - Android UI
```

## Regole Cursor

Le linee guida dettagliate sono in **`.cursor/rules/*.mdc`**:

| File | Contenuto |
|------|-----------|
| `00-project-overview.mdc` | Visione, vincoli globali (sempre attiva) |
| `10-architecture.mdc` | MVVM, layer, package structure (sempre attiva) |
| `20-kotlin-standards.mdc` | Stile Kotlin, coroutine, naming |
| `30-jetpack-compose.mdc` | Compose, Material 3, schermata |
| `40-speech-llm-tts.mdc` | STT, LLM, TTS, permessi |
| `50-robot-eyes-ui.mdc` | Occhi, emozioni, animazioni |
| `60-security-and-config.mdc` | API key, privacy |
| `70-testing-and-quality.mdc` | Test e checklist |

## Documentazione: `docs/` vs `docs/guides/`

| Cartella | Pubblico | Lingua | Uso |
|----------|----------|--------|-----|
| **`docs/`** | Agenti AI, implementatori | Prevalentemente inglese | Contratti, architettura, path sorgente, checklist QA — **SSOT tecnico** per codice e PR |
| **`docs/guides/`** | Umano (configuratore, tester) | Italiano | Guide narrative, diagrammi, FAQ — comprensione e smoke test; **non** sostituiscono le spec |

**Regola:** per implementare una feature, leggere `docs/` (e questa pagina). Per capire il comportamento dal punto di vista umano, leggere `docs/guides/`. Se cambia il runtime, aggiornare entrambe le linee.

| Argomento | Guide (umano) | Spec (agente) |
|-----------|---------------|---------------|
| Memoria | `docs/guides/MEMORIA.md`, `docs/guides/MEMORIA_TECNICA.md` | `docs/MEMORY.md`, `docs/MEMORY_ACCESS.md` |

Indice guide: `docs/guides/README.md`.

## Prima di implementare

1. Leggere `00` e `10`.
2. Per feature vocali/LLM → `40`; per occhi → `50`.
3. Per tool → `docs/TOOL_ARCHITECTURE.md`.
4. Per input esterni → `docs/INPUT_ARCHITECTURE.md`.
5. Per pipeline STT (orchestrator + provider) → `docs/STT_ARCHITECTURE.md`.
6. Per task schedulati / promemoria vocali → `docs/SCHEDULED_TASKS.md`.
7. Per memoria utente (unified recall + tool + consolidation) → `docs/MEMORY.md` (spec); panoramica umana → `docs/guides/MEMORIA.md`.
7b. Per log episodico Log Day (piani, thread sociali, EPISODI in unified recall) → `docs/ACTIVITY_LOG.md`.
7c. Per accesso unificato memoria (write path, recall, notifiche unread) → `docs/MEMORY_ACCESS.md`.
7d. Per LLM recall planner (piano JSON per turno vocale, no fallback) → `docs/MEMORY_RECALL_PLANNER.md`.
8. Per contesto robot / silenzio notifiche → `docs/ROBOT_CONTEXT.md`.
9. Per corpo fisico ESP32 (myDeskBody) → `docs/BODY_INTEGRATION.md` + prompt dinamico `body_capabilities_prompt.txt` via `BodyPromptProviderImpl`.
9b. **Assembly prompt runtime** (`ReasoningEngineImpl.buildPromptWithContext`): base `llm_system_prompt.txt` + AVAILABLE TOOLS + condizionali: `body_capabilities` (ESP32), `heartbeat_playbook` (solo tick heartbeat/weekly_reflection), memory/day/activity/robot/spatial/mood. SSOT labels: `docs/PROMPT_PHILOSOPHY.md`.
10. **Per filosofia prompt (capability + vincoli, esempi illustrativi)** → `docs/PROMPT_PHILOSOPHY.md`.
11. Per policy risoluzione autonoma (tool diretto vs catena) → `docs/AGENT_REASONING.md`.
12. Per espressioni occhi (campo `emotion` LLM) → `docs/ROBOT_EXPRESSIONS.md`.
13. **Per visione agente autonomo (heartbeat, OODA, emozioni)** → `docs/Drafts/AUTONOMOUS_AGENT_VISION.md`.
14. **Per persona cognitiva e policy autonome (spec concettuale + mapping JSON)** → `docs/nextPromptv1.md` (runtime: `llm_system_prompt.txt` + `body_capabilities_prompt.txt`).
15. **Umore a due livelli (SSOT)** → `MoodManager`: **valenza persistente** (±1, solo eventi codificati) in `STATO ROBOT`; **emotion** LLM = espressione effimera (occhi/TTS, TTL ~30s, non modifica valenza).
16. **Per memoria spaziale / auto-localizzazione stanza** → `docs/SPATIAL_MEMORY.md`.
17. Scope minimo: una capability per volta.
18. Non committare segreti; usare `local.properties.example`.

## Tool disponibili

| Tool | Località | Descrizione |
|------|----------|-------------|
| `take_photo` | LOCAL | Scatta foto con la camera |
| `detect_presence` | LOCAL | Verifica silenziosa presenza alla scrivania (heartbeat) |
| `analyze_room_scene` | LOCAL | Estrae landmark stanza da una foto (vision) |
| `match_place` | LOCAL | Confronta landmark con stanze memorizzate |
| `save_place` | LOCAL | Crea/aggiorna stanza memorizzata |
| `list_places` | LOCAL | Elenca luoghi attivi |
| `set_current_place` | LOCAL | Conferma SSOT stanza corrente (dopo match o conferma utente) |
| `get_weather` | REMOTE | Meteo da OpenWeatherMap |
| `web_search` | REMOTE | Ricerca web via SearXNG JSON (config: `SEARX_BASE_URL`) |
| `fetch_url` | REMOTE | Legge pagina web come testo (OkHttp + Jsoup) |
| `open_browser` | LOCAL | Apre URL nel browser |
| `resolve_phone_contact` | LOCAL | Cerca numero in rubrica/memorie (alias mamma/madre); prima di `dial_phone` |
| `dial_phone` | LOCAL | Apre dialer con numero precompilato (utente tap Chiama); pausa STT in chiamata se permesso telefono |
| `resolve_whatsapp_target` | LOCAL | Cerca chat WhatsApp (contatto/gruppo) in rubrica/memorie |
| `send_whatsapp` | LOCAL | Apre WhatsApp con messaggio precompilato (utente tap Invia) |
| `play_spotify` | LOCAL | Apre Spotify con ricerca (artista, genere, musica) |
| `set_robot_context` | LOCAL | Contesto robot (lavoro/call/riunione) e silenzio notifiche robot-only |
| `set_reminder` | LOCAL | Schedula task (annuncio vocale + notifica a scadenza) |
| `get_reminders` | LOCAL | Elenca promemoria attivi |
| `delete_reminder` | LOCAL | Cancella promemoria per id |
| `save_memory` | LOCAL | Salva fatto utente (IDENTITY/PREFERENCE/ROUTINE/FACT) o memoria autonoma heartbeat (OBSERVATION/INTENT/PATTERN + `ttl_days`) |
| `log_daily_activity` | LOCAL | Registra attività effimera (pasto, passeggiata, pausa) nel log 7 giorni |
| `list_memories` | LOCAL | Elenca memorie attive |
| `delete_memory` | LOCAL | Dimentica per id o argomento (match fuzzy, più memorie correlate) |
| `add_list_item` | LOCAL | Aggiunge nota/todo/spesa |
| `list_items` | LOCAL | Elenca elementi lista strutturata |
| `update_list_item` | LOCAL | Aggiorna testo o checked |
| `delete_list_item` | LOCAL | Rimuove elemento per id o testo |
| `set_volume` | LOCAL | Controlla volume media |
| `make_light` | LOCAL | Modalità lampada: schermo bianco luminoso o ripristino tema scuro |
| `show_notification` | LOCAL | Mostra notifica di sistema |
| `move_body_joint` | HARDWARE | Muove un joint del corpo ESP32 (myDeskBody) |
| `move_body_joints` | HARDWARE | Muove più joint del corpo in un comando |
| `body_home` | HARDWARE | Ritorno posizione neutra corpo |
| `body_status` | HARDWARE | Stato motori e connettività corpo |

## Input esterni disponibili

| Input | Priorità | Descrizione |
|-------|----------|-------------|
| `notification` | DEFERRED | Notifiche da app (WhatsApp, SMS, etc.) |
| `scheduled_task` | DEFERRED | Promemoria utente a scadenza (voce + notifica) |
| `hardware_button` | BLOCKING | Pulsante fisico ESP32 (futuro) |
| `sensor_reading` | DEFERRED | Sensori ambientali (futuro) |

Gli input esterni vengono elaborati dal LLM quando il microfono è attivo. Dettagli: `docs/INPUT_ARCHITECTURE.md`.

## Configurazione LLM (runtime)

- **Impostazioni** (ingranaggio in basso a sinistra) → LLM
- Provider: **LM Studio** (locale) o **Gemini** (Google AI)
- Persistenza: DataStore + EncryptedSharedPreferences per API key
- Dettagli: `docs/LLM_SETTINGS.md`

## Stack

- `minSdk` 24, `compileSdk` 35, Compose BOM, Material 3
- Package attuale: `com.example.mydeskrobot`
- LLM: `LlmClient` → `LmStudioClient` | `GeminiClient` (factory da settings)
