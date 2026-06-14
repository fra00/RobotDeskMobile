# My Desk Robot — Autonomous Agent Vision

> **Scopo:** Documento di riferimento per l'evoluzione verso un agente autonomo.  
> **Stato:** Draft v1  
> **Data:** Maggio 2026

---

## 1. La tesi

Un assistente vocale che **aspetta sempre l'utente** non è un compagno. È un tool.

My Desk Robot vuole essere qualcosa di diverso: un'entità che **condivide la scrivania**, osserva, ricorda, e ogni tanto — con giudizio — prende l'iniziativa.

La differenza non è il modello linguistico (tutti ne hanno uno buono). È **come lo usi**:
- Loop continuo, non solo risposta a trigger
- Memoria persistente, non sessione usa-e-getta
- Corpo fisico con espressioni, non voce disincarnata
- Silenzio intelligente come output valido, non come fallimento

---

## 2. Cosa ci differenzia (competitive edge)

| Elemento | Alexa/Siri/Gemini | My Desk Robot |
|----------|-------------------|---------------|
| Trigger | Utente parla | Utente + tempo + eventi |
| Memoria | Cloud, limitata, opaca | Locale, persistente, trasparente |
| Corpo | Nessuno | Occhi, emozioni, presenza |
| Privacy | Dati su server | LLM locale opzionale |
| Proattività | Push notification | Ragionamento situazionale |
| Silenzio | Bug / "non ho capito" | Decisione valida |

---

## 3. Architettura cognitiva: OODA Loop

```
OBSERVE   → Heartbeat tick + sensori + notifiche + memoria
     ↓
ORIENT    → HeartbeatContextBuilder assembla il quadro situazionale
     ↓
DECIDE    → LLM + RuleBasedPrefilter → parla / agisce / tace
     ↓
ACT       → TTS + tool + occhi + silenzio
     ↓
FEEDBACK  → Utente risponde / ignora → aggiorna working memory
     ↓
REFLECT   → Self-reflection periodica → aggiorna long-term memory
```

Questo loop è il "battito cardiaco" dell'agente. Senza di esso, è solo un chatbot.

---

## 4. I sei pilastri

### 4.1 Heartbeat (tick autonomo)

Timer periodico che alimenta il ReasoningEngine anche senza input esterni.

- **Intervallo:** configurabile, default 10 minuti
- **Priorità:** DEFERRED (non interrompe conversazioni)
- **Gate:** mic attivo, non notte, non in call/meeting
- **Output:** silenzio (80–95%), oppure suggerimento calibrato

### 4.2 Confidence Threshold

Il LLM misura quanto è sicuro che valga la pena parlare.

```json
{
  "reply": "Sono le sei, di solito porti fuori Rex.",
  "speak_confidence": 0.82,
  "reasoning": "ROUTINE cane + lunedì 18:05 + standby 40min",
  "emotion": "happy",
  "action": {"type": "none"}
}
```

- Sotto soglia (default 0.75) → silenzio automatico
- Soglia configurabile dall'utente
- Log per tuning

### 4.3 Emotional State Machine

Stato emotivo autonomo che evolve indipendentemente dal dialogo.

```kotlin
data class RobotMood(
    val valence: Float,             // -0.4…+0.85 persistent wellbeing
    val baseline: Float,            // personal drift target (~+0.1)
    val baseEmotion: RobotEmotion,  // derived standby eyes/body
    val intensity: Float,           // 0.0–1.0
    val since: Long,
    val reason: MoodReason?,
    val recentDeltas: List<MoodDelta>,
)
```

**Due livelli:** valenza (solo eventi codificati: poke, elogio, insulto, task, idle) vs `emotion` LLM (effimera, TTL).

**Regole di transizione:**

| Condizione | Transizione |
|------------|-------------|
| Standby > 30 min | NEUTRAL → BORED (0.3) |
| Standby > 90 min | BORED → DROWSY (0.5) |
| Reminder urgente < 15 min | → ANXIOUS (0.6) |
| Interazione positiva recente | → HAPPY (0.4), decay 20 min |
| Poke occhi ripetuti | → CONFUSED / ANGRY (`EYE_POKE`), decay ~8 min |
| Scusa utente dopo poke | Riduce fastidio (non happy immediato) |
| Notte | → SLEEPING (1.0) |

**SSOT:** `MoodManager` è l’unico writer di `RobotMood`. Occhi, heartbeat e ogni turno vocale leggono lo stesso stato (`STATO ROBOT` nel prompt). Il campo `emotion` del JSON LLM è solo espressione effimera durante il TTS, non sovrascrive il mood persistente.

Gli occhi mostrano il mood anche in silenzio. Il robot **sembra vivo**.

### 4.4 Working Memory

Buffer volatile di "cosa è successo oggi":

```kotlin
data class WorkingMemory(
    val todayInteractions: Int,
    val lastUserMood: String?,
    val topicsDiscussedToday: List<String>,
    val proactiveSpeaksToday: Int,
    val ignoredSuggestionsToday: Int,
    val lastProactiveSpeak: Long?,
)
```

- Iniettata nel prompt heartbeat
- Reset a mezzanotte o al primo "buongiorno"
- Previene ripetizioni ("già parlato del meteo oggi")

### 4.5 Self-Reflection

Ragionamento periodico (settimanale) sul proprio comportamento:

```
[SYSTEM_INPUT: weekly_reflection]
Questa settimana:
- Interventi proattivi: 47
- Risposte positive: 31
- Ignorati/rifiutati: 8
- Topic utili: reminder cane, meteo mattina
- Topic ignorati: suggerimenti serie TV
```

Il LLM analizza e aggiorna la memoria:
- Abbassa importance dei topic ignorati
- Salva: "suggerimenti serie TV = bassa gradimento"

**Apprendimento senza ML** — il LLM ragiona sul proprio comportamento.

### 4.6 Theory of Mind (leggera)

Tracciare non solo "cosa so dell'utente" ma "cosa l'utente probabilmente sa/sente":

- L'utente probabilmente sa già del meteo (guardato stamattina)
- L'utente probabilmente non sa della mail urgente
- L'utente sembra stressato oggi (tono interazioni)

Fase avanzata. Richiede i pilastri precedenti.

---

## 5. Scala di azione

Ogni intervento proattivo ha un "livello di invasività":

```
0. Silenzio                    (default ~80–95% dei tick)
1. Solo occhi / emozione       (curiosità, noia)
2. Suggerimento vocale breve   ("Vuoi uscire con Rex?")
3. Tool informativo            (get_weather, web_search)
4. Tool che modifica mondo     (set_reminder, set_volume)
5. Azione senza chiedere       (solo whitelist esplicita)
```

Salire di livello richiede più contesto e più fiducia.

---

## 6. Regole non negoziabili

1. **DEFERRED** — mai interrompere TTS/conversazione
2. **Notte** — soppresso (scheduled task esclusi)
3. **Robot context SILENT** — heartbeat non parla
4. **Cooldown** — non parlare se hai già parlato negli ultimi N min
5. **Cap orario** — max 2–3 interventi vocali spontanei / ora
6. **Mic off = off** — niente tick verso LLM

---

## 7. Roadmap di implementazione

### Fase H1 — Heartbeat base ✅
- [x] `RobotInput.Heartbeat` + `HeartbeatInputSource`
- [x] `HeartbeatScheduler` (AlarmManager)
- [x] `HeartbeatContextBuilder` (payload minimo)
- [x] Prompt HEARTBEAT nel system prompt
- [x] Settings: on/off, intervallo, fascia oraria

### Fase H2 — Confidence threshold ✅
- [x] `speak_confidence` nel JSON response
- [x] `LlmResponseParser` esteso
- [x] Soglia configurabile in Settings
- [x] Log "heartbeat suppressed"

### Fase H3 — Emotional state machine ✅
- [x] `RobotMood` data class
- [x] `MoodEngine` con regole di transizione
- [x] Integrazione con UI occhi
- [x] Mood influenza tono risposta LLM

### Fase H4 — Working memory ✅
- [x] `WorkingMemory` data class
- [x] Injection nel prompt heartbeat
- [x] Reset giornaliero
- [x] Tracciamento topic discussi

### Fase H5 — Self-reflection ✅
- [x] `SYSTEM_INPUT: weekly_reflection`
- [x] Scheduler settimanale (integrato nel monitor)
- [x] LLM analizza e aggiorna memoria

### Fase H6 — Theory of mind
- [x] Tracciamento "user awareness state"
- [x] Inferenza umore utente
- [x] Calibrazione risposte

---

## 8. Criteri di successo

### H1+H2 (MVP proattività)
- [ ] Il robot parla da solo < 3 volte/ora in condizioni normali
- [ ] > 70% degli interventi spontanei sono percepiti come utili (test utente)
- [ ] Il silenzio è percepito come "il robot sta pensando", non come bug

### H3 (presenza emotiva)
- [ ] Gli occhi mostrano emozioni coerenti anche in silenzio
- [ ] L'utente percepisce il robot come "presente" sulla scrivania

### H4–H6 (maturità)
- [ ] Il robot non ripete le stesse cose
- [ ] Impara quali suggerimenti funzionano
- [ ] Calibra il tono in base all'umore percepito

---

## 9. Rischi e mitigazioni

| Rischio | Gravità | Mitigazione |
|---------|---------|-------------|
| Parla troppo | Alta | Confidence threshold + cap orario |
| Parla cose ovvie | Media | Working memory + teoria della mente |
| Complessità cresce | Alta | Incrementale, una fase alla volta |
| LLM locale non basta | Media | Testare presto, fallback Gemini |
| Utenti non lo vogliono | Media | Proattività opt-in, default conservativo |

---

## 10. Filosofia guida

> "Un collega sulla scrivania che ogni tanto alza lo sguardo — non un radioamatore."

Il successo non è "quante volte parla" ma "quante volte ha ragione a parlare".

Il silenzio intelligente è il feature principale.

---

## 11. File correlati

| File | Ruolo |
|------|-------|
| `docs/INPUT_ARCHITECTURE.md` | Bus input esistente |
| `docs/MEMORY.md` | Long-term memory |
| `docs/ROBOT_CONTEXT.md` | Profili e silenzi |
| `docs/ROBOT_EXPRESSIONS.md` | Emozioni occhi |
| `docs/SCHEDULED_TASKS.md` | Reminder (stesso pattern) |
| `docs/Drafts/AgentEvolution-GapAnalysis.md` | Gap analysis vs draft Claude |

---

*Ultimo aggiornamento: maggio 2026*
