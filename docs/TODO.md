# TODO — My Desk Robot

> **SSOT roadmap aperta.** Visione autonoma (draft storico, non checklist): `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` — **fidarsi di questo file** per ciò che resta da fare.  
> **Ultimo allineamento codice/doc:** settembre 2026.

---

## Prossimo focus

### H3 — Emotional state machine

- [x] Umore dinamico H3: `TurnMoodEvaluator`, hotword idle, burst/ripetizione, tier happy LLM, ephemeral neutral
- [ ] Smoke manuale: [`guides/UMORE_SMOKE.md`](guides/UMORE_SMOKE.md) (standby vs sessione attiva vs notte, occhi + corpo ESP32)
- [x] **Svago → valence base:** look-around `+0.04`, distraction completa `+0.08` (`SelfCare*`); skip sleep/night; interrupt senza bump distraction — [`MOOD.md`](MOOD.md)
- [ ] **(Idea) Attaccamento / simpatia verso l’utente** — asse separato dalla wellbeing; gioco *con* l’utente vs svago in solitaria (da progettare, non implementare ora)
- [ ] **(Idea) Open thread / richiamo affettivo:** slot leggero “ci eravamo detti…” (riunione, birra con amici, …) — scritto se LLM/marca evento importante; consumato **una volta** in fascia speak (stessi gate di proattività / non inventare); max rarità. Non nuovo store fatti IDENTITY — continuità di presenza. Priorità dopo smoke Predictivity.
- [ ] Opzionale dopo smoke: cap orario self-care se gonfia troppo il mood

### H4 — Working memory

- [ ] `recordIgnoredSuggestion` su ignoro esplicito utente (oggi: timeout in `ProactiveTracker` + stats settimanali)
- ~~Iniettare topic anti-ripetizione nel wellness~~ — non necessario (check 1×/giorno; dominio disattivabile in UI)

### H5 — Self-reflection settimanale

- [ ] Consolidamento memoria post-reflection (PATTERN/OBSERVATION misurabili, non solo save LLM generico)

### H6 — Theory of mind / umore utente in proattività

- ~~Umore utente nelle decisioni proattive~~ — **non obiettivo** (ott 2026). `UserAwarenessState` già rimosso; nessuna reintroduzione. `user_tone` resta solo per umore **robot** (lodi/scuse), non per gate proattivi.

### Follow-up proattività (post-H7 v1)

- [x] `wellnessPresentEnough` cablato (body first → fallback interazione; idle buffer separato)
- [x] Wellness done solo dopo esito; micro-tick switch separato da wellness/predittività
- [ ] Smoke manuale: [`guides/PROATTIVITA_SMOKE.md`](guides/PROATTIVITA_SMOKE.md)

### Wellness v2 — compute ≠ communicate (ott 2026)

Spec SSOT: [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md) Channel 2 · guida [`guides/PROATTIVITA.md`](guides/PROATTIVITA.md).

- [x] **Docs** — calcolo con gate leggeri; snapshot LLM SSOT; notifica se attenzione; voce solo on-demand; UI riepilogo
- [x] **Plan** — store DTO, wire notify, retirement TTS path, ask/inject
- [x] **Code** — `WellnessWatcher` senza idle/presence; persist snapshot; notify cap; drop proactive TTS; summary UI; dialogue ask (`STATO WELLNESS`)
- [x] **Wellness v2 harden (ott 2026):** compute mic-independent; mark done solo se snapshot salvato; clear inFlight / retry se JSON senza `wellness` — smoke device: check **partito** e snapshot in Riepilogo
- [x] **Scoring pasti — assenza ≠ ok (ott 2026):** prompt + `(none logged today)` nel contesto; `ok` solo con evidenza positiva; dopo finestra pranzo/cena senza log → `attention`
- [x] **Work vs social channels (ott 2026):** Teams/Slack → `WORK_THREAD` (carico_lavoro); WhatsApp/… → `SOCIAL_THREAD` (`WorkCommunicationChannels`) — no backfill storico
- [ ] Smoke v2 completo in [`guides/PROATTIVITA_SMOKE.md`](guides/PROATTIVITA_SMOKE.md) (Predittività / abitudini / notifica — ancora aperto; Wellness compute+persist ok in smoke parziale)
- [ ] Feedback loop strutturato dopo proactive speak (accettato / ignorato / rifiutato)
- [ ] **Proattività + Abitudini dedotte — verifica end-to-end (ott 2026):** in uso reale **non sembrano presenti / non funzionano correttamente** (lista vuota o slot che non compaiono; Predittività quasi mai propone nulla; a volte tratta come fatte cose non fatte). Da verificare prima di redesign:
  - **Mining:** Log Day → `RecurringHabitSlotMiner` → persistenza slot / confidence / UI Impostazioni → Proattività → «Abitudini dedotte» (perché spesso vuota?)
  - **Eleggibilità:** gate confidence, hitCount, finestra oraria, presenza, mic, cooldown, cap giornaliero
  - **`DeviationWatcher`:** trigger reale vs silenzio; system input → LLM → speak
  - **Prompt / recall:** `ABITUDINI ATTESE` / PATTERN = aspettative, mai prova di attività fatte; confrontare solo vs Log Day / HC
  - Smoke formale: [`guides/PROATTIVITA_SMOKE.md`](guides/PROATTIVITA_SMOKE.md) + spec [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md)
- [ ] **`ProactiveSpeakGate` — studio utilizzo (non toccare codice per ora):** chiarire se ha ancora senso così (solo cap/cooldown/fascia/SILENT/translation) vs aspettativa “momento in cui disturba meno”; chi lo chiama (Predictivity, commitment verify, …); overlap con presence/idle/mic; eventuale semplificazione o split. **Solo analisi** finché non si decide.

### Fire-and-check / impegni

- [x] **Commitment sticky (caso 1, ott 2026):** «assolutamente» senza orario → pin icona fino a Elimina (`createStickyCommitment`); prompt non inventa ora / non TODO — [`FIRE_AND_CHECK.md`](FIRE_AND_CHECK.md)
- [ ] **Caso 2 timing vs deadline:** Verifica +60′ dal primario può cadere dopo l’appuntamento (es. lastre 16:30 → verify 17:30); backlog — non toccare +60 per ora
- [ ] **Fire-and-check + commitment follow-up — verifica comportamento reale (ott 2026):** mai smoke formale; dubbi sul fisico in uso quotidiano. Studiare **entrambi** i kind vs [`FIRE_AND_CHECK.md`](FIRE_AND_CHECK.md) (solo analisi, niente redesign finché non chiarito):
  - **PHYSICAL** (`fire_and_check`): creazione loop, secondo reminder «Verifica…», presenza/foto, UI icone, chiusura/skip
  - **COMMITMENT** timed (`commitment_follow_up` → auto «Verifica impegno:» +60 min): primario vs follow-up, voce gated da `ProactiveSpeakGate`, rememorize max 1, UI
  - Overlap reminder / gate speak / notifica-only se mic off

---

## Backlog prodotto

| Item | Note |
|------|------|
| Memory pin + Riorganizza | ✅ `isPinned`, exact dedup, LLM consolidation auto/manuale (gate configurabile); ⬜ fragmentation analyzer |
| Tool **Note** dedicato | Oggi: `add_list_item` type `NOTE` |
| **News** summarization | Chunking in `fetch_url` ok; summarization ONNX opzionale |
| **Traduttore** | ✅ v1 continuous mode — [`TRANSLATION_MODE.md`](TRANSLATION_MODE.md); one-shot via dialogo |
| Sensori ambiente | Input `sensor_reading` |
| **Domotica** | Termostato/luci con conferma utente — **via Google Home APIs (Android)**, non Home Graph sync. Spike: tool LLM discovery+comando (vedi sotto) |

### Domotica — Google Home APIs (idea, non implementare ora)

Auth OAuth/consent → LLM risolve con tool tipizzati (come body/web), **conferma utente** su comandi che cambiano stato:

- [ ] `list_home_devices` — elenco device (struttura/stanza, tipo, id)
- [ ] `list_home_device_traits` (o `get_home_device`) — trait/comandi/attributi supportati su un device (`supports`)
- [ ] `home_device_command` — esegue un comando su trait (on/off, level, thermostat, …) solo dopo conferma / invasività alta
- Home Graph sync/report **non** usare; SDM solo se serve Nest-only in parallelo
- Vincoli: tipizzati (no JSON libero inventato), registry tipi/trait in app, cloud Google obbligatorio |
| Pulsante hardware ESP32 | `hardware_button` BLOCKING |
| **Speaker ID** | Draft `docs/Drafts/SPEAKER_IDENTIFICATION.md` |
| Sessione vocale auto post-notifica | `activateVoiceSession()` non collegato al TTS notifica — `INPUT_ARCHITECTURE.md` §1.1 |
| **Commitment follow-up** | ✅ v1 — urgenza → `commitment_follow_up` + `Verifica impegno` — [`FIRE_AND_CHECK.md`](FIRE_AND_CHECK.md) |
| **Notification proxy** | ✅ v1 — [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md); guida [`guides/PROXY_NOTIFICHE.md`](guides/PROXY_NOTIFICHE.md) |
| **Phone capability bridge** | ✅ v1 — pull exercises/contacts + dual reminder — [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) |
| **Skill editor Blockly** | Draft `docs/Drafts/SKILL_EDITOR_BLOCKLY.md` — non implementato |
| **Body kinematics prompt** | ⬜ Clarificare movimento ESP32 nel prompt/doc (vedi sotto) |

### Body — cinematica / prompt (aperto, ott 2026)

Oggi `body_capabilities_prompt.txt` e tool notes sono troppo generici: segni L/R/su-giù poco espliciti, POV robot vs utente ambiguo («mirror perspective» vs tabella «my right»), poca mappa servo → effetto fisico.

Da fare (prompt normativo + allineo `BODY_INTEGRATION.md` / descrizioni tool):

- [ ] **POV unico**: destra/sinistra = sempre dal punto di vista del robot (o sempre utente — ma una sola regola), documentata come hard constraint
- [ ] **Tabella segni per joint**: `base_pan` / `display_pan` (+/− → dove guarda); `head_tilt` (+ = alto, − = basso); `head_roll` (+/− = quale spalla)
- [ ] **Quando quale joint**: `base_pan` vs `display_pan` vs `look_at_user` (già abbozzato — irrigidire)
- [ ] **Ampiezze tipiche**: sguardo leggero ~10–15°, giro netto ~20–30°, scan stanza ±25° (entro clamp ±45°)
- [ ] Smoke: «guarda a destra/sinistra», «solo la testa», «guarda in alto/basso», cenno sì/no
- [x] Look-around noia / micro-tick / gesti yaw → `base_pan` (non `display_pan`) — ott 2026

---

## Debito tecnico / doc (aperto)

| Item | Dettaglio |
|------|-----------|
| `ConversationViewModel` monolitico (~4k+ righe) | Estrarre coordinatori senza cambiare architettura — report: [`ARCHITECTURE_HEALTH.md`](ARCHITECTURE_HEALTH.md) |
| Dual path input bus | Doc: `SystemInputDispatcher` vs `InputRouter` — `INPUT_ARCHITECTURE.md` §3.1 |
| Guide umane | Mancano guide narrative LLM / body (memoria, proattività, umore, proxy ok) |
| `TOOL_ARCHITECTURE.md` | ~1600 righe — snellire sezioni storiche |
| Prompt corpo cinematica | Vedi sezione *Body — cinematica / prompt* sopra |
| **Barge-in TTS (ott 2026)** | Codice presente (`beginBargeIn`, `SpeechInterrupted`, `onSpeechInterrupted`, `EchoSpeechFilter`) ma **disabilitato** nel ViewModel per eco TTS→mic — [`STT_ARCHITECTURE.md`](STT_ARCHITECTURE.md). **Verificare** se conviene ripristinare (filtro eco / AEC) o **rimuovere del tutto** il percorso morto. |

- [ ] **Barge-in durante TTS — verifica o rimozione:** oggi STT pausato per tutto il turno assistente; barge-in non chiamato. Decidere: (a) tenere spento e documentato, (b) riprovare con eco più solido, (c) cancellare API/service/VM path se non conviene.

---

## Test coverage (unit)

**~115+ file di test.** Buona copertura su domain, parser, policy, memoria unificata, proattività H7, Health Connect mapper/sync.

| Area | Esempi |
|------|--------|
| Memoria RAG | `UnifiedMemoryRepositoryTest`, `LlmMemoryRecallPlannerTest`, `MemorySearchScorerTest`, `MemoryReorganizePolicyTest`, `UpsertExactMatchTest` |
| Nome utente IDENTITY | `UserNameIdentityPolicyTest`, `SaveMemoryToolTest` (conflict / system-turn block) |
| Proattività H7 | `DeviationWatcherTest`, `WellnessWatcherTest`, `WellnessContextBuilderTest`, `RecurringHabitSlotMinerTest` |
| Health Connect | `HealthConnectExerciseLabelMapperTest`, `HealthConnectExerciseSyncTest`, `WellnessContextBuilderHealthConnectTest` |
| Mood / body mapper | `MoodEngineTest`, `EmotionGestureMapperTest`, `BodyExpressionMapperTest` |
| Heartbeat legacy | `HeartbeatMicroTickPolicyTest`, `DomainSchedulerTest` |

**Lacune:** `ConversationViewModel`, E2E input drain.

Priorità test: `IdleLookAroundEligibility`, Wellness/Predictivity watchers (fake deps).

---

## Completato

### Autonomia

- **H7 — Proattività unificata (v1)** — Predittività + Wellness; settings; care domains = toggle su Wellness; ordine = fase visuale pre-score — [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md)
- **H1 — Heartbeat shell** — scheduler, micro-tick; attention domains (care + custom) → wellness
- **H2 — Confidence threshold** — `speak_confidence`; suppress heartbeat / wellness / predittività
- **H3 (core + dinamico)** — `MoodManager`, `TurnMoodEvaluator`, hotword idle, burst/ripetizione, tier happy LLM, UI occhi, coreografie corpo ESP32
- **H4 (core)** — `WorkingMemoryRepository`, dedup deviation/wellness, injection heartbeat legacy
- **H5 (core)** — `WeeklyStatsRepository`, tick `weekly_reflection` nel mood loop (senza gate mic dedicato)
- **H6** — chiuso come non-obiettivo (ott 2026): niente umore utente in proattività; `UserAwareness` rimosso; `user_tone` solo mood robot

### Presenza, corpo, attenzione

- Desk presence ML Kit — `docs/DESK_PRESENCE.md`
- Attention centering ogni turno vocale — `UserAttentionCentering` + `locateUserNow()` per predittività
- Body expression runtime — `BodyExpressionController`

### Memoria, input, voce, tool

- Unified memory RAG — `MEMORY.md`, `MEMORY_EMBEDDING.md`, `MEMORY_ACCESS.md`
- Memory review follow-up, notifiche DEFERRED, inbox unificata
- STT unificato, promemoria vocali, web, Spotify, corpo ESP32, spatial, Log Day, WhatsApp/telefono, fire-and-check
- UX: notifiche lette post-TTS, catalogo tool body, draft `UNIFIED_MEMORY_RAG_PLAN` rimosso
- **Health Connect → Log Day** — sync exercise sessions (`PHYSICAL_NOW` / `HEALTH_CONNECT`) su Wellness + session start; toggle Proattività — `ACTIVITY_LOG.md`, `PROACTIVE_ARCHITECTURE.md`
- **User display name IDENTITY** — solo auto-assegnazione formale; guardrail canale notifica/system + conflitto (`replace_user_name`); no keyword router sulla frase — `MEMORY.md`
- **No voice shortcut notifiche** — rimosso `handleNotificationVoiceCommand`; lettura unread via LLM + `NOTIFICHE_NON_LETTE` — `ROBOT_CONTEXT.md`
- **Translation mode v1** — one-shot in dialogo; continuo via `set_translation_mode` + TTS locale target — `TRANSLATION_MODE.md`

### Doc hygiene (settembre 2026)

- Rimossi riferimenti a draft già cancellati (`AgentEvolution-GapAnalysis`, `STT-Analysis`)
- Eliminato `Drafts/AnalysysClaude.md` (prompt vibe-coding storico)
- Banner su `AUTONOMOUS_AGENT_VISION.md`: non roadmap

---

## Documentazione SSOT

| Argomento | Spec |
|-----------|------|
| Catalogo funzionalità | [`FEATURES.md`](FEATURES.md) |
| Architecture health | [`ARCHITECTURE_HEALTH.md`](ARCHITECTURE_HEALTH.md) |
| Proattività | [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md) · guida [`guides/PROATTIVITA.md`](guides/PROATTIVITA.md) |
| Proxy notifiche | [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md) · guida [`guides/PROXY_NOTIFICHE.md`](guides/PROXY_NOTIFICHE.md) |
| Umore | [`MOOD.md`](MOOD.md) · guida [`guides/UMORE.md`](guides/UMORE.md) · smoke [`guides/UMORE_SMOKE.md`](guides/UMORE_SMOKE.md) |
| Heartbeat | [`HEARTBEAT_ARCHITECTURE.md`](HEARTBEAT_ARCHITECTURE.md) (micro-tick) |
| Memoria | [`MEMORY.md`](MEMORY.md), [`MEMORY_RECALL_PLANNER.md`](MEMORY_RECALL_PLANNER.md) |
| Log Day / HC | [`ACTIVITY_LOG.md`](ACTIVITY_LOG.md) |
| Traduzione | [`TRANSLATION_MODE.md`](TRANSLATION_MODE.md) |
| Indice agenti | [`AGENTS.md`](../AGENTS.md) |
