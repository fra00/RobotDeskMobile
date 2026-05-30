# Analisi STT — bug “testo visibile ma no LLM” e latenza ~6s

> Analisi codice (maggio 2026). File chiave: `SttListeningOrchestrator`, `VoskSpeechToTextDataSource`, `ConversationViewModel`, `HotwordListeningService`.

---

## Implementato (STT unificato)

Vedi **`docs/STT_ARCHITECTURE.md`**.

| Cambiamento | Dettaglio |
|-------------|-----------|
| `ListeningConfig` | `endOfUtteranceMs` (~1800) + `segmentSilenceMs` (~990) |
| `SttListeningOrchestrator` | `lastContentAt` solo su transcript; `tryFinalizePhrase()` dopo listen + poll; niente secondo listen con buffer pieno |
| Provider | `segmentSilenceMs` iniettato (Android intent + Vosk segment end) |
| `ConversationViewModel` | `clearCurrentUtteranceDisplay()` su early return + log eco/coda |
| Test JVM | `FakeSpeechToTextDataSource`, `SttListeningOrchestratorTest` |

---

## Pipeline precedente (sessione attiva) — pre-fix

```text
Vosk listenWithChunks (1 ciclo ≈ fino a silenzio 1,5s + timeout)
  → append buffer + UtteranceInProgress (UI: currentUtterance "… testo")
  → delay 400ms (RESTART_DELAY_MS)
  → loop: finalizePhraseIfPaused() se silenzio ≥ utterance_pause (2s)
  → UtteranceReadyForLlm
  → ConversationViewModel.onUtteranceReadyForLlm
  → sendPhraseToLlm → Thinking + beginAssistantTurn (STT in pausa)
```

**Importante:** il testo in UI con prefisso `…` è `currentUtterance`, **non** ancora `conversationLog`. La riga “Tu:” appare solo in `sendPhraseToLlm`.

---

## Problema 1 — Testo visibile ma LLM non parte; ripetere sovrascrive

### Cause probabili (ordinate per probabilità)

#### A) Confusione UI: frase “in costruzione” vs cronologia

- `ConversationUiState.displayText` concatena log + `… currentUtterance`.
- L’utente vede il testo e crede che sia già “inviato”; in realtà si attende ancora `finalizePhraseIfPaused` + `onUtteranceReadyForLlm`.

#### B) `finalizePhraseIfPaused` ritardato o bloccato dal loop

Dopo ogni `listenWithChunks`:

1. `lastSpeechAt` aggiornato.
2. `delay(400ms)`.
3. All’iterazione successiva serve `now - lastSpeechAt >= utterance_pause_ms` (**2000ms** da `utterance_pause_seconds`).

Se il controllo fallisce, parte **un nuovo** `listenWithChunks` (Vosk può restare in ascolto altri **1,5s** di silenzio parziale, o fino a **15s** timeout se non rileva speech).

I **partial Vosk** nel `ChunkListener` aggiornano `lastSpeechAt` → **resetano** il timer dei 2s anche senza nuova frase utente (rumore/riverbero).

**Effetto:** pausa lunga o “bloccato in ascolto” con testo fermo su `… frase`.

#### C) Frase scartata dopo finalize (eco) — “sovrascrittura”

In `SttListeningOrchestrator.finalizePhraseIfPaused`:

- `phraseBuffer.clear()` **prima** di `UtteranceReadyForLlm`.

In `ConversationViewModel.onUtteranceReadyForLlm`:

- `EchoSpeechFilter` può fare `return` senza `sendPhraseToLlm`.
- `currentUtterance` **non viene azzerato** su early return.

L’utente vede ancora la frase scartata; al turno STT successivo il buffer è **vuoto** → solo le nuove parole → **sembra sovrascrittura**.

#### D) Coda LLM non svuotata

Se `isAssistantTurnInProgress()` (Thinking / Speaking / CapturingImage):

- `queueUtteranceForLlm(phrase)` — **non** aggiorna subito il log.
- `drainQueuedUtterance()` solo a fine turno assistente.

Se fine turno non arriva (job LLM appeso, TTS bloccato) → frase persa o molto ritardata.

#### E) Race `beginAssistantTurn` / `clearPendingPhrase`

`beginAssistantTurn()` chiama `orchestrator.clearPendingPhrase()` e `sttPaused = true`.

Se coincide con fine frase utente, il buffer orchestrator può essere svuotato mentre la UI mostra ancora `currentUtterance`.

#### F) `onUtteranceInProgress` ignorato durante turno assistente

Con `isAssistantTurnInProgress()`, gli aggiornamenti STT in UI sono saltati; possibile disallineamento percepito tra orecchio e schermo.

---

## Problema 2 — ~6 secondi fine parlato → “Sto pensando”

### Budget latenza (somma tipica con Vosk)

| Fase | Durata | Dove |
|------|--------|------|
| Fine parlato Vosk (no partial nuovi) | **1,5s** | `END_OF_SPEECH_SILENCE_MS` in `VoskSpeechToTextDataSource` |
| Restart orchestrator | **0,4s** | `RESTART_DELAY_MS` |
| Fine frase (silenzio dopo contenuto) | **~1,8s** | `utterance_pause_seconds` → `ListeningConfig.endOfUtteranceMs` |
| Segmento provider | **~1s** | `ListeningConfig.segmentSilenceMs` |
| Secondo ciclo listen (spesso) | **0,4–1,5s** | finalize solo a inizio loop; spesso serve 2° iterazione |
| **Totale tipico** | **~4–6s** | Coerente con osservazione |

Nota: il commento in `ConversationUiState` parla di “5s di pausa” ma `strings.xml` ha **2s** — solo documentazione UI obsoleta.

### Android STT vs Vosk

Con **Android SpeechRecognizer** i tempi cambiano (end-of-speech di sistema, niente `END_OF_SPEECH_SILENCE_MS` interno). Confrontare log con provider attivo in Impostazioni STT.

---

## Cosa concordo (diagnosi)

1. Due timer in serie (Vosk **1,5s** + app **2s**) spiegano gran parte dei 6s.
2. Il loop “ascolta → 400ms → controlla pausa → riascolta” può ritardare o impedire `finalize`.
3. Il bug “sovrascrittura” è spesso **buffer svuotato + UI non aggiornata + nuovo chunk**, non vera append persa.
4. Eco TTS può far **sparire** la frase per il LLM pur visibile a schermo.

---

## Direzioni di fix (priorità suggerita)

### Quick wins (config)

- [ ] Ridurre `utterance_pause_seconds` (es. 2 → **1**) se non taglia frasi a metà.
- [ ] Ridurre `END_OF_SPEECH_SILENCE_MS` Vosk (es. 1500 → **800–1000**).
- [ ] Allineare commento UI “5s” → valore reale.

### Fix architetturali (consigliati)

1. **Timer unificato end-of-utterance**  
   Un solo clock “utente ha finito” (max(Vosk EOS, utterance_pause) o solo uno dei due), non due in serie.

2. **Finalize subito dopo `listenWithChunks` success**  
   Chiamare `finalizePhraseIfPaused()` anche subito dopo transcript, non solo a inizio loop successivo (evita 2° listen inutile).

3. **Non resettare `lastSpeechAt` su partial rumorosi**  
   Opzione: partial non estendono la pausa 2s; solo final / transcript incrementano.

4. **Su early return in `onUtteranceReadyForLlm`**  
   `currentUtterance = ""` + messaggio debug/log; opzionale riga log “(non inviato: eco)”.

5. **Eco filter**  
   Log quando scarta; in sessione attiva post-TTS considerare cooldown più lungo prima di accettare STT.

6. **Coda utterance**  
   Se `queueUtteranceForLlm`, mostrare in UI “in coda”; timeout safety drain.

7. **Metriche**  
   Log timestamp: fine audio, `UtteranceReadyForLlm`, `sendPhraseToLlm`, `Thinking` — per validare fix.

---

## Verifica manuale (Logcat)

Filtro: `SttOrchestrator`, `VoskSttDataSource`, `ConversationVM`

1. Ultimo `Partial` / `Final result` Vosk.
2. `UtteranceReadyForLlm` compare?
3. `sendPhraseToLlm` compare subito dopo?
4. Gap temporale tra `Final result on speech end` e `sendPhraseToLlm`.
5. Messaggi `Echo` / early return senza send.

---

## Decisioni aperte

1. Target latenza accettabile? (es. **≤ 2,5s** fine parlato → Thinking)
2. Provider principale: Vosk o Android?
3. Accettare taglio frase se si abbassa `utterance_pause`?
4. Priorità fix STT **prima** di Fase B agente (heartbeat/reminder vocali)?

---

*Riferimenti storici: `utterance_pause_seconds=2`, Vosk `END_OF_SPEECH_SILENCE_MS=1500` (rimosso; ora `segmentSilenceMs` da config).*
