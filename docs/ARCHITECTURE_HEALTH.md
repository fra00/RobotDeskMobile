# Architecture health report

> Snapshot qualità / struttura repo (ottobre 2026).  
> Non sostituisce [`TODO.md`](TODO.md) come roadmap; serve da riferimento per debito strutturale.  
> Rivalutare dopo split ViewModel o cambi ampi di layer.

---

## Verdetto

Architettura a 3 layer **sostanzialmente solida** (`domain` / `reasoning` spesso puri, memoria e policy ben testate), ma il rischio operativo è concentrato: **`ConversationViewModel` (~4250 LOC)** come hub di voce, settings, memoria, proattività, presenza. Non è spaghetti diffuso: **periferia ok, centro monolitico**.

---

## Package / layer

Sotto `com.example.mydeskrobot/`:

| Package | Ruolo |
|---------|--------|
| `ui/` | Compose |
| `presentation/` | ViewModel / UiState |
| `domain/` | Kotlin puro (target) |
| `data/` | Repository / Android I/O |
| `reasoning/` | Layer 2 — tool chain, LLM client API |
| `integration/` | Layer 3 — tool, presence, wellness, proxy |
| `memory/` | Dominio verticale SSOT documenti |

### Violazioni di layer (localizzate)

| Direzione | Esempio | Nota |
|-----------|---------|------|
| domain → data | `MoodManager` → `MoodRepository` concreto + `Log` | Porta/interfaccia in domain mancante |
| domain → data + integration | `ProactiveSpeakGate` | Tipi settings e policy fuori da domain |
| ui → data | Mode Bot, HC, settings dialogs | Bypass presentation |
| presentation → data/integration | `ConversationViewModel` | Composition root eccessivo |
| reasoning → Android | `LlmResponseParser` | Solo `Log` (minore) |

Wiring `presentation`→`data` senza Hilt è tipico; qui è eccessivo per volume nel VM.

---

## God class / file pesanti (LOC approx)

| LOC | File |
|----:|------|
| **~4250** | `presentation/conversation/ConversationViewModel.kt` |
| ~1631 | `memory/unified/UnifiedMemoryRepository.kt` |
| ~871 | `ui/screen/RobotScreen.kt` |
| ~574 | `ui/components/IdleDistractionOverlay.kt` |
| ~479 | `reasoning/ToolChainOrchestrator.kt` |
| ~400+ | STT orchestrator, presence, Mode Bot dialog, overlay/settings UI |

- **VM** = unico vero god object (debito già in [`TODO.md`](TODO.md)).
- `UnifiedMemoryRepository` / `ToolChainOrchestrator` = pezzi grossi di dominio, più giustificabili.

---

## Antipattern

| Issue | Dove / nota |
|-------|-------------|
| `runBlocking` sparso | VM, Factory, MainActivity, `ReminderAlarmReceiver`, notification listener, HTTP proxy/capability, `ReasoningModule`, … |
| Path paralleli input | `SystemInputDispatcher` vs `InputRouter` — [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md) §3.1 |
| Memoria legacy | `UserMemoryRepository` ancora presente accanto a unified |
| ui→data diretto | Dialoghi settings / proxy / Health Connect |
| Object globali | Bus input, gate proattivi (utili ma accoppianti) |

---

## Forma copertura test

- **~155** file `*Test*` vs **~518** `.kt` main (ordini di grandezza).
- **Forte:** memoria RAG, mood engine, proattività H7, proxy capability, policy domain, HC mapper/sync.
- **Debole:** **zero test su `ConversationViewModel`**; poco E2E voce / input drain (come da TODO).

---

## Punti di forza

- Separazione reasoning / integration / presentation reale e documentata (`AGENTS.md`, `TOOL_ARCHITECTURE.md`).
- Domain in gran parte testabile e testato.
- Memoria unificata con writer / projection guard.
- Debito strutturale **esplicito** in TODO (non nascosto).
- Feature verticali (proxy, HC, wellness) spesso con test sui pezzi puri.

---

## Priorità se si ripulisce (non roadmap prodotto)

1. Spezzare `ConversationViewModel` in coordinatori (settings, memory lifecycle, system-input drain, voice turn) senza cambiare prodotto.
2. Porte domain pure: Mood repository / tipi usati da speak gate fuori da `data`/`integration`.
3. Ridurre `runBlocking` sui path caldi (init, notifiche, reminder).
4. Unificare ingresso input (un solo bus + drain).
5. Smoke test VM dopo lo split (fake STT/LLM/TTS).

Feature prodotto aperte (Predictivity smoke, fire-and-check, open thread, domotica, …) → [`TODO.md`](TODO.md).

---

## Collegamenti

| Doc | Ruolo |
|------|--------|
| [`TODO.md`](TODO.md) | Roadmap + debito VM |
| [`FEATURES.md`](FEATURES.md) | Catalogo funzionalità |
| [`TOOL_ARCHITECTURE.md`](TOOL_ARCHITECTURE.md) | Design tool / layer |
| [`INPUT_ARCHITECTURE.md`](INPUT_ARCHITECTURE.md) | Dual path input |
| [`AGENTS.md`](../AGENTS.md) | Indice agenti |
