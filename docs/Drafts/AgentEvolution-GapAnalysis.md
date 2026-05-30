# Evoluzione agente — Gap analysis (vs AnalysysClaude.md)

> **Scopo:** punto fisso per dibattito e pianificazione.  
> **Riferimento:** `docs/Drafts/AnalysysClaude.md` (roadmap Claude, non aggiornata allo stato repo).  
> **Ultimo allineamento codice:** maggio 2026.

---

## 1. Cosa propone il draft Claude

| Blocco | Contenuto |
|--------|-----------|
| Input | Notifiche, **heartbeat**, **reminder scaduti** (`ReminderFired`), sensori |
| Memoria | Room + injection prompt + tool `save_memory` / `list` / `delete` |
| Reminder | `set` / `get` / `delete` + annuncio **vocale** via dispatcher |
| Tool quotidiani | Meteo (Open-Meteo), note, lista spesa, web |
| Visione | `detect_presence`, `analyze_environment` |
| Prompt | Comportamento autonomo, HEARTBEAT, memoria auto-save |

**Priorità nel draft:** memoria → reminder → heartbeat → meteo → note → visione → spesa → web.

---

## 2. Già implementato (stato repo)

| Area | Stato | Note vs draft |
|------|--------|----------------|
| Wake word + STT + TTS | ✅ | — |
| LLM (LM Studio / Gemini) + JSON strutturato | ✅ | `reply`, `emotion`, `action`, tool chain |
| Occhi / emozioni | ✅ **oltre draft** | `wink`, `loving`, `sleeping`, `drowsy`; `docs/ROBOT_EXPRESSIONS.md` |
| Visione | ✅ | `take_photo` (non tool presence dedicati) |
| Notifiche → `SystemInputDispatcher` | ✅ | `docs/INPUT_ARCHITECTURE.md` |
| Policy + coda differita | ✅ | `InputPolicyEngine`, `DeferredInputQueue` |
| Contesto robot | ✅ | `set_robot_context`, `docs/ROBOT_CONTEXT.md` |
| Tool chain | ✅ | `ToolChainOrchestrator`, `docs/TOOL_ARCHITECTURE.md` |
| Web | ✅ **oltre draft** | `web_search` (SearXNG + fallback DDG HTML) + `fetch_url` |
| Meteo | ✅ **variante** | OpenWeatherMap + API key (non Open-Meteo) |
| Reminder | ⚠️ parziale | Solo `set_reminder`; scadenza = **notifica Android** |
| Memoria | ⚠️ parziale | Room + `MemoryExtractionService` + injection; **no** tool LLM save/list/delete |
| Data/ora prompt | ✅ | `{{CURRENT_DATETIME}}` |
| Ragionamento agente (prompt) | ✅ **oltre draft** | `AUTONOMOUS PROBLEM SOLVING`, `docs/AGENT_REASONING.md` |
| Altri tool | ✅ | `play_spotify`, `open_browser`, `set_volume`, `show_notification` |

`RobotInput` attuali: `Notification`, `HardwareButton` (stub), `SensorReading` (stub).  
**Mancano:** `Heartbeat`, `ReminderFired`.

---

## 3. Non implementato (o molto diverso)

| Voce draft | Gap |
|------------|-----|
| Heartbeat | Nessun scheduler / `InputSource` / prompt HEARTBEAT |
| Tool memoria LLM | Nessun `save_memory` / `list_memories` / `delete_memory` |
| Reminder vocali | `ReminderAlarmReceiver` → notifica, non LLM → TTS |
| `get_reminders` / `delete_reminder` | Assenti |
| Note | Aperto in `docs/TODO.md` |
| Lista spesa | Assente |
| `detect_presence` / `analyze_environment` | Assenti (solo `take_photo`) |
| Open-Meteo | Non usato (OWM sì) |
| Prompt COMPORTAMENTO AUTONOMO (system input) | Parziale (notifiche sì; heartbeat no) |
| Planner Kotlin separato | Solo policy testuale; nessun `TaskPlanner` |

---

## 4. Condivisione con il draft (cosa tenere)

1. **Due canali:** voce utente + `SYSTEM_INPUT` — architettura già avviata.
2. **Proattività:** heartbeat + reminder che **parlano** = salto da assistente reattivo ad agente da scrivania.
3. **Tool diretto vs catena:** allineato a `AGENT_REASONING.md` (`get_weather` prima; `web_search` → `fetch_url` per il resto).
4. **Reminder → dispatcher → LLM** migliore della sola notifica di sistema.
5. **Heartbeat:** DEFERRED, gate notturno, rispetto `set_robot_context`.
6. **Room + AlarmManager** per dati strutturati e timer.

---

## 5. Dove adattare / dissentire

| Proposta draft | Posizione repo |
|----------------|----------------|
| DuckDuckGo Instant Answer | Superato: SearXNG + fallback; self-host per stabilità |
| Open-Meteo obbligatorio | OWM ok; Open-Meteo opzionale |
| Solo tool per memoria | Tenere **estrazione automatica** + aggiungere tool per controllo utente |
| Tool visione separati | Fase 1: intent su `take_photo`; tool dedicati solo se serve JSON rigido |
| Room ovunque | Room per CRUD; DataStore per settings resta ok |
| Lista tool statica nel prompt | Evolvere verso elenco da `ToolRouter` / definitions |
| Planner LLM subito | Prima planner rule-based (Fase D), poi eventuale LLM plan-only |

---

## 6. Roadmap proposta (evoluzione agente)

### Fase A — Cervello allineato (basso effort)

- [ ] Documento allineato (questo file + aggiornare `TODO.md` quando si chiudono voci)
- [ ] Prompt unificato `SYSTEM_INPUT` (quando agire / `reply: ""` / notte / profilo robot)
- [ ] (Opz.) Elenco `emotion` / tool definitions generato o verificato vs codice

### Fase B — Proattività vocale (alto impatto)

- [ ] `ReminderFired` → `SystemInputDispatcher` → LLM → TTS
- [ ] `get_reminders`, `delete_reminder`
- [ ] `Heartbeat` + `HeartbeatInputSource` + scheduler + prompt HEARTBEAT

### Fase C — Memoria e quotidiano

- [ ] Tool memoria LLM (o esporre `forget` già in repo)
- [ ] Note (Room + tool)
- [ ] Città meteo default in Settings

### Fase D — Planner leggero

- [ ] `RuleBasedPlanner` in `reasoning/` (test JVM): meteo, ricerca, fallback
- [ ] Replan su `TOOL_RESULT` errore

### Fase E — Dopo B–D

- [ ] Lista spesa
- [ ] Visione con intent su `take_photo`
- [ ] News (spesso basta web + prompt fonti)
- [ ] Open-Meteo (solo se si abbandona OWM)

**Non in cima:** spesa prima di heartbeat/reminder vocali; planner LLM doppio su ogni frase.

---

## 7. Mappa impatto

```text
[Reattivo]  voce → LLM → tool → TTS     ████████████░░
[Input]     notifiche → dispatcher       ██████████░░░░
[Memoria]   Room + inject + extract      ████████░░░░░░
[Proattivo] tick + promemoria vocale     ██░░░░░░░░░░░░
[Planner]   regole / replan              █░░░░░░░░░░░░░
[Utility]   note, spesa, news            ███░░░░░░░░░░░
```

---

## 8. Decisioni aperte (da dibattere)

1. Reminder: solo voce robot, o anche notifica Android?
2. Heartbeat: intervallo default (5 min draft) e gate ore (7–23)?
3. Memoria: priorità tool LLM vs solo estrazione automatica?
4. Meteo: restare OWM o migrare Open-Meteo?
5. Planner: Fase D subito dopo B o rimandare?
6. Visione: nuovi tool vs prompt su `take_photo`?

---

## 9. File correlati

| File | Ruolo |
|------|--------|
| `docs/Drafts/AnalysysClaude.md` | Roadmap originale Claude (vibe coding) |
| `docs/AGENT_REASONING.md` | Policy tool diretto vs catena |
| `docs/INPUT_ARCHITECTURE.md` | Bus input esterni |
| `docs/ROBOT_CONTEXT.md` | Profili e DROP notifiche |
| `docs/WEB_SEARCH.md` | SearXNG + fetch |
| `docs/ROBOT_EXPRESSIONS.md` | Campo `emotion` |
| `docs/TODO.md` | Backlog breve |

---

*Aggiornare questo file quando si chiudono fasi o si cambiano decisioni §8.*
