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
| Proattività | `docs/guides/PROATTIVITA.md` | `docs/PROACTIVE_ARCHITECTURE.md` |
| Umore | `docs/guides/UMORE.md` | `docs/MOOD.md`, `docs/ROBOT_EXPRESSIONS.md` |

Indice guide: `docs/guides/README.md`.

## Panoramica sistema (sintesi documentazione)

**Identità:** non assistente generico ma **compagno di scrivania** — memoria locale trasparente, corpo fisico opzionale (ESP32), proattività situazionale, silenzio come output valido.

**Evoluzione:** da Q&A vocale → **agente cognitivo** con memoria unificata RAG, input paralleli (notifiche, promemoria), heartbeat autonomo e corpo ESP32.

### Mappa documenti

| File | Argomento | Note |
|------|-----------|------|
| `docs/TOOL_ARCHITECTURE.md` | Design tool, JSON, catene, 3 layer | SSOT architettura tool |
| `docs/INPUT_ARCHITECTURE.md` | Bus input, notifiche, policy DEFERRED/BLOCKING | Gate: microfono attivo |
| `docs/STT_ARCHITECTURE.md` | Pipeline STT unificata (Android/Vosk) | Pausa durante TTS |
| `docs/SCHEDULED_TASKS.md` | Promemoria vocali | AlarmManager + voce |
| `docs/MEMORY.md` | Memoria utente, categorie, recall | |
| `docs/MEMORY_ACCESS.md` | Write/read unificato, `memory_documents.db` | SSOT dialogo |
| `docs/MEMORY_RECALL_PLANNER.md` | Piano JSON recall per turno vocale | **No fallback Kotlin** se fallisce |
| `docs/MEMORY_EMBEDDING.md` | RAG semantico ONNX | Hybrid 0.7 cosine + 0.3 token |
| `docs/MEMORY_REVIEW_FOLLOWUP.md` | Projection guard, budget, safety pin L1 | |
| `docs/ACTIVITY_LOG.md` | Log Day episodico (7 giorni) | PHYSICAL_NOW, PLAN, SOCIAL_THREAD |
| `docs/SPATIAL_MEMORY.md` | Stanze, landmark visivi, auto-localizzazione | |
| `docs/ROBOT_CONTEXT.md` | Profili lavoro/call, silenzio notifiche | |
| `docs/PROACTIVE_ARCHITECTURE.md` | Predictivity + Wellness, ordine come input visivo | **SSOT proattività** |
| `docs/HEARTBEAT_ARCHITECTURE.md` | Heartbeat alarm, micro-tick | Care + custom domains → Wellness toggles (H7) |
| `docs/DESK_PRESENCE.md` | ML Kit presenza + centering; `UserPresencePolicy` | |
| `docs/BODY_INTEGRATION.md` | ESP32 myDeskBody HTTP REST | Tool HARDWARE |
| `docs/PROMPT_PHILOSOPHY.md` | Capability + vincoli, non playbook rigido | SSOT labels prompt |
| `docs/AGENT_REASONING.md` | Tool diretto vs catena, persistent search | `ChainSpeechPolicy` |
| `docs/ROBOT_EXPRESSIONS.md` | Token `emotion` → occhi | Dettaglio token; architettura → `MOOD.md` |
| `docs/MOOD.md` | Umore persistente + espressione effimera | **SSOT mood** — triggers, valence, prompt |
| `docs/nextPromptv1.md` | Persona cognitiva v1.6 | Runtime: `llm_system_prompt.txt` |
| `docs/LLM_SETTINGS.md` | LM Studio / Gemini | |
| `docs/WEB_SEARCH.md` | SearXNG + `fetch_url` | |
| `docs/VISION.md` | Flusso visione (legacy `imageRequired`) | Preferire tool chain |
| `docs/TODO.md` | Backlog H3–H6 e utility | **SSOT roadmap aperta** |
| `docs/guides/MEMORIA.md` | Panoramica memoria (IT, umano) | |
| `docs/guides/MEMORIA_TECNICA.md` | Flussi recall/write (IT, dev) | |
| `docs/guides/PROATTIVITA.md` | Proattività panoramica (IT, umano) | |
| `docs/guides/UMORE.md` | Umore robot panoramica (IT, umano) | |
| `docs/guides/UMORE_SMOKE.md` | Smoke test occhi + corpo | |
| `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` | Visione OODA, 6 pilastri autonomia | Draft — vedi avvertenze sotto |
| `docs/Drafts/SPEAKER_IDENTIFICATION.md` | Speaker ID embedding, privacy, `enroll_speaker` | Draft — §9 dubbi aperti, non implementato |
| `docs/Drafts/AgentEvolution-GapAnalysis.md` | Gap vs draft Claude | **Obsoleto** (maggio 2026) |
| `docs/Drafts/STT-Analysis.md` | Bug STT latenza storico | **Obsoleto** — fix in STT_ARCHITECTURE |

### Memoria (due livelli)

| Livello | Ruolo |
|---------|--------|
| **Store operativi** | `activity_log.db`, `scheduled_tasks`, `list_items`, `spatial_places` |
| **Indice cognitivo SSOT** | `memory_documents.db` — unico accesso dialogo |

**Recall vocale:** frase utente → `LlmMemoryRecallPlanner` (JSON) → `recallForQuestion()` → blocco MEMORIA (max 60 righe) → LLM dialogo.

**Write path:** `UnifiedMemoryWriter` + proiezione obbligatoria; `MemoryProjectionGuard` + reconcile settimanale.

**Categorie:** IDENTITY/PREFERENCE/ROUTINE/FACT (permanenti, visibili utente) · OBSERVATION/INTENT/PATTERN (TTL heartbeat, solo robot) · EPISODE/REMINDER/LIST_ITEM/SPATIAL (recall + UI parziale).

**Storage semantics:** fatto duraturo → `save_memory` · task → `add_list_item` TODO · nota → `add_list_item` NOTE · spesa → `add_list_item` SHOPPING · allarme orario → `set_reminder`.

### Autonomia e roadmap

**Loop OODA (target):** OBSERVE (Log Day + room order in Wellness tick) → ORIENT → DECIDE (Wellness / Predictivity deviation) → ACT → REFLECT (`weekly_reflection` + habit miner).

| Fase | Contenuto | Stato (`docs/TODO.md`) |
|------|-----------|------------------------|
| H1 | Heartbeat base (scheduler, playbook, micro-tick) | ✅ Completato (shell; care → Wellness) |
| H2 | `speak_confidence`, soglia invasività | ✅ Completato |
| **H7** | **Predictivity + Wellness unificato** | ✅ Completato (v1) — smoke manuale aperto |
| H3 | State machine emozioni (`MoodManager` + corpo ESP32) | 🟡 Parziale — core ok, smoke `docs/guides/UMORE_SMOKE.md` |
| H4 | Working memory giornaliera | 🟡 Parziale — prompt anti-ripetizione + ignoro utente |
| H5 | Self-reflection settimanale | 🟡 Parziale — gate mic E2E + consolidamento PATTERN |
| H6 | Theory of mind (awareness utente) | ❌ Rimosso keyword-based (`UserAwareness`); tono utente via LLM `user_tone` (solo mood robot) — redesign proattivo aperto |

**Scala invasività:** 0 silenzio (80–95%) → 1 solo occhi → 2 voce breve → 3 tool info → 4 tool che modifica → 5 azione senza chiedere (whitelist).

**Regole non negoziabili:** DEFERRED, notte soppressa, robot context SILENT, cooldown 20 min tra proactive speak, cap **3 proactive speak/giorno** (`ProactiveGatePolicy`), mic off = niente tick LLM.

### Stato implementazione

**Fatto:** architettura 3 layer, tool JSON + catene, STT unificato, input notifiche, memoria unificata RAG, recall planner LLM, activity log, spatial memory, robot context, web/meteo/Spotify, body ESP32 + espressione corpo (mood/ephemeral/speaking), desk presence ML Kit + attention centering, heartbeat H1–H2, proattività H7 v1, domini attenzione (Wellness), liste strutturate.

**Backlog:** chiusura H3–H5 (smoke, WM wellness, reflection memory), H6 redesign LLM-driven se serve umore utente in proattività, news/traduttore/domotica, memory pin Level 2, tool note dedicato, speaker ID (draft).

### Avvertenze documentazione

- **`docs/PROACTIVE_ARCHITECTURE.md`** è SSOT per Predittività + Wellness (H7 v1 implementato); heartbeat resta per micro-tick.
- **`docs/TODO.md`** è SSOT per roadmap aperta; `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` segna H1–H6 come fatti — **disallineamento noto**, fidarsi di `TODO.md` per ciò che resta da fare.
- **Draft obsoleti:** non usare `AgentEvolution-GapAnalysis.md`, `STT-Analysis.md` come SSOT — citano componenti rimossi o fix già applicati. Memoria RAG unificata: SSOT `docs/MEMORY.md`, `docs/MEMORY_EMBEDDING.md`, `docs/MEMORY_ACCESS.md` (draft `UNIFIED_MEMORY_RAG_PLAN` rimosso giugno 2026).
- **WhatsApp/telefono:** tool in tabella sotto, nessuna spec dedicata in `docs/` — comportamento da `AGENTS.md` + codice.

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
8b. **Per proattività target (Predittività + Wellness, ordine come input)** → `docs/PROACTIVE_ARCHITECTURE.md`; guida umana → `docs/guides/PROATTIVITA.md`. Heartbeat shell (micro-tick) → `docs/HEARTBEAT_ARCHITECTURE.md`.
9. Per corpo fisico ESP32 (myDeskBody) → `docs/BODY_INTEGRATION.md` + prompt dinamico `body_capabilities_prompt.txt` via `BodyPromptProviderImpl`.
9b. **Assembly prompt runtime** (`ReasoningEngineImpl.buildPromptWithContext`): base `llm_system_prompt.txt` + AVAILABLE TOOLS + condizionali: `body_capabilities` (ESP32), `heartbeat_playbook` (solo tick heartbeat/weekly_reflection), memory/day/activity/robot/spatial/mood. SSOT labels: `docs/PROMPT_PHILOSOPHY.md`.
10. **Per filosofia prompt (capability + vincoli, esempi illustrativi)** → `docs/PROMPT_PHILOSOPHY.md`.
11. Per policy risoluzione autonoma (tool diretto vs catena) → `docs/AGENT_REASONING.md`.
12. Per espressioni occhi (campo `emotion` LLM) → `docs/ROBOT_EXPRESSIONS.md`; architettura umore → `docs/MOOD.md`; guida umana → `docs/guides/UMORE.md`.
13. **Per visione agente autonomo (heartbeat, OODA, emozioni)** → `docs/Drafts/AUTONOMOUS_AGENT_VISION.md`.
14. **Per persona cognitiva e policy autonome (spec concettuale + mapping JSON)** → `docs/nextPromptv1.md` (runtime: `llm_system_prompt.txt` + `body_capabilities_prompt.txt`).
15. **Umore a due livelli (SSOT)** → `docs/MOOD.md`: **valenza persistente** (`MoodManager`, `STATO ROBOT`); **emotion** LLM = espressione effimera (occhi/TTS, TTL ~25–40 s) + delta valenza opzionale via `LlmEmotionValenceMapper`.
16. **Per memoria spaziale / auto-localizzazione stanza** → `docs/SPATIAL_MEMORY.md`.
17. **Per speaker identification (embedding vocale, privacy, enrollment)** → `docs/Drafts/SPEAKER_IDENTIFICATION.md` (draft).
18. Scope minimo: una capability per volta.
19. Non committare segreti; usare `local.properties.example`.
20. **Test coverage e lacune** → `docs/TODO.md` sezione *Test coverage*.

## Test (sintesi)

~110 unit test in `app/src/test`. Forte su domain/parser/memoria; debole su `ConversationViewModel`, `HeartbeatOrchestrator`, integrazione presenza/corpo runtime. Dettaglio: `docs/TODO.md`.

## Regressioni (obbligo agente)

Ogni modifica a runtime, prompt o recall deve **preservare i comportamenti documentati** salvo richiesta esplicita dell'utente.

| Prima di chiudere | Azione |
|-------------------|--------|
| **Scope** | Toccare solo file e flussi del task; niente refactor o pulizie collaterali. |
| **Contratto** | Se cambia comportamento visibile (voce, memoria, tool, UI), aggiornare **spec** (`docs/`) e, se serve, **guide** (`docs/guides/`). |
| **Test automatici** | Eseguire unit test del modulo toccato (es. memoria → `UnifiedMemoryRepositoryTest`, `LlmMemoryRecallPlannerTest`, `MemoryRecallPlanMappingTest`). Aggiungere/aggiornare test o golden fixture quando si cambia logica non banale. |
| **Prompt** | Modifiche a `*_prompt.txt`: verificare che esempi JSON e tool notes non contraddicano il codice (es. shortcut rimosso ≠ esempio che lo richiama ancora). |
| **Smoke umano** | Dove non c'è coverage (ViewModel, catena vocale E2E), indicare all'utente **1–3 frasi** da riprovare su device dopo rebuild APK. |

**Segnali tipici di regressione:** percorso Kotlin parallelo al LLM; planner vs dialogo che si contraddicono; tool che bypassano `UnifiedMemoryWriter` / `recallForQuestion`; comportamento diverso tra frase esatta e frase naturale sulla stessa intenzione.

Checklist estesa: `.cursor/rules/70-testing-and-quality.mdc`.

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
| `save_memory` | LOCAL | Salva fatto utente (IDENTITY/PREFERENCE/ROUTINE/FACT, optional `pinned`) o memoria autonoma heartbeat (OBSERVATION/INTENT/PATTERN + `ttl_days`) |
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
| `notification` | DEFERRED | Notifiche da app (WhatsApp, Teams, SMS, etc.) |
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
