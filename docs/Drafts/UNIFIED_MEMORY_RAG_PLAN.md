# Unified Memory RAG — Piano di lavoro (DRAFT)

> Proposta architetturale per My Desk Robot. Versione **0.3-draft** — non implementata.  
> Destinato a review tecnica (LLM / team) e implementazione incrementale.  
> Review esterna v0.1 + v0.2 incorporate + **filosofia cognitiva**.

**Progetto:** My Desk Robot  
**Data:** 2025-06-19 (agg. v0.3)  
**Stato:** Draft — **GO** (review v0.2); pronto per Fase 0 benchmark  

---

## 1. Executive summary

My Desk Robot non punta a un assistente con regole fisse, ma a un **agente cognitivo**: la memoria deve comportarsi come quella umana — **un accesso**, **ricordi consolidati**, **ciò che conta emerge**, **il rumore si scarta**.

Oggi la macromemoria è **frammentata** (più DB, più provider, retrieval a token). L'obiettivo è un **Unified Memory RAG**: un unico punto di accesso che recupera da tipi diversi (`kind` + metadata), con **consolidation** (anti-fragmentazione) e **retrieval semantico** (miglior rapporto domanda↔memoria), senza iniettare memoria irrilevante.

**Non obiettivo:** sostituire il discernimento del LLM con regole rigide; il codice implementa **pattern** che massimizzano il risultato, il LLM fa il ragionamento fine (incluso ignorare memorie assurde o fuori contesto).

**North star (6 principi → pattern tecnici):**

| # | Principio cognitivo | Pattern implementativo |
|---|---------------------|------------------------|
| 1 | Ciò che usi spesso resta; ciò che conta è difficile da dimenticare | `useCount`, `lastUsedAt`, `confidence`, pruning che **non** elimina prima i ricordi forti/usati |
| 2 | Un solo accesso mentale, molte fonti | `UnifiedMemoryRepository.searchRelevant()` — un API, molti `kind` |
| 3 | Memoria ottimizzata, non frammentata | `MemoryConsolidationService` (resta) + canonical docs + re-embed |
| 4 | Miglior rapporto qualità domanda↔memoria | Embedding + `minScore` + filtri metadata |
| 5 | Non recuperare/iniettare se non inerente | Soglia pertinenza + **blocco vuoto** se nessun hit; no injection “sempre tutto” |
| 6 | Il cervello può considerare un ricordo assurdo e ignorarlo | Retrieval porta candidati; **LLM dialogo** scarta il non pertinente; prompt: non usare tutto ciò che vedi |

**Decisione SSOT (chiusa in v0.2):** l'indice unified è **source of truth** per read/write; migrazione batch da legacy in standby — **no dual-write prolungato**.

---

## 1.1 Architettura cognitiva — pattern, non regole

Il sistema non deve essere un catalogo di IF/THEN per ogni domanda. Deve replicare **pattern** che nella mente umana funzionano bene:

```text
                    ┌─────────────────────────┐
                    │   UNICO ACCESSO MEMORIA  │  ← principio 2
                    │   (recall / consolidate) │
                    └────────────┬────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
   CONSOLIDATE              RECALL                   FORGET
   (ottimizza,              (domanda →               (topic +
    anti-fragm.)              candidati)               pruning)
         │                       │
         │                       ├─ filtro kind/metadata (strutturato)
         │                       ├─ semantic + minScore (concettuale)
         │                       └─ 0 risultati → silenzio (principio 5)
         │
         └─ LLM curatore periodico (già esistente)

                    ┌─────────────────────────┐
                    │   LLM COGNITIVO (dialogo) │  ← principi 5–6
                    │   usa ciò che serve;       │
                    │   ignora assurdo/rumore    │
                    └─────────────────────────┘
```

### Recall a strati (chi decide RAG sì/no)

Non un router LLM dedicato ogni turno — **pattern a costo crescente**:

| Strato | Pattern | Quando |
|--------|---------|--------|
| **A — Struttura** | Query diretta su `kind` + metadata (data, stato attivo) | Promemoria, liste, PLAN “oggi”, spatial |
| **B — Rilevanza** | Semantic search + `minScore` + hint `MemoryIntentDetector` | Domande su fatti utente, episodi puntuali |
| **C — Catalogo** | Elenco per `useCount` / `updatedAt` senza query | Vision entity, “cosa conosco di te” ampio |
| **D — LLM tool** | `list_memories` / `get_macromemory` in catena | Solo se injection insufficiente |

**Principio 5:** se lo strato B non supera `minScore` → **nessuna memoria iniettata** (es. “chi era Garibaldi?”). Il LLM risponde da conoscenza generale senza rumore.

**Principio 6:** se qualcosa passa la soglia ma è semanticamente assurdo per la domanda (es. “verde d’invidia” su “cosa ho comprato di verde”), il **LLM lo ignora** — il retrieval non deve pretendere di fare tutto.

### Consolidation = sonno / riorganizzazione mentale

La **consolidation LLM periodica** (già in codebase) **non si sostituisce** al RAG — è il meccanismo che implementa il principio 3:

- Input: N frammenti sulla stessa topic
- Output: poche righe canoniche dense
- Poi: retrieval semantico trova quelle righe con qualsiasi paraphrase

Sequenza corretta: **consolidate → recall → LLM discernimento**.

### EPISODE: doppio livello (principio 3 sui log)

| Livello | Uso | Query tipo |
|---------|-----|------------|
| Episodio singolo | “Quando mi ha scritto Marco?” | RAG + filtro actor/channel |
| `HABIT_SUMMARY` aggregato | “Cosa ho fatto questa settimana?” | Doc derivato (già parzialmente `ActivityHabitProfile`) |

Episodi raw non devono saturare il recall su domande generiche temporali.

---

## 2. Stato attuale (AS-IS)

### 2.1 Store dati separati

| Store | Repository | Contenuto tipico | Persistenza |
|-------|------------|------------------|-------------|
| User memory | `UserMemoryRepository` | IDENTITY, PREFERENCE, ROUTINE, FACT, OBSERVATION, INTENT, PATTERN | Room `user_memory.db` |
| Log Day | `ActivityLogRepository` | PHYSICAL_NOW, PLAN, SOCIAL_THREAD, COMMITMENT, TENTATIVE/CONFIRMED | Room `activity_log.db` |
| Promemoria | `ScheduledTaskRepository` | Task vocali/notifiche a scadenza | Room |
| Liste | `ListItemRepository` | TODO, NOTE, SHOPPING | Room |
| Spatial | spatial DAO | Stanze, landmark, posizione corrente | Room |

### 2.2 Injection nel prompt (per turno vocale)

Assemblate in `ReasoningEngineImpl.buildPromptWithContext()`:

| Provider | Trigger / condizione | Cosa inietta |
|----------|----------------------|--------------|
| `MemoryPromptContextProviderImpl` | Profilo intent + query | Max 8–20 righe user memory |
| `DayContextPromptProviderImpl` | `MemoryIntentDetector` include PLAN | Reminders + todo + note + EPISODI PROSSIMI |
| `ActivityContextProviderImpl` | Sempre (voice) | PROFILO ABITUDINI + ATTIVITÀ RECENTI (physical) |
| `SpatialContextProvider` | SPATIAL / vision | Stanza corrente, luoghi |
| Heartbeat | `HeartbeatContextBuilder` | INTENT, OBSERVATION, PATTERN |

### 2.3 Retrieval user memory oggi

- **Matcher:** `MemoryTopicMatcher` — token overlap + substring, soglia `MIN_RANK_SCORE = 0.25`
- **Cap injection:** 8–14 righe per profilo in `MemoryPromptContextProviderImpl`
- **Cap store:** prune a ~300 righe user-facing (`pruneIfNeeded`)
- **Dedup leggero:** `reorganize()` + `MemoryDuplicateDetector`
- **Compaction LLM:** `MemoryConsolidationService` — merge globale user-facing, backup, hash skip se unchanged
- **Intent routing:** `MemoryIntentDetector` (keyword italiane, zero LLM)

### 2.4 Write path attuali

| Canale | Servizio / tool | Output |
|--------|-----------------|--------|
| Estrazione standby | `MemoryExtractionService` | Upsert Room user memory |
| Estrazione episodi | `ActivityExtractionService` | Upsert Log Day |
| Tool espliciti | `save_memory`, `log_daily_activity`, `set_reminder`, `add_list_item` | Rispettivi store |
| Consolidation | `MemoryConsolidationService` | Replace user-facing con righe canoniche |

---

## 3. Problemi misurati (motivazione)

### P1 — Retrieval semantico insufficiente

Query: *"quando lavoro il venerdì?"*  
Memoria DB: *"orario mattutino fine settimana"*  
→ **miss** (zero token condivisi).

### P2 — Cap arbitrario per turno

Con 40 ROUTINE in DB, injection porta 2–6 righe per profilo. Il LLM risponde incompleto **perché non vede i dati**, non perché non li capisce.

### P3 — Frammentazione

Estrazione + dialogo producono varianti testuali dello stesso fatto (es. 7 righe orari lavoro). `reorganize()` rimuove quasi-duplicati testuali; `MemoryConsolidationService` merge via LLM — ma il retrieval token-based non recupera bene i frammenti residui.

Esempio reale in DB:

```text
"il venerdì lavori dalle 9 del mattino fino all'una"
"dal lunedì al giovedì lavori sia mattina che pomeriggio"
"Lavora il pomeriggio dal lunedì al venerdì"
"Il venerdì lavora dall 9:00 alle 13:00"
"l'utente lavora di solito fino alle 13:00"
"lun-gio dalle 14:00 alle 18:00"
"lun-ven dalle 9:00 alle 13:00"
```

Risposta attuale: parziale. Risposta attesa: *"lun-gio 9:00–13:00 e 14:00–18:00; venerdì solo 9:00–13:00."*

### P4 — Fragmentazione architetturale

Per rispondere a domande composite (*"cosa devo fare oggi?"*, *"quando mi ha scritto Marco?"*) servono **N query a N repository** con logiche diverse. Difficile estendere e testare.

### P5 — Forget / search / resolver

`forgetByTopic`, `PhoneContactResolver`, `WhatsAppTargetResolver` usano lo stesso matcher token — stessi limiti semantici.

---

## 4. Visione target (TO-BE)

### 4.1 Concetto: Unified Memory Document

Ogni elemento memorizzabile diventa un **documento** nell'indice RAG:

```kotlin
data class MemoryDocument(
    val id: Long,
    val value: String,              // testo canonico per LLM + embedding
    val kind: MemoryDocumentKind,   // tipo macromemoria
    val category: String?,          // sotto-tipo (es. ROUTINE, SOCIAL_THREAD)
    val source: MemorySource,       // extractor, tool, consolidation, system
    val embedding: FloatArray?,     // 384-dim, nullable se modello assente
    val confidence: Float,
    val useCount: Int,
    val lastUsedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?,           // null = permanente
    val isActive: Boolean,
    // Metadata temporali / relazionali
    val dayKey: String?,            // giorno evento o log
    val scheduledDayKey: String?,
    val scheduledAtMs: Long?,
    val actor: String?,             // contatto, es. WhatsApp
    val sourceChannel: String?,
    val episodeConfidence: EpisodeConfidence?, // TENTATIVE | CONFIRMED
    val externalRef: String?,       // link a entità originale (reminderId, listItemId, …)
)
```

### 4.2 Kind enum (v1 proposta)

```kotlin
enum class MemoryDocumentKind {
    USER_FACT,          // ex save_memory / extraction: IDENTITY, PREFERENCE, ROUTINE, FACT
    EPISODE,            // ex Log Day: physical, plan, social, commitment
    REMINDER,           // promemoria attivi
    LIST_ITEM,          // todo, note, spesa
    SPATIAL,            // stanza / luogo
    AUTONOMY,           // OBSERVATION, INTENT, PATTERN (heartbeat)
    HABIT_SUMMARY,      // riga derivata aggregata (opzionale, read-only)
}
```

### 4.3 Flusso retrieval unificato

```text
Input: userText + TurnHints (intent detector, heartbeat, system input)
         ↓
Opzionale: MemoryIntentDetector → filtri metadata (kind, dayKey, …)
         ↓
TextEmbedder.embed(query)  ~30ms on-device
         ↓
UnifiedMemoryIndex.search(
    queryVector,
    limit = 15–20,
    filters = activeFilters,
    minScore = threshold,
)
         ↓
Fallback: token rank (MemoryTopicMatcher) se embedding assente o score basso
         ↓
markUsed(selected docs) + formatPromptBlock()
         ↓
ReasoningEngineImpl → system prompt LLM
```

### 4.4 Cosa NON cambia filosoficamente

- Estrazione da `conversationLog` (memoria + episodi) resta LLM-driven
- Consolidation LLM per merge frammenti
- Privacy on-device
- `MemoryIntentDetector` resta come **hint filtri**, non gate assoluto
- Macromemoria composabile: non tutto va iniettato sempre; si recupera per rilevanza

---

## 5. Architettura componenti

### 5.1 Nuovi componenti

| Componente | Responsabilità |
|------------|----------------|
| `TextEmbedder` | Carica ONNX, `embed(text): FloatArray`, lazy init, cache opzionale |
| `UnifiedMemoryIndex` | Persistenza documenti + HNSW / brute-force cosine |
| `UnifiedMemoryRepository` | CRUD, search, markUsed, prune, migrate from legacy |
| `UnifiedMemoryWriter` | Adapters write-path: extraction, tools, consolidation |
| `UnifiedMemoryPromptProvider` | Sostituisce injection frammentata per read-path dialogo |
| `MemoryConsolidationService` (v2) | Operates on `MemoryDocumentKind.USER_FACT` (+ optional EPISODE) |
| `MemoryReindexJob` | Re-embed batch dopo consolidation / model upgrade |

### 5.2 Componenti deprecati (fase transizione)

| Componente | Destino |
|------------|---------|
| `MemoryPromptContextProviderImpl` | → `UnifiedMemoryPromptProvider` |
| Injection separata Day/Activity per voice | → query RAG con filtri `kind` + `scheduledDayKey` |
| `MemoryTopicMatcher` come primary | → fallback secondario |
| Multi-DB write duplicato | → writer unico + sync legacy opzionale |

### 5.3 Componenti che restano (con adattamento)

| Componente | Ruolo |
|------------|-------|
| `MemoryIntentDetector` | Hint filtri metadata |
| `MemoryConsolidationService` | Logica merge LLM (adapter su nuovo schema) |
| `ActivityExtractionService` | Scrive `EPISODE` documents |
| `MemoryExtractionService` | Scrive `USER_FACT` documents |
| Tool locali | Scrivono via `UnifiedMemoryWriter` |
| Heartbeat playbook | Legge AUTONOMY + EPISODE imminenti via search/filtri |

---

## 6. Scelta tecnologica storage indice

### Opzione A — Room + embedding column (consigliata fase 1)

- Aggiungere tabella `memory_documents` in Room esistente o DB dedicato
- Colonna `embedding BLOB`
- Search: cosine similarity in memoria per N ≤ 2000
- **Pro:** minimo disrupzione, riusa migration Room, test facili
- **Contro:** no HNSW nativo; sufficiente fino a ~2k documenti attivi

### Opzione B — ObjectBox v4 + @HnswIndex

- Entity con vector index nativo
- **Pro:** HNSW performante a scala maggiore
- **Contro:** secondo stack DB, curva apprendimento, migrazione più costosa

### Opzione C — Ibrido

- Room = source of truth testuale + metadata
- ObjectBox / in-memory index = solo vettori + id mapping
- **Pro:** separazione concern
- **Contro:** sync dual-write

**Raccomandazione draft:** partire con **Opzione A**; passare a B solo se profiling mostra collo di bottiglia search > 50ms con corpus reale.

---

## 7. Modello embedding

### Scelta primaria (draft)

- **Modello:** `paraphrase-multilingual-MiniLM-L12-v2` (ONNX)
- **Dim:** 384
- **Runtime:** ONNX Runtime Android
- **Size:** ~120MB in assets o download first-run

### Alternativa

- `multilingual-e5-small` (~80MB) se benchmark italiano inferiore

### Gate qualità (obbligatorio pre-integrazione)

```kotlin
val benchmarkPairs = listOf(
    "il venerdì lavora dalle 9 alle 13" to "quando lavoro il venerdì",
    "il cane si chiama Brina" to "come si chiama il mio animale",
    "ogni mattina fa colazione alle 8" to "abitudini mattutine utente",
    "lun-gio lavora anche il pomeriggio 14-18" to "orari pomeridiani settimana",
)
// Soglia minima: cosine similarity >= 0.55 su tutte le coppie
// Se fallisce → cambiare modello prima di procedere
```

### Policy embedding

| Evento | Azione |
|--------|--------|
| Nuovo documento | embed on write (IO dispatcher) |
| Consolidation merge | embed solo righe canoniche nuove |
| Update value | re-embed |
| Soft delete | rimuovi da indice attivo |
| Model upgrade | `MemoryReindexJob` full rebuild background |

---

## 8. Read path — mapping query → retrieval

### 8.1 Turno dialogo standard

```kotlin
suspend fun buildContext(userText: String, hints: TurnHints): String {
    val detection = MemoryIntentDetector.detect(userText)
    val filters = buildFilters(detection, hints)
    val docs = unifiedMemoryRepository.searchRelevant(
        query = userText,
        limit = 20,
        filters = filters,
    )
    return formatKnownContextBlock(docs)
}
```

### 8.2 Filtri metadata per intent (esempi)

| Intent / domanda | Filtri suggeriti |
|------------------|------------------|
| PLAN / *"cosa devo fare oggi"* | `kind in [REMINDER, LIST_ITEM, EPISODE]` + `scheduledDayKey = today` |
| QUERY entity | `kind = USER_FACT` + semantic top-k |
| VISION / foto | `kind = USER_FACT`, category FACT/ROUTINE, **o** catalogo entity senza query |
| Social / WhatsApp | `kind = EPISODE`, category SOCIAL_THREAD, actor opzionale |
| Heartbeat | AUTONOMY + EPISODE imminenti TENTATIVE oggi |
| DEFAULT chat | USER_FACT top-k + EPISODE recenti opzionali |

### 8.3 Casi speciali (non solo vector)

| Caso | Strategia |
|------|-----------|
| Query vuota / vision | Catalogo entity: top N per `useCount` / `updatedAt` su USER_FACT FACT/ROUTINE |
| Promemoria imminente | Filtro temporale strutturato + sort by `scheduledAtMs` |
| *"segna come lette"* | Operazione su store, non retrieval |
| Forget *"dimentica cane"* | semantic search + soft delete matched ids |
| Domanda fuori dominio (*"chi era Garibaldi?"*) | Nessun hit ≥ `minScore` → **blocco memoria vuoto** (principio 5) |
| Query tipo proprietà/elenco (*"cosa è verde?"*) | Limite strutturale RAG; LLM + onestà o tool esplicito — non promettere elenco completo via similarity |

### 8.4 Soglia minima di pertinenza (`minScore`) — obbligatoria

Pattern cognitivo: il cervello non “riempie” con ricordi debolmente associati.

```kotlin
searchRelevant(query, limit = 20, filters, minScore = calibratedThreshold)
// calibratedThreshold: da benchmark Fase 0 (tipico 0.40–0.55), non fisso a priori
// Solo documenti con score >= minScore entrano nel prompt
// Se lista vuota → nessun blocco KNOWN CONTEXT (non iniettare rumore)
```

**Divisione del lavoro:**

| Fase | Responsabile | Cosa fa |
|------|--------------|---------|
| Retrieval + minScore | Codice | Scarta rumore **lontano**; risparmia token |
| Discernimento fine | LLM cognitivo | Tra candidati sopra soglia, usa/ignora (principio 6) |

Non sono in competizione: due filtri in sequenza con granularità diversa.

### 8.5 Confine di responsabilità retrieval ↔ LLM (principio 6 — non scusa)

Il principio 6 **non** giustifica retrieval mediocre (“il LLM sistemerà”). Ogni layer ha obblighi **misurabili**:

| Responsabilità | Owner | Obbligo verificabile |
|----------------|-------|----------------------|
| Rumore lontano / fuori dominio | Retrieval (`minScore`, filtri) | **M2b:** blocco vuoto su ≥90% query fuori dominio |
| Paraphrase pertinente trovata | Retrieval (embedding) | **M2 / M2c:** recall su golden set |
| Falso positivo semantico sopra soglia | LLM dialogo | **M6-discern:** risposta corretta ignorando candidato spurio |
| Query tipo elenco/proprietà | Prompt dialogo + LLM | **M6-honesty:** non inventare elenco completo (vedi §8.6) |

**Regola:** se M2/M2c falliscono → **non** attribuire al LLM; migliorare retrieval/soglie/modello.  
Se M2/M2c passano ma M6-discern fallisce → prompt dialogo o modello dialogo.

### 8.6 Dipendenza prompt dialogo (limite strutturale RAG)

Casi tipo *"cosa è verde?"* / *"cosa ho comprato di verde?"* richiedono istruzioni in `llm_system_prompt.txt` (fuori scope storage, **in scope release**):

- Non enumerare tutti gli elementi con una proprietà se il contesto iniettato è un campione similarity-based
- Usare solo memorie **pertinenti alla domanda specifica**; ignorare metafore/idiomi (es. “verde d’invidia”)
- Dire esplicitamente quando la memoria non basta per un elenco completo

Task implementativo: aggiornare prompt in **Fase 2** insieme a `UnifiedMemoryPromptProvider`.

---

## 9. Write path — adapter unificato

### 9.1 Mapping sorgenti → documenti

| Sorgente | kind | Note |
|----------|------|------|
| `save_memory` tool | USER_FACT | category da param |
| Memory extraction | USER_FACT | skip one-off task |
| Activity extraction | EPISODE | kind, confidence, actor |
| `log_daily_activity` | EPISODE | |
| `set_reminder` | REMINDER | externalRef = taskId |
| `add_list_item` | LIST_ITEM | |
| Spatial save/match | SPATIAL | |
| Heartbeat save | AUTONOMY | TTL da categoria |
| Consolidation output | USER_FACT | source = consolidation |

### 9.2 Idempotenza / dedup on write

1. Exact match su `(kind, category, normalizedValue)` → upsert
2. Semantic near-duplicate (cosine > **threshold da benchmark Fase 0**, same kind) → merge confidence, keep newer — **non fissare 0.92 a priori**
3. Consolidation periodica per cluster residui (pattern principale anti-fragmentazione)

### 9.3 Forza del ricordo (principio 1)

Pattern “usato spesso + difficile da dimenticare”:

| Segnale | Effetto |
|---------|---------|
| `useCount` ↑ su recall/injection | Memoria “forte” — ultima a essere prunata |
| `confidence` alta + IDENTITY/ROUTINE | Resistenza al prune |
| `lastUsedAt` recente | Boost opzionale in ranking (tie-break), non sostituto di semantic score |
| Consolidation | Rigenera canonical line; `useCount` reset su nuove righe (già oggi) |

Pruning: ordine `confidence ASC`, `useCount ASC`, `lastUsedAt ASC` — **dimentica prima ciò che non conta e non si usa**.

---

## 10. Consolidation e deframmentazione

### 10.1 Scope consolidation v2

- **Per kind** (non solo globale): USER_FACT per categoria; opzionale EPISODE stesso `scheduledDayKey`
- Trigger: fine sessione, manuale settings, post-estrazione se `writes >= N`
- Skip se `rows <= 3` per cluster

### 10.2 Pipeline

```text
1. Load active docs per (kind, category)
2. Backup JSON snapshot (rollback)
3. LLM: merge → righe canoniche italiane
4. Parse + validate ratio (MIN_OUTPUT_RATIO)
5. Soft-delete sorgenti + insert canonici
6. Re-embed solo nuove righe
7. Update content hash
```

### 10.3 Costo latenza consolidation

| Fase | Tempo stimato |
|------|---------------|
| LLM merge 40→8 righe | 1–5 s (già oggi) |
| Re-embed 8 righe | ~250 ms |
| Full reindex 500 docs | ~15 s (solo se necessario, background) |

**Regola:** consolidation e reindex **non bloccano** STT/TTS; job su `Dispatchers.IO` + flag `isReorganizing` in UI settings.

---

## 11. Migrazione — piano per fasi

### Fase 0 — Benchmark (1 settimana)

- [ ] Integrare `TextEmbedder` standalone + test ONNX su device target
- [ ] Benchmark coppie italiane (sezione 7)
- [ ] Calibrare **`minScore`** e soglia **dedup on-write** (non numeri fissi)
- [ ] Calibrare **hybrid weights** (cosine vs token) su subset golden — o dichiarare default provvisorio e spostare tuning a Fase 6
- [ ] Documentare p50/p95 embed latency
- [ ] Baseline AS-IS: recall token matcher sul golden set (per M2 comparativo)
- [ ] **Gate:** se embedding fallisce coppie italiane → stop o cambio modello

### Fase 1 — Foundation (1–2 settimane)

- [ ] Schema `MemoryDocument` + Room table / repository
- [ ] `UnifiedMemoryRepository.save()` + `searchRelevant()` con `minScore`
- [ ] **Migrazione batch one-shot** legacy → unified in standby (notte) — unified = SSOT
- [ ] **No dual-write prolungato**; rollback solo da backup pre-migrazione
- [ ] Test unitari search + markUsed + prune + empty recall

### Fase 2 — Read path voice (1 settimana)

- [ ] `UnifiedMemoryPromptProvider` sostituisce `MemoryPromptContextProviderImpl`
- [ ] Hybrid score durante transizione: peso da Fase 0 **o** default `0.7 cosine + 0.3 token` (tuning esplicito in Fase 6, non dogma)
- [ ] Aggiornare `llm_system_prompt.txt` — confine retrieval vs discernimento (§8.5–8.6)
- [ ] A/B log: confronto risposte su golden set; misurare M6-discern

### Fase 3 — Write path complete (1 settimana)

- [ ] Adapter extraction memoria + Log Day → unified
- [ ] Adapter tools (reminder, list, spatial)
- [ ] Forget/delete/search aggiornati

### Fase 4 — Consolidation v2 + reindex (1 settimana)

- [ ] Consolidation per kind/category su unified docs
- [ ] Backup / rollback UI in settings
- [ ] `MemoryReindexJob` incrementale

### Fase 5 — Deprecazione legacy (1 settimana)

- [ ] Rimuovere injection multi-provider ridondante (Day/Activity come primary)
- [ ] Mantenere fallback token-only se embedder down
- [ ] Migration one-shot dati legacy → unified
- [ ] Aggiornare docs + AGENTS.md

### Fase 6 — Tuning produzione

- [ ] Limit dinamico (10–25) per corpus size
- [ ] Profiling heartbeat / PLAN / VISION
- [ ] Pruning policy unificata cross-kind

---

## 12. Metriche di successo

### Must-have (release blocker)

| ID | Metrica | Target |
|----|---------|--------|
| M1 | Query *"orari di lavoro"* → risposta completa lun-ven | Tutti gli slot corretti |
| M2 | Recall@15 vs baseline token matcher | **Fase 0:** ≥75–80% golden set 30 query **e** miglioramento vs AS-IS; **post-consolidation:** ≥85%; target 90% a regime |
| M2b | Falsi positivi retrieval (fuori dominio) | 0 memorie iniettate (minScore) su ≥90% casi tipo “Garibaldi” |
| M2c | Falsi negativi retrieval (paraphrase pertinenti) | ≥85% domande con fatto noto in memoria **non** restituiscono blocco vuoto (es. “lavori da casa?” ↔ “smart working”) |
| M6-discern | Principio 6 — LLM ignora spurii sopra soglia | ≥90% su set **adversarial** noto (es. “cosa ho comprato di verde?” con “amico verde d’invidia” in contesto) |
| M6-honesty | Query elenco/proprietà | LLM non inventa elenco completo; ammette limite o chiede precisione — valutazione manuale su 5 casi |
| M3 | Latenza embed query per turno | p95 ≤ 50 ms |
| M4 | Latenza search | p95 ≤ 20 ms (N ≤ 2000) |
| M5 | Zero regressione vision catalog | Entity names iniettati pre-foto |
| M6 | PLAN oggi | Reminder + todo + episodi oggi via metadata filter |

### Nice-to-have

| ID | Metrica | Target |
|----|---------|--------|
| M7 | Riduzione righe duplicate post-consolidation | ≥ 50% cluster orari |
| M8 | useCount correlato a retrieval reale | markUsed su doc iniettati |
| M9 | APK size impact | Modello downloadable, APK + ≤ 5MB wrapper |

---

## 13. Set golden query (valutazione LLM reviewer)

Usare per test manuali e automatici:

```text
1. "Come si chiama il mio cane?"
2. "Quando lavoro il venerdì?"
3. "Cosa devo fare oggi?"
4. "Cosa devo fare domani?"
5. "Quando mi ha scritto Marco su WhatsApp?"
6. "Quali promemoria ho attivi?"
7. "Cosa c'è nella lista della spesa?"
8. "Dove sono?" / "In che stanza siamo?"
9. "Cosa sai di me?"
10. "Dimentica tutto sul cane"
11. [VISION] "Fai una foto" → catalogo entity disponibile
12. [HEARTBEAT] Episodio TENTATIVE oggi → proattività senza list_memories ogni tick
13. [M2b] "Chi era Garibaldi?" → 0 memorie iniettate
14. [M2c] "Lavori da casa?" con memoria "smart working" → memoria recuperata
15. [M6-discern] "Cosa ho comprato di verde?" con spurio "amico verde d'invidia" → solo maglia/comprato
16. [M6-honesty] "Cosa è verde che conosci?" → onestà su limiti elenco, no confabulazione
```

---

## 14. Rischi e mitigazioni

| Rischio | Impatto | Mitigazione |
|---------|---------|-------------|
| Modello ONNX troppo pesante | APK, RAM, cold start | Download first-run; e5-small fallback |
| False positive semantic | Risposte wrong topic | Filtri metadata + hybrid token score |
| Consolidation troppo aggressiva | Perdita fatti | Backup, ratio guard, rollback UI |
| Dual-write inconsistency | Drift legacy/unified | **Evitato:** unified SSOT + migrazione batch; no sync notturno fragile |
| EPISODE TTL 7 giorni vs USER_FACT permanente | Confusione indice | `expiresAt` + prune job per kind |
| VISION senza query testuale | Miss entity | Catalog path dedicato, non solo search |
| Reindex full durante dialogo | UI freeze | Background only + progress |

---

## 15. Decisioni chiuse (v0.2) e domande residue

### Chiuse

| # | Decisione | Scelta v0.2 |
|---|-----------|-------------|
| 1 | **SSOT** | Unified index = source of truth; legacy migrato poi deprecato |
| 2 | **EPISODE** | Doppio livello: episodio singolo + `HABIT_SUMMARY` aggregato |
| 4 | **Cap injection** | Dinamico: tutti i doc con `score >= minScore`, max 20 — non injection fissa “sempre tutto” |
| 5 | **ObjectBox v1** | No; Room + cosine fino a profiling |

### Ancora aperte

1. **Promemoria completati:** soft-delete immediato o retention breve per audit?
2. **Tool `list_memories`:** mantiene API attuale mappata su unified search?
3. **Multilingua:** solo italiano v1 o normalizzazione EN in consolidation?

---

## 16. Criteri per valutazione esterna (checklist reviewer LLM)

Il reviewer deve rispondere:

- [ ] Il problema P1–P5 è accurately scoped?
- [ ] Lo schema `MemoryDocument` copre tutti i write path attuali?
- [ ] I filtri metadata sono sufficienti per PLAN/VISION/HEARTBEAT?
- [ ] Il piano di migrazione è incrementale e reversibile?
- [ ] Le metriche M1–M6 sono testabili automaticamente?
- [ ] La latenza budget è realistica per device mid-range?
- [ ] Consolidation v2 evita duplicazione con `MemoryConsolidationService` attuale?
- [ ] Manca qualche failure mode (privacy, offline, model missing)?
- [ ] La raccomandazione Room-first vs ObjectBox-first è condivisibile?

- [ ] I principi cognitivi (§1.1) sono riflessi in recall/consolidation/pruning?
- [ ] `minScore` e soglie dedup sono calibrate in Fase 0, non magic numbers?
- [ ] Empty recall su domande fuori dominio è comportamento atteso?

**Review esterna v0.1:** GO-with-changes — incorporato in v0.2.  
**Review esterna v0.2:** GO — raffinamenti M2c / M6-discern / hybrid calibration in v0.3.

**Output atteso reviewer:** GO / GO-with-changes / NO-GO con elenco modifiche obbligatorie.

---

## 17. Fuori scope v1

- Embedding cloud (Gemini, OpenAI)
- Cross-device sync memoria
- Graph memory / relazioni esplicite tra documenti
- RAG su intero `conversationLog` storico (solo documenti estratti)
- Auto-modifica prompt system da retrieval feedback

---

## 18. Riferimenti codice attuale

| Area | File principale |
|------|-----------------|
| Injection memoria | `app/src/main/java/.../integration/memory/MemoryPromptContextProviderImpl.kt` |
| Token retrieval | `app/src/main/java/.../memory/MemoryTopicMatcher.kt` |
| Consolidation | `app/src/main/java/.../memory/consolidate/MemoryConsolidationService.kt` |
| Intent hint | `app/src/main/java/.../reasoning/memory/MemoryIntentDetector.kt` |
| Prompt assembly | `app/src/main/java/.../reasoning/ReasoningEngineImpl.kt` |
| Log Day injection | `DayContextPromptProviderImpl.kt`, `ActivityContextProviderImpl.kt` |
| Estrazione | `MemoryExtractionService.kt`, `ActivityExtractionService.kt` |
| Documentazione | `docs/MEMORY.md`, `docs/ACTIVITY_LOG.md` |

---

## 19. Decision log (da compilare in implementazione)

| Data | Decisione | Razionale |
|------|-----------|-----------|
| 2025-06-19 | Unified = SSOT | Evita dual-write; un accesso cognitivo |
| 2025-06-19 | Consolidation resta | Anti-fragmentazione; complementare al RAG |
| 2025-06-19 | Pattern cognitivi > regole rigide | LLM cognitivo + retrieval come supporto |
| 2025-06-19 | Review v0.2 → GO | Inizio implementazione da Fase 0 |
| 2025-06-19 | Confine retrieval/LLM misurabile | M2b + M2c + M6-discern (v0.3) |
| TBD | Storage: Room vs ObjectBox | Dopo benchmark N e latency |
| TBD | minScore + dedup + hybrid weights | Calibrazione Fase 0 / tuning Fase 6 |

---

*Fine documento — Unified Memory RAG Plan v0.3-draft*
