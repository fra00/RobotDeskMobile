# TODO — My Desk Robot

## Documentazione

- Visione agente autonomo → `docs/Drafts/AUTONOMOUS_AGENT_VISION.md`
- Gap analysis vs draft Claude → `docs/Drafts/AgentEvolution-GapAnalysis.md`

---

## Prossimo: Autonomous Agent (fasi H2–H6)

### H2 — Confidence threshold ⬅️ IN CORSO
- [ ] `speak_confidence` nel JSON response
- [ ] Soglia configurabile
- [ ] Log "heartbeat suppressed"

### H3 — Emotional state machine
- [ ] `RobotMood` + regole transizione
- [ ] Integrazione UI occhi
- [ ] Mood autonomo (non solo da LLM)

### H4 — Working memory
- [ ] Buffer "cosa è successo oggi"
- [ ] Reset giornaliero
- [ ] Prevenzione ripetizioni

### H5 — Self-reflection
- [ ] Ragionamento settimanale
- [ ] Aggiornamento memoria da feedback

### H6 — Theory of mind
- [ ] Tracciamento awareness utente
- [ ] Inferenza umore

---

## Backlog (dopo H1–H2)

- Memory safety pin Level 2 (`isPinned` schema + comando vocale “ricordalo sempre”)
- Tool per prender appunti (Note)
- Tool per lista spesa
- News (fetch contenuto troppo grande — serve chunking o estrazione titoli)
- Traduttore
- Sensori temperatura / ambiente (fase 3+)
- Tool domotica (termostato, luci — con conferma)

---

## Completato

- ~~Unified memory RAG (Fase 0–2 + read-path + consolidation v2 + write-path unified-first + legacy mirror removed + factory singleton)~~ — `memory_documents.db`, hybrid recall, `UnifiedMemoryFactory` process-scoped
- ~~Memory review follow-up (2.1 / 2.2 / 2.3)~~ — projection guard + weekly reconcile, recall budget + week/month cues, safety pin Level 1 — see `docs/MEMORY_REVIEW_FOLLOWUP.md`
- ~~Memory hygiene~~ — `MemoryIntentDetector` rimosso (sostituito da `MemoryRecallCueResolver`); `UserMemoryRepository` resta solo per migrazione one-shot + test
- ~~H1 — Heartbeat base~~ — `HeartbeatInputSource`, `HeartbeatScheduler`, `HeartbeatContextBuilder`, `heartbeat_playbook_prompt.txt`, settings UI
- ~~Tool per leggere sito e riassumere~~ (`web_search` + `fetch_url`)
- ~~Tool per ascoltare la musica~~ (`play_spotify`)
- ~~Contesto robot / silenzio notifiche~~ (`set_robot_context`)
- ~~Tool per leggere notifiche~~ (NotificationListener)
- ~~Memoria utente~~ (estrazione automatica + tool `save/list/delete_memory`)
- ~~Promemoria vocali~~ (`set_reminder` + `ScheduledTaskFired`)
- ~~Data/ora nel prompt~~ (`{{CURRENT_DATETIME}}`)
