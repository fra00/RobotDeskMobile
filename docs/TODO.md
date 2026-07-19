# TODO — My Desk Robot

> **SSOT roadmap aperta.** Visione autonoma (draft, può essere disallineata): `docs/Drafts/AUTONOMOUS_AGENT_VISION.md`  
> **Ultimo allineamento codice:** giugno 2026.

---

## Prossimo focus

### H3 — Emotional state machine

- [x] Umore dinamico H3: `TurnMoodEvaluator`, hotword idle, burst/ripetizione, tier happy LLM, ephemeral neutral
- [ ] Smoke manuale: [`guides/UMORE_SMOKE.md`](guides/UMORE_SMOKE.md) (standby vs sessione attiva vs notte, occhi + corpo ESP32)

### H4 — Working memory

- [ ] `recordIgnoredSuggestion` su ignoro esplicito utente (oggi: timeout in `ProactiveTracker` + stats settimanali)
- ~~Iniettare topic anti-ripetizione nel wellness~~ — non necessario (check 1×/giorno; dominio disattivabile in UI)

### H5 — Self-reflection settimanale

- [ ] Consolidamento memoria post-reflection (PATTERN/OBSERVATION misurabili, non solo save LLM generico)

### H6 — Theory of mind

- [ ] Umore utente nelle decisioni proattive: da riprogettare LLM-driven (luglio 2026: rimosso `UserAwarenessState` keyword-based; il tono utente ora arriva dal LLM via `user_tone`, solo per il mood robot)
- [ ] Feedback loop strutturato dopo proactive speak (accettato / ignorato / rifiutato)

### Follow-up proattività (post-H7 v1)

- [x] `wellnessPresentEnough` cablato (body first → fallback interazione; idle buffer separato)
- [x] Wellness done solo dopo esito; micro-tick switch separato da wellness/predittività
- [ ] Smoke manuale: [`guides/PROATTIVITA_SMOKE.md`](guides/PROATTIVITA_SMOKE.md)

---

## Backlog prodotto

| Item | Note |
|------|------|
| Memory pin + Riorganizza | ✅ `isPinned`, exact dedup, LLM consolidation auto/manuale (gate configurabile); ⬜ fragmentation analyzer |
| Tool **Note** dedicato | Oggi: `add_list_item` type `NOTE` |
| **News** summarization | Chunking in `fetch_url` ok; summarization ONNX opzionale |
| **Traduttore** | Tool o catena web |
| Sensori ambiente | Input `sensor_reading` |
| **Domotica** | Termostato/luci con conferma utente |
| Pulsante hardware ESP32 | `hardware_button` BLOCKING |
| **Speaker ID** | Draft `docs/Drafts/SPEAKER_IDENTIFICATION.md` |
| Sessione vocale auto post-notifica | `activateVoiceSession()` non collegato al TTS notifica — `INPUT_ARCHITECTURE.md` §1.1 |

---

## Debito tecnico / doc (aperto)

| Item | Dettaglio |
|------|-----------|
| `ConversationViewModel` monolitico (~3.9k righe) | Estrarre coordinatori senza cambiare architettura |
| Dual path input bus | Doc: `SystemInputDispatcher` vs `InputRouter` — `INPUT_ARCHITECTURE.md` §3.1 |
| Guide umane | Mancano guide narrative LLM / body (memoria, proattività, umore ok) |
| `TOOL_ARCHITECTURE.md` | ~1600 righe — snellire sezioni storiche |
| Draft obsoleti | Eliminare `AgentEvolution-GapAnalysis.md`, `STT-Analysis.md` (fix in `STT_ARCHITECTURE.md`) |

---

## Test coverage (unit)

**~115 file di test.** Buona copertura su domain, parser, policy, memoria unificata, proattività H7.

| Area | Esempi |
|------|--------|
| Memoria RAG | `UnifiedMemoryRepositoryTest`, `LlmMemoryRecallPlannerTest`, `MemorySearchScorerTest`, `MemoryReorganizePolicyTest`, `UpsertExactMatchTest` |
| Proattività H7 | `DeviationWatcherTest`, `WellnessWatcherTest`, `WellnessContextBuilderTest`, `RecurringHabitSlotMinerTest` |
| Mood / body mapper | `MoodEngineTest`, `EmotionGestureMapperTest`, `BodyExpressionMapperTest` |
| Heartbeat legacy | `HeartbeatMicroTickPolicyTest`, `DomainSchedulerTest` |

**Lacune:** `ConversationViewModel`, `HeartbeatOrchestrator`, `ProactiveGatePolicy`, E2E input drain.

Priorità test: `ProactiveGatePolicy`, `HeartbeatOrchestrator` (fake deps).

---

## Completato

### Autonomia

- **H7 — Proattività unificata (v1)** — Predittività + Wellness; settings; care domains = toggle su Wellness; ordine = fase visuale pre-score — [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md)
- **H1 — Heartbeat shell** — scheduler, micro-tick; attention domains (care + custom) → wellness
- **H2 — Confidence threshold** — `speak_confidence`; suppress heartbeat / wellness / predittività
- **H3 (core + dinamico)** — `MoodManager`, `TurnMoodEvaluator`, hotword idle, burst/ripetizione, tier happy LLM, UI occhi, coreografie corpo ESP32
- **H4 (core)** — `WorkingMemoryRepository`, dedup deviation/wellness, injection heartbeat legacy
- **H5 (core)** — `WeeklyStatsRepository`, tick `weekly_reflection` nel mood loop (senza gate mic dedicato)
- **H6 (core)** — rimosso `UserAwarenessRepository` (keyword-based, luglio 2026); tono utente LLM-driven via `user_tone`

### Presenza, corpo, attenzione

- Desk presence ML Kit — `docs/DESK_PRESENCE.md`
- Attention centering ogni turno vocale — `UserAttentionCentering` + `locateUserNow()` per predittività
- Body expression runtime — `BodyExpressionController`

### Memoria, input, voce, tool

- Unified memory RAG — `MEMORY.md`, `MEMORY_EMBEDDING.md`, `MEMORY_ACCESS.md`
- Memory review follow-up, notifiche DEFERRED, inbox unificata
- STT unificato, promemoria vocali, web, Spotify, corpo ESP32, spatial, Log Day, WhatsApp/telefono, fire-and-check
- UX: notifiche lette post-TTS, catalogo tool body, draft `UNIFIED_MEMORY_RAG_PLAN` rimosso

---

## Documentazione SSOT

| Argomento | Spec |
|-----------|------|
| Proattività | [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md) · guida [`guides/PROATTIVITA.md`](guides/PROATTIVITA.md) |
| Umore | [`MOOD.md`](MOOD.md) · guida [`guides/UMORE.md`](guides/UMORE.md) · smoke [`guides/UMORE_SMOKE.md`](guides/UMORE_SMOKE.md) |
| Heartbeat | [`HEARTBEAT_ARCHITECTURE.md`](HEARTBEAT_ARCHITECTURE.md) (micro-tick) |
| Memoria | [`MEMORY.md`](MEMORY.md), [`MEMORY_RECALL_PLANNER.md`](MEMORY_RECALL_PLANNER.md) |
| Indice agenti | [`AGENTS.md`](../AGENTS.md) |