# TODO — My Desk Robot



> **SSOT roadmap aperta.** Per visione autonoma vedi `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` (draft — può essere disallineato).  

> **Ultimo allineamento codice:** giugno 2026.



---



## Prossimo focus



### H3 — Emotional state machine (parziale → completare)

- [x] `RobotMood` + `MoodEngine` + `MoodManager` (valenza persistente ±1)

- [x] Integrazione UI occhi (`DisplayEmotionResolver`, `MoodUiStateMapper`, `BodyExpressionController`)

- [x] Coreografie corpo ESP32 (`EmotionGestureMapper`, ephemeral → gesto in dialogo, `HeadNeutralizer`, speaking micro-moves)

- [ ] Test manuali / checklist emozioni standby vs sessione attiva vs notte



### H4 — Working memory (parziale → completare)



**A cosa serve:** memoria **effimera della giornata** (non va in `memory_documents.db`). Il robot sa cosa è già successo oggi — topic discussi, quante volte ha parlato in modo proattivo, ultima interazione — e lo inietta nel contesto heartbeat. Obiettivo: **non essere ripetitivo** (“ti ho già chiesto del pranzo”) e rispettare i cap/cooldown proattivi con dati reali.



- [x] Buffer giornaliero (`WorkingMemoryRepository`, reset a mezzanotte)

- [x] Injection in heartbeat (`HeartbeatContextBuilder`: interazioni, topic, proactive speaks)

- [ ] Prevenzione ripetizioni esplicita nel prompt heartbeat (topic già discussi oggi → non ripetere)

- [ ] `recordIgnoredSuggestion` collegato al flusso utente (oggi solo parziale: timeout in `ProactiveTracker`, non ignoro esplicito dell’utente)



### H5 — Self-reflection settimanale (parziale → completare)



**A cosa serve:** ogni settimana il robot **riflette** sulle statistiche accumulate (`WeeklyStatsRepository`: proactive accettati/ignorati, topic, pattern) e può consolidare insight in memoria autonoma (`PATTERN`, `OBSERVATION`). È il pilastro **REFLECT** del loop OODA — imparare dal comportamento della settimana, non solo reagire al turno.



- [x] `WeeklyStatsRepository` + tick `weekly_reflection` (`checkAndTriggerReflection`)

- [x] Playbook + `RobotInput.WeeklyReflection` nel bus input

- [ ] Verifica end-to-end: reflection parte solo con mic attivo? (stesso gate degli altri system input)

- [ ] Consolidamento memoria post-reflection (PATTERN → azioni misurabili, non solo save LLM)



### H6 — Theory of mind (parziale → completare)

- [x] `UserAwarenessRepository` + `UserStateTracker` + injection heartbeat

- [ ] Inferenza umore utente usata nelle decisioni proattive (oltre al campo in contesto)

- [ ] Feedback loop: risposta utente dopo proactive speak → aggiorna awareness in modo strutturato



---



## Backlog prodotto



| Item | Note |

|------|------|

| Memory safety pin **Level 2** | `isPinned` in schema + comando vocale “ricordalo sempre” (L1 keyword-only: `MemorySafetyPinDetector`) |

| Tool **Note** dedicato | Oggi: `add_list_item` type `NOTE` — valutare UI/UX lista note separata |

| **News** con chunking | `fetch_url` troppo grande; serve estrazione titoli o summarization a chunk |

| **Traduttore** | Tool o catena web non ancora presente |

| Sensori ambiente | Input `sensor_reading` (futuro) |

| **Domotica** | Termostato/luci con conferma utente |

| Pulsante hardware ESP32 | `hardware_button` BLOCKING (futuro) |

| **Speaker ID** | Draft `docs/Drafts/SPEAKER_IDENTIFICATION.md` (§9 dubbi aperti) |

| Sessione vocale auto post-notifica | `activateVoiceSession()` esiste ma **non** è collegato al TTS notifica — vedi `INPUT_ARCHITECTURE.md` §1.1 |



**Rimosso dal backlog** (già coperto):

- ~~Lista spesa~~ → `add_list_item` `SHOPPING` + `list_items` / `update_list_item`

- ~~Integrazione corpo ESP32~~ → `move_body_joint`, `body_home`, `body_status`, prompt condizionale



---



## UX / qualità / debito tecnico



| Item | Stato | Dettaglio |

|------|--------|-----------|

| Notifiche lette dopo TTS (modalità normale) | ✅ Fatto | `markEpisodeRead` dopo TTS riuscito; silent mode resta in inbox |

| Catalogo tool body allineato al prompt | ✅ Fatto | `BODY_UNAVAILABLE_SECTION` se corpo non configurato; save su test connessione OK |

| Notifiche con mic spento | ✅ By design | Input droppati (`canAcceptInput`); nessuna coda su disco — accettato per ora |

| `ConversationViewModel` monolitico (~3.4k righe) | ⬜ Debito | Estrarre coordinatori (notifiche, speech, settings) senza cambiare architettura |

| Dual path input bus | ⬜ Doc | `SystemInputDispatcher` usato da notifiche/heartbeat/reminder; `InputRouter` registrato ma heartbeat non passa da lì — vedi `INPUT_ARCHITECTURE.md` §3.1 |

| Guide umane incomplete | ⬜ Doc | Solo memoria in `docs/guides/`; mancano heartbeat, LLM, body, smoke test |

| `TOOL_ARCHITECTURE.md` troppo lungo | ⬜ Doc | ~1600 righe; spezzare o snellire sezioni storiche |

| Draft obsoleti in `docs/Drafts/` | ⬜ Doc | `UNIFIED_MEMORY_RAG_PLAN`, `AgentEvolution-GapAnalysis`, `STT-Analysis` — non usare come SSOT |

| Allineamento `AGENTS.md` / spec | ✅ Fatto | Giugno 2026 — tabella H1–H6, test coverage, feature recenti |



---



## Test coverage (unit, `app/src/test`)



**~110 file di test** (giugno 2026). Buona copertura su **domain**, **parser**, **policy**, **memoria unificata**, **tool locali**.



### Ben coperto

| Area | Esempi test |

|------|-------------|

| Memoria RAG / unified | `UnifiedMemoryRepositoryTest`, `MemorySearchScorerTest`, `MemoryProjectionGuardTest`, `LlmMemoryRecallPlannerTest` |

| Mood / emozioni UI | `MoodEngineTest`, `MoodUiStateMapperTest`, `EyeExpressionMapperTest`, `EphemeralExpressionTest` |

| Body ESP32 (mapper) | `EmotionGestureMapperTest`, `BodyExpressionMapperTest`, `HeadNeutralizerTest`, `SpeakingMicroMovesTest` |

| Heartbeat (parziale) | `DomainSchedulerTest`, `HeartbeatCriticParserTest`, `HeartbeatInputSourceTest`, `HeartbeatMicroTickPolicyTest` |

| Presenza / centering planner | `DeskPresenceGateTest`, `PresenceFusionPolicyTest`, `AttentionCenteringPlannerTest` |

| Input policy / coda | `InputPolicyEngineTest`, `DeferredInputQueueTest` |

| STT / speech domain | `SttListeningOrchestratorTest`, `EchoSpeechFilterTest`, `WakePhraseMatcherTest` |

| Spatial / activity | `PlaceMatcherTest`, `SavePlaceToolTest`, `ActivityLogRepositoryTest` |



### Lacune (moduli runtime critici senza test dedicati)

| Modulo | Note |

|--------|------|

| `ConversationViewModel` | Hub orchestrazione — nessun test |

| `HeartbeatOrchestrator` | Tick end-to-end — nessun test |

| `HeartbeatContextBuilder` | Assembly contesto — nessun test |

| `ProactiveGatePolicy` / `ProactiveTracker` | Gate proattività — nessun test |

| `ReasoningEngineImpl` / `ToolChainOrchestrator` | Solo `LlmResponseParserTest`, `ChainSpeechPolicyTest` |

| `DeskPresenceMonitor` | Solo `DeskPresenceGateTest` (policy, non ML Kit) |

| `UserAttentionCentering` | Closed-loop su ogni turno vocale; scan simmetrico solo senza volto a inizio turno — test `UserAttentionCenteringTest` |

| `BodyExpressionController` | Solo mapper/gesture |

| `MoodManager` | Solo `MoodEngineTest` |

| `WorkingMemoryRepository` | Solo modello `WorkingMemoryTest` |

| `WeeklyStatsRepository` | Solo `WeeklyStatsTest` (domain) |

| `UserAwarenessRepository` | Solo tracker/state |

| `NotificationInputSource` / listener | Parziale (`UnannouncedNotificationTest`, inbox mapper) |



Priorità test futuri: `ProactiveGatePolicy`, `HeartbeatOrchestrator` (con fake deps), `InputPolicyEngine`+deferred drain E2E.



---



## Completato



### Autonomia

- ~~**H1 — Heartbeat base**~~ — scheduler, orchestrator, playbook, domini attenzione (6 built-in + custom), critic pass HIGH, micro-tick senza LLM

- ~~**H2 — Confidence threshold**~~ — `speak_confidence` in JSON; soglia heartbeat configurabile; suppress heartbeat/TTS intermedio



### Presenza, corpo, attenzione (giugno 2026)

- ~~Desk presence ML Kit~~ — `DeskPresenceMonitor`, gate heartbeat, settings UI — `docs/DESK_PRESENCE.md`

- ~~Attention centering~~ — ogni turno vocale (sessione attiva), scan simmetrico se no face a inizio turno, hold pose se volto perso — `UserAttentionCentering`

- ~~Body expression runtime~~ — `BodyExpressionController`, mood/ephemeral/speaking/micro-tick, `BodyHardwareBusyGate`



### Memoria e input

- ~~Unified memory RAG (Fase 0–2 + read/write unified-first)~~ — `memory_documents.db`, hybrid recall, `UnifiedMemoryFactory`

- ~~Memory review follow-up (2.1 / 2.2 / 2.3)~~ — projection guard, weekly reconcile, recall budget, safety pin L1 — `docs/MEMORY_REVIEW_FOLLOWUP.md`

- ~~Memory hygiene~~ — `MemoryRecallCueResolver`; `UserMemoryRepository` solo migrazione + test

- ~~Tool notifiche~~ — `NotificationListener`, policy DEFERRED, inbox unificata (promemoria + episodi unread + deferred)

- ~~Marcatura notifica letta dopo annuncio vocale~~ — giugno 2026, modalità NORMAL



### Voce, LLM, tool

- ~~Memoria utente~~ — estrazione automatica + `save/list/delete_memory`

- ~~Promemoria vocali~~ — `set_reminder` + `ScheduledTaskFired`

- ~~Data/ora nel prompt~~ — `{{CURRENT_DATETIME}}`

- ~~Contesto robot / silenzio notifiche~~ — `set_robot_context`, `docs/ROBOT_CONTEXT.md`

- ~~Web~~ — `web_search` + `fetch_url`

- ~~Musica~~ — `play_spotify`

- ~~Corpo ESP32 (myDeskBody)~~ — tool HARDWARE + `body_capabilities_prompt.txt` — `docs/BODY_INTEGRATION.md`

- ~~Memoria spaziale~~ — `save_place`, `match_place`, DOVE SONO — `docs/SPATIAL_MEMORY.md`

- ~~Log Day / activity log~~ — `log_daily_activity`, estrazione LLM — `docs/ACTIVITY_LOG.md`

- ~~Fire-and-check~~ — `set_reminder` + verifica — `docs/FIRE_AND_CHECK.md`

- ~~WhatsApp / telefono~~ — `resolve_whatsapp_target`, `send_whatsapp`, `dial_phone`

- ~~STT unificato Android/Vosk~~ — `docs/STT_ARCHITECTURE.md`



---



## Documentazione di riferimento



| Argomento | Spec |

|-----------|------|

| Visione autonomo (draft) | `docs/Drafts/AUTONOMOUS_AGENT_VISION.md` |

| Gap analysis (obsoleto) | `docs/Drafts/AgentEvolution-GapAnalysis.md` |

| Indice agenti | `AGENTS.md` |

