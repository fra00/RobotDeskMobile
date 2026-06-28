# Speaker Identification — Spec di design

> **Stato:** Draft — non implementato; estensione futura  
> **Data:** Giugno 2026  
> Riepilogo decisioni prese in sede di revisione architetturale.  
> Da leggere insieme a `HEARTBEAT_ARCHITECTURE.md` e `DESK_PRESENCE.md` —  
> stesso principio di confine deterministico/LLM già applicato lì.  
> **§9** raccoglie dubbi aperti e possibili miglioramenti da risolvere prima dell'implementazione.

---

## 1. Scopo (e cosa NON è)

**Scopo:** riconoscere se la voce che sta parlando corrisponde al
profilo vocale del **proprietario** dell'app/robot, o a una voce
diversa (familiare enrolled, oppure sconosciuta).

**Esplicitamente NON in scope:**
- Nessun sistema multi-utente con memorie separate per persona.
  Il robot resta concettualmente mono-utente: un'app, un
  dispositivo fisico, un proprietario, una memoria.
- Nessuna inferenza di stato emotivo/stress dal tono della voce
  (prosodia/analisi emotiva). Tecnologia troppo immatura e fragile
  per essere trattata come fatto — stesso rischio già discusso e
  scartato per le inferenze comportamentali sui domini di attenzione.
- Nessuna autenticazione di sicurezza per azioni critiche. Lo
  speaker ID qui descritto è un segnale di contesto per il LLM,
  non un cancello di sicurezza — la sola voce può essere imitata
  o il riconoscimento può sbagliare; non va usato come unico
  fattore per decisioni con conseguenze reali in futuro.

---

## 2. Principio architetturale (stesso confine già stabilito)

Identico al principio già applicato a `DeskPresenceMonitor` e a
tutto il sistema memoria/domini:

```
Tipo B (deterministico, legittimo):
  "Questa voce corrisponde al profilo embedding salvato
   come proprietario?" → calcolo di similarità vettoriale,
   nessuna interpretazione semantica.

Tipo A (va sempre al LLM, mai Kotlin):
  "Cosa significa che è [nome] a parlare in questo
   contesto? Devo trattarlo diversamente? Posso condividere
   questa informazione?" → resta sempre giudizio del LLM,
   guidato da regole nel prompt, non da branching Kotlin.
```

Kotlin produce un **metadato**, mai una decisione comportamentale.

---

## 3. Output che entra nel contesto del turno

Stesso formato già usato per `deskOccupancy`, `lastPresenceCheckAt`
e gli altri segnali Tipo B esistenti — un blocco metadati, non
un'istruzione:

```
PARLANTE: proprietario (confidence 0.91)
```
oppure
```
PARLANTE: voce enrolled — "madre" (confidence 0.84)
```
oppure
```
PARLANTE: non riconosciuto
```

Nessun'altra interpretazione viene fatta lato Kotlin. Il LLM decide
cosa farne, guidato dalle regole della sezione 5.

---

## 4. Pipeline tecnica

```
Audio in ingresso (microfono)
         │
         ├──→ Thread A: STT → LLM → esecuzione comando
         │    (lento: 2-8+ secondi su LLM locale)
         │
         └──→ Thread B: estrazione speaker embedding
              → confronto coseno con profili salvati
              (rapido: tipicamente 50-200ms)
```

**Esecuzione in parallelo**, non in sequenza — il Thread B è
ordini di grandezza più rapido del Thread A, quindi nella stragrande
maggioranza dei casi il risultato è già pronto quando serve
costruire il contesto per il LLM. Nessuna latenza percepibile
aggiunta all'interazione normale.

**Timeout obbligatorio con fallback:**
```kotlin
val speaker = withTimeoutOrNull(300) {
    speakerDeferred.await()
} ?: SpeakerMatch.unknown()
```
Lo speaker ID non deve **mai** bloccare o ritardare l'esecuzione
del comando principale. Se non è pronto o non è conclusivo entro
la finestra di timeout, si procede con `PARLANTE: non riconosciuto`
— stesso trattamento di una voce mai vista.

**Modello:** speaker embedding (es. ECAPA-TDNN o equivalente,
convertito ONNX — stesso stack ONNX Runtime già usato per
l'embedding testuale). Confronto a soglia di confidenza configurabile,
non fisso.

---

## 5. Regole comportamentali (da scrivere nel prompt/playbook, non nel codice)

### 5.1 Privacy — azioni con effetti permanenti

Se `PARLANTE` non è il proprietario (enrolled-non-proprietario o
sconosciuto):
- **Non eseguire** `save_memory`, modifiche a domini, modifiche a
  promemoria/liste, o qualsiasi azione con effetto persistente.
- Le richieste informative non sensibili (ora, meteo, domande
  generiche) restano disponibili normalmente.

### 5.2 Riservatezza verso terzi

Se `PARLANTE` non è il proprietario, il LLM non deve condividere
informazioni personali del proprietario anche se richieste
esplicitamente (es. "tuo figlio mi ha detto che è stressato" → mai).
Stesso principio di riservatezza che applicherebbe una persona
reale con le informazioni private di qualcun altro.

### 5.3 Registro comunicativo

Il tono può adattarsi a chi parla (più informale con il
proprietario, eventualmente diverso con un familiare enrolled),
ma resta governato dal LLM nel merito — Kotlin fornisce solo
l'identità, non istruzioni di tono.

### 5.4 Filtro azioni di sistema

Modifiche a impostazioni, catalogo domini, configurazione
heartbeat: **solo proprietario**. Qualsiasi altro parlante che
richieda questo tipo di azione riceve un rifiuto cortese, non
viene eseguita silenziosamente né bloccata senza spiegazione.

### 5.5 Cautela proattività sui domini sensibili

Collegamento con `DeskPresenceMonitor` (vedi `DESK_PRESENCE.md`):
se il presence gate rileva una persona ma lo speaker ID indica
che **non** è il proprietario, i domini con `sensitivity=HIGH`
(carico lavoro, contatti sociali, e simili — vedi
`HEARTBEAT_ARCHITECTURE.md` catalogo domini) devono restare in
silenzio. Un'osservazione personale destinata al proprietario non
va fatta in presenza di terzi.

---

## 6. Enrollment — solo comando esplicito del proprietario

**Decisione chiave:** nessun enrollment automatico, nessuna soglia
di interazioni che triggera una richiesta di nome. L'iniziativa di
registrare una nuova voce parte **sempre e solo** da un comando
esplicito del proprietario, in qualsiasi momento lui scelga.

### Perché non l'alternativa (enrollment dopo N interazioni)

Scartata per due motivi distinti, non solo questione di tono:

1. **Costruirebbe un sistema multi-profilo per accumulo**, senza
   che il proprietario lo abbia mai deciso — esattamente il tipo
   di scope creep che il punto 1 di questo documento esclude
   esplicitamente.
2. **Problema di consenso**: una voce sconosciuta che riceve la
   domanda "come ti chiami?" da parte del robot non sa di star
   dando consenso a essere registrata in un sistema di
   riconoscimento persistente — diverso da una presentazione
   casuale in conversazione.

### Comportamento corretto

```
Una voce non riconosciuta interagisce con il robot
(anche più volte, senza limite)
         ↓
Il robot risponde normalmente, applicando le regole
della sezione 5 (nessuna pressione, nessuna domanda
"chi sei", nessun enrollment automatico)
         ↓
SOLO se il proprietario, in qualsiasi momento, dà
il comando esplicito → enrollment
```

### Tool — `enroll_speaker`

```
Trigger vocale tipico: "Robot, ricordati questa voce,
è mia madre" / "Questa è [nome], salvala"

Vincolo: chiamabile SOLO se il PARLANTE del turno
corrente è già identificato come proprietario
(coerente con sezione 5.4 — azione di sistema)

Comportamento: estrae l'embedding dall'audio della
conversazione in corso (o richiede un breve campione
vocale aggiuntivo se l'audio disponibile è troppo
corto/rumoroso per un embedding affidabile — soglia
minima consigliata: 10-15 secondi di parlato continuo)

Salva: embedding + nome fornito dal proprietario,
nessuna interazione richiesta alla persona enrolled
```

---

## 7. Cosa resta deliberatamente fuori da questa spec

- UI di gestione profili enrolled (lista, rinomina,
  elimina) — da specificare come estensione naturale
  delle impostazioni esistenti, stesso pattern già
  usato per `AttentionDomainsSettingsScreen`.
- Comportamento in caso di più voci enrolled con
  confidence simile/ambigua tra loro — da definire
  con test reali prima di fissare una soglia di
  disambiguazione.
- Qualsiasi estensione verso autenticazione/sicurezza
  (vedi sezione 1, esplicitamente escluso).

---

## 8. Checklist di coerenza con l'architettura esistente

```
[ ] Stesso confine Tipo A / Tipo B di DESK_PRESENCE.md
[ ] Stesso pattern "metadato nel contesto, non
    decisione Kotlin" di EnvironmentFreshnessProvider
[ ] Stesso principio "azioni permanenti solo
    proprietario" già stabilito per i domini
[ ] Nessuna deriva verso sistema multi-utente
[ ] Nessuna inferenza emotiva/diagnostica dal segnale
    vocale (stesso vincolo già applicato ai domini
    HIGH sensitivity)
```

---

## 9. Dubbi aperti, rischi e miglioramenti (revisione pre-implementazione)

> Sezione aggiunta in revisione architetturale — **non vincolante** rispetto
> alle decisioni §1–8, ma da risolvere o accettare esplicitamente prima di
> scrivere codice. Nessuna implementazione pianificata finché resta draft.

### 9.1 Privacy affidata quasi tutta al prompt (§5.1, §5.4)

Le regole §5 dicono al LLM di non eseguire `save_memory`, liste, impostazioni…
ma oggi i tool vengono eseguiti se il modello li invoca (`ToolChainOrchestrator`).
A differenza di `DeskPresenceGate` (heartbeat bloccato in Kotlin), qui non c'è
ancora un gate deterministico sui tool ad alto rischio.

**Dubbio:** basta il prompt, o serve un **enforcement ibrido**?
- Metadato `PARLANTE` al LLM per tono e spiegazioni (Tipo A).
- Gate Kotlin fail-closed su tool persistenti quando `PARLANTE != proprietario`
  o `confidence < soglia` — policy su identità già calcolata, non interpretazione
  semantica (analogo a `DeskPresenceGate`).

**Da decidere prima dell'implementazione.**

### 9.2 Pipeline STT attuale senza audio grezzo esposto

Da `STT_ARCHITECTURE.md`: mic → STT → **testo** → LLM. Vosk legge PCM
internamente ma non lo espone; `SpeechRecognizer` Android spesso non fornisce
l'audio della utterance.

**Gap tecnico:** speaker embedding e `enroll_speaker` richiedono PCM (es. 16 kHz
mono). Probabile necessità di un componente nuovo, non coperto in §4:

```
SpeechCaptureBuffer (rolling 30–60 s)
  ← tap parallelo a Vosk / AudioRecord
  → embedding al finalize utterance
  → enroll_speaker legge finestra temporale o ultimi N secondi
```

**Dubbio:** speaker ID potrebbe essere **non disponibile** sul path Android STT-only;
documentare dipendenza da Vosk o tap `AudioRecord` dedicato.

### 9.3 Paradosso `enroll_speaker` (§6)

Il tool è invocabile solo se `PARLANTE = proprietario`, ma il caso d'uso tipico è:
*"ricordati questa voce, è mia madre"* — mentre ha parlato (o parla) la madre,
non il proprietario nel turno di comando.

**Dubbio:** l'enrollment deve usare la **voce target nel buffer recente**,
non l'audio del turno corrente (spesso solo il proprietario che comanda).

**Proposta da valutare:**
```
enroll_speaker(name, role=owner|other, source=last_utterance|last_30s|session)
```
- `other`: embedding dagli ultimi 20–30 s di buffer (dove parlava il familiare).
- `owner`: bootstrap al primo avvio con frase guidata (§9.5).

### 9.4 Soglia enrollment 10–15 secondi (§6)

Le utterance reali sono spesso 2–5 secondi. 10–15 s di parlato continuo è lungo
per UX naturale.

**Alternative:** accumulo multi-turno nello stesso buffer; modalità guidata
(*"fai parlare [nome] per qualche secondo"*); abbassare soglia accettando
embedding meno stabili + possibilità di re-enrollment.

### 9.5 Bootstrap profilo proprietario (mancante in §1–6)

Non è definito come si crea il **primo** profilo `proprietario`. Senza questo
la feature non parte.

**Opzioni da specificare:** wizard primo avvio; comando `enroll_speaker(role=owner)`;
enrollment implicito alla prima configurazione LLM/body. Da aggiungere a §6
quando si implementa.

### 9.6 Affidabilità su telefono e falsi segnali

| Errore | Effetto |
|--------|---------|
| Falso negativo (proprietario → `non riconosciuto`) | Frustrazione; blocchi ingiusti su memorie/impostazioni |
| Falso positivo (ospite → proprietario) | Leak informazioni personali |
| Voce alterata, distanza mic, TV/radio | Confidence instabile turno per turno |

**Miglioramenti possibili:**
- Terzo stato nel metadato: `PARLANTE: incerto — miglior match proprietario (0.54)`
  per chiedere conferma invece di binario owner/unknown.
- **Sticky speaker** per sessione (60–120 s) per ridurre flicker tra turni.
- Flusso di re-enrollment del proprietario dopo N fallimenti.

### 9.7 Timeout 300 ms vs cold start ONNX

Secondo modello ONNX oltre all'embedder testuale (~118 MB). Su cold start o
device lento, 300 ms può essere stretto → troppi turni `non riconosciuto`.

**Da calibrare** con profiling reale; considerare timeout adattivo o warm-up
all'avvio sessione vocale.

### 9.8 Fusione presenza visiva + voce (§5.5) — casi ambigui

| Presenza ML Kit | Speaker ID | Interpretazione possibile |
|-----------------|------------|---------------------------|
| PRESENT | non proprietario | Ospite alla scrivania ✓ |
| PRESENT | proprietario | Normale ✓ |
| ABSENT | non proprietario | Voce altra stanza / TV / radio? |
| PRESENT | proprietario | Proprietario fuori campo ma visibile un ospite? |

**Dubbio:** serve matrice esplicita o blocco `CONTESTO SOCIALE` nel prompt con
entrambi i segnali e nota su conflitto.

**Per heartbeat (domini HIGH):** valutare gate Kotlin deterministico
(`presence ∈ {PRESENT, UNCERTAIN}` AND `speaker != owner` → suppress),
non solo regola prompt — coerente con `DeskPresenceGate`.

### 9.9 Costo operativo e modalità disabilitazione

- Storage e download modello aggiuntivo.
- Manutenzione soglie per ambiente/microfono.
- UI profili (§7 ancora aperta).

**Proposta:** toggle impostazioni *Speaker ID disabilitato* per uso single-user
senza ospiti — zero overhead finché la feature non è matura.

### 9.10 Integrazione prevista nel codice esistente (note)

Quando si implementerà, punti di aggancio probabili:
- `buildPromptWithContext` in `ReasoningEngineImpl` — nuovo blocco metadati
  (stesso pattern di `moodContext`, `robotContext`).
- `EnvironmentFreshnessProvider` — eventuale estensione per heartbeat.
- `SttListeningOrchestrator` / `VoskSpeechToTextDataSource` — buffer PCM.
- `ToolRouter` — eventuale policy gate su tool persistenti (§9.1).

### 9.11 Priorità se/quando si implementa

1. Buffer audio + bootstrap proprietario.
2. Metadato `PARLANTE` nel contesto turno.
3. Regole prompt §5.
4. Gate Kotlin su tool ad alto rischio (se accettato in §9.1).
5. `enroll_speaker` con `source=buffer` (§9.3).
6. Fusione heartbeat HIGH + presenza (§9.8).
7. UI gestione profili (§7).
