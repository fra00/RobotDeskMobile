# TODO — My Desk Robot

## Documentazione

- Visione agente autonomo → `docs/Drafts/AUTONOMOUS_AGENT_VISION.md`
- Gap analysis vs draft Claude → `docs/Drafts/AgentEvolution-GapAnalysis.md`

---

## Prossimo: Autonomous Agent (fasi H1–H6)

### H1 — Heartbeat base ⬅️ IN CORSO
- [ ] `RobotInput.Heartbeat` + `HeartbeatInputSource`
- [ ] `HeartbeatScheduler` (AlarmManager)
- [ ] `HeartbeatContextBuilder` (payload minimo)
- [ ] Prompt HEARTBEAT nel system prompt
- [ ] Settings: on/off, intervallo, fascia oraria

### H2 — Confidence threshold
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

- Tool per prender appunti (Note)
- Tool per lista spesa
- News (fetch contenuto troppo grande — serve chunking o estrazione titoli)
- Traduttore
- Sensori temperatura / ambiente (fase 3+)
- Tool domotica (termostato, luci — con conferma)

---

## Completato

- ~~Tool per leggere sito e riassumere~~ (`web_search` + `fetch_url`)
- ~~Tool per ascoltare la musica~~ (`play_spotify`)
- ~~Contesto robot / silenzio notifiche~~ (`set_robot_context`)
- ~~Tool per leggere notifiche~~ (NotificationListener)
- ~~Memoria utente~~ (estrazione automatica + tool `save/list/delete_memory`)
- ~~Promemoria vocali~~ (`set_reminder` + `ScheduledTaskFired`)
- ~~Data/ora nel prompt~~ (`{{CURRENT_DATETIME}}`)
