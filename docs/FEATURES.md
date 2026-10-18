# My Desk Robot — Catalogo funzionalità (attuale)

> Inventario di ciò che il prodotto **espone oggi**.  
> Roadmap / aperto → [`TODO.md`](TODO.md).  
> Tabella tool LLM (nomi wire) → [`AGENTS.md`](../AGENTS.md) § *Tool disponibili* (SSOT catalogo tool).

**Ultimo aggiornamento:** ottobre 2026.

---

## 1. Dialogo e voce

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| STT (Android / Vosk) | ✅ | [`STT_ARCHITECTURE.md`](STT_ARCHITECTURE.md) |
| LLM (LM Studio / Gemini) | ✅ | [`LLM_SETTINGS.md`](LLM_SETTINGS.md) |
| TTS risposta | ✅ | — |
| Catene tool JSON | ✅ | [`TOOL_ARCHITECTURE.md`](TOOL_ARCHITECTURE.md), [`AGENT_REASONING.md`](AGENT_REASONING.md) |
| Modalità traduzione one-shot + continua | ✅ | [`TRANSLATION_MODE.md`](TRANSLATION_MODE.md) · tool `set_translation_mode` |

---

## 2. UI e presenza

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| Occhi Compose + emotion LLM | ✅ | [`ROBOT_EXPRESSIONS.md`](ROBOT_EXPRESSIONS.md) |
| Umore persistente + espressione effimera | ✅ | [`MOOD.md`](MOOD.md) · guida [`guides/UMORE.md`](guides/UMORE.md) |
| Desk presence ML Kit + attention centering | ✅ | [`DESK_PRESENCE.md`](DESK_PRESENCE.md) |
| Idle look-around | ✅ | [`HEARTBEAT_ARCHITECTURE.md`](HEARTBEAT_ARCHITECTURE.md) |
| Modalità lampada (`make_light`) | ✅ | tool LOCAL |
| Contesto robot lavoro/call/SILENT | ✅ | [`ROBOT_CONTEXT.md`](ROBOT_CONTEXT.md) · `set_robot_context` |

---

## 3. Memoria e Log Day

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| Memoria unificata RAG + recall planner LLM | ✅ | [`MEMORY.md`](MEMORY.md), [`MEMORY_ACCESS.md`](MEMORY_ACCESS.md), [`MEMORY_RECALL_PLANNER.md`](MEMORY_RECALL_PLANNER.md) |
| Embedding ONNX hybrid | ✅ | [`MEMORY_EMBEDDING.md`](MEMORY_EMBEDDING.md) |
| Tool memoria / liste | ✅ | `save_memory`, `list_memories`, `delete_memory`, `add_list_item`, … |
| Log Day 7 giorni + `log_daily_activity` | ✅ | [`ACTIVITY_LOG.md`](ACTIVITY_LOG.md) |
| Health Connect → Log Day (locale e/o cell via bridge) | ✅ | [`ACTIVITY_LOG.md`](ACTIVITY_LOG.md), [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) |
| Memoria spaziale (stanze / landmark) | ✅ | [`SPATIAL_MEMORY.md`](SPATIAL_MEMORY.md) · tool `*_place` |

---

## 4. Proattività

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| Predictivity (habit slots + deviation) | ✅ v1 | [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md) |
| Wellness (domini + ordine stanza) | ✅ **v2** (snapshot SSOT + notifica se attenzione; no TTS proattivo; UI riepilogo) | [`PROACTIVE_ARCHITECTURE.md`](PROACTIVE_ARCHITECTURE.md) · guida [`guides/PROATTIVITA.md`](guides/PROATTIVITA.md) |
| Gate invasività / cooldown / cap giornaliero | ✅ | `ProactiveSpeakGate` |
| Weekly reflection (tick + stats) | ✅ core | H5 consolidamento strutturato → [`TODO.md`](TODO.md) |
| Umore utente in decisioni proattive | ❌ non obiettivo | H6 chiuso |

---

## 5. Promemoria e impegni

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| `set_reminder` / `get_reminders` / `delete_reminder` | ✅ | [`SCHEDULED_TASKS.md`](SCHEDULED_TASKS.md) |
| Fire-and-check fisico | ✅ | [`FIRE_AND_CHECK.md`](FIRE_AND_CHECK.md) |
| Commitment follow-up (`Verifica impegno`) | ✅ | idem |
| Dual reminder → notifica anche sul cell (opt-in Mode Bot) | ✅ | [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) §6 |

---

## 6. Input esterni

| Input | Stato | Spec |
|-------|-------|------|
| Notifiche (listener locale o proxy) | ✅ | [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md), [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md) |
| `scheduled_task` a scadenza | ✅ | [`SCHEDULED_TASKS.md`](SCHEDULED_TASKS.md) |
| Pulsante hardware ESP32 | ❌ futuro | — |
| Sensori ambiente | ❌ futuro | — |

---

## 7. Proxy telefono (stessa app, ruoli ROBOT / PROXY)

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| Forward notifiche PROXY → ROBOT | ✅ | [`NOTIFICATION_PROXY.md`](NOTIFICATION_PROXY.md) · guida [`guides/PROXY_NOTIFICHE.md`](guides/PROXY_NOTIFICHE.md) |
| Pull `exercises` (Health Connect sul cell) | ✅ | [`PHONE_CAPABILITY_BRIDGE.md`](PHONE_CAPABILITY_BRIDGE.md) |
| Pull `contacts` (rubrica cell → resolve telefono/WhatsApp) | ✅ | idem |
| Push `local_notification` (dual reminder) | ✅ | idem |
| Controllo dialer / WhatsApp / Spotify **sul cell** | ❌ fuori scope | azioni restano sul device ROBOT |

---

## 8. Tool LLM per area

Catalogo nominale completo: [`AGENTS.md`](../AGENTS.md) § *Tool disponibili*.

| Area | Tool (esempi) |
|------|----------------|
| Visione / presenza | `take_photo`, `detect_presence`, `analyze_room_scene` |
| Spazio | `match_place`, `save_place`, `list_places`, `set_current_place` |
| Web / meteo | `web_search`, `fetch_url`, `get_weather`, `open_browser` |
| Telefono / WhatsApp / Spotify | `resolve_phone_contact`, `dial_phone`, `resolve_whatsapp_target`, `send_whatsapp`, `play_spotify` |
| Contesto / traduzione | `set_robot_context`, `set_translation_mode` |
| Promemoria | `set_reminder`, `get_reminders`, `delete_reminder` |
| Memoria / attività / liste | `save_memory`, `log_daily_activity`, `list_memories`, `delete_memory`, `add_list_item`, `list_items`, `update_list_item`, `delete_list_item` |
| Device UI | `set_volume`, `make_light`, `show_notification` |
| Corpo ESP32 | `move_body_joint`, `move_body_joints`, `body_home`, `body_status`, `look_at_user` |

Tool body presenti nel catalogo LLM **solo** se corpo abilitato + URL configurato.

---

## 9. Corpo fisico (ESP32 myDeskBody)

| Funzionalità | Stato | Spec |
|--------------|-------|------|
| HTTP REST joints / home / status | ✅ | [`BODY_INTEGRATION.md`](BODY_INTEGRATION.md) |
| Prompt capability dinamico | ✅ | `body_capabilities_prompt.txt` |
| Coreografie da `emotion` / mood (Kotlin) | ✅ | [`MOOD.md`](MOOD.md), `BODY_INTEGRATION.md` |
| Cinematica POV/segni L-R/su-giù nel prompt | ⬜ da approfondire | [`TODO.md`](TODO.md) § *Body — cinematica* |

---

## 10. Cosa non è esposto (scelta / backlog)

| Item | Note |
|------|------|
| Speaker identification | Draft |
| Skill editor Blockly | Draft |
| News summarization ONNX | Backlog |
| Domotica / sensori / pulsante ESP32 BLOCKING | Backlog |
| Theory of mind (umore utente → proattività) | Non obiettivo |

Dettaglio aperto: sempre [`TODO.md`](TODO.md).

---

## Manutenzione

Quando aggiungi una capability utente-visibile o un tool LLM:

1. Aggiorna la tabella tool in `AGENTS.md` (se tool nuovo).
2. Aggiorna **questa pagina** (riga area + link spec).
3. Spec di dominio in `docs/` (+ guida in `docs/guides/` se serve).
