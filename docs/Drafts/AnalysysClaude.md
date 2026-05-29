# My Desk Robot — Prompt per Vibe Coding

## Contesto del Progetto

Stai lavorando su **My Desk Robot**, un'app Android (Kotlin) per un robot da scrivania con le seguenti caratteristiche già implementate:

- Wake word detection ("ehi robot") + STT + TTS
- Integrazione LLM via API OpenAI-compatible (LM Studio locale o Gemini cloud)
- Sistema occhi animati con emozioni (`happy`, `sad`, `angry`, `surprised`, `confused`, `neutral`, `bored`, `thinking`)
- Visione: scatto foto fotocamera frontale + modello multimodale (2 passaggi LLM)
- Lettura notifiche Android (WhatsApp, SMS, Gmail, Calendario) come input al ReasoningEngine
- Sistema di profili/contesto robot (`set_robot_context`: normal, work, call, meeting, focus)
- Architettura a 3 layer: **Robot UI** (Android) → **Reasoning Module** (Kotlin puro) → **External Integrations**
- `SystemInputDispatcher` (SharedFlow bus) che riceve input da qualsiasi sorgente
- `InputPolicyEngine` con priorità BLOCKING / DEFERRED
- `DeferredInputQueue` con dedup e TTL
- Il LLM risponde sempre in JSON strutturato:

```json
{
  "reply": "testo parlato dal robot",
  "emotion": "happy",
  "action": {
    "type": "none | tool_call | confirm_required",
    "tools": [
      {
        "name": "nome_tool",
        "params": {},
        "await_result": true,
        "purpose": "perché chiamo questo tool"
      }
    ],
    "parallel": false,
    "chain_status": "in_progress | complete | failed"
  }
}
```

---

## Cosa Devi Implementare

### 1. Tick Autonomo (`HeartbeatInputSource`)

Implementa un input periodico che alimenta il `ReasoningEngine` anche in assenza di input esterni.

**Comportamento:**
- Si attiva ogni X minuti (configurabile in Settings, default: 5 minuti)
- Emette un `RobotInput.Heartbeat` nel `SystemInputDispatcher`
- Priorità: `DEFERRED` (non interrompe conversazioni in corso)
- Il gate del microfono attivo già esistente si applica normalmente
- Funziona sia in standby (`WaitingForHotword`) che in sessione attiva

**RobotInput da aggiungere:**
```kotlin
data class Heartbeat(
    val minutesSinceLastInteraction: Long,
    val currentHour: Int,
    val currentMinute: Int,
    val dayOfWeek: String,
    override val timestamp: Long = System.currentTimeMillis(),
) : RobotInput() {
    override val sourceId: String = "heartbeat"
    override val priority: InputPriority = InputPriority.DEFERRED
}
```

**Formato per LLM:**
```
[SYSTEM_INPUT: heartbeat]
Ora: 15:30
Giorno: Lunedì
Minuti dall'ultima interazione: 87
```

**Implementazione:**
- Usa `AlarmManager` con `setRepeating` o `WorkManager` per persistenza
- Il receiver emette `SystemInputEvent.InputReceived` nel dispatcher
- Deve sopravvivere a rotation e background (usa un Service o WorkManager)
- Aggiungi `HeartbeatInputSource` seguendo l'interfaccia `InputSource` esistente

---

### 2. Memoria Persistente (`MemoryRepository`)

Il LLM deve poter leggere e scrivere fatti persistenti sull'utente, aggiornati nel tempo.

**Struttura dati:**
```kotlin
data class MemoryEntry(
    val id: String,           // UUID
    val content: String,      // "Il lunedì porta a spasso il cane alle 18"
    val category: String,     // "routine" | "preference" | "project" | "person" | "other"
    val createdAt: Long,
    val updatedAt: Long,
    val importance: Int,      // 1-5, per prioritizzare il context
)
```

**Storage:** SQLite via Room, tabella `memory_entries`

**Iniezione nel system prompt** (via `RobotContextProvider` esistente):
```
[MEMORIA UTENTE]
- Il lunedì porta a spasso il cane alle 18 (routine)
- Progetto attuale: My Desk Robot, app Android (project)
- Preferisce risposte concise (preference)
```

**Regole di iniezione:**
- Massimo 10 entry nel prompt (le più importanti per `importance` desc)
- Ordinate per `importance` DESC, poi `updatedAt` DESC
- Se context window è limitato, tronca a 500 caratteri totali

**Tool per la memoria:**
```
TOOL: save_memory
DESCRIPTION: Salva un fatto importante sull'utente per ricordarlo in futuro
PARAMS:
  - content (string, required): Il fatto da ricordare
  - category (string, required): "routine" | "preference" | "project" | "person" | "other"
  - importance (int, optional, default 3): Importanza 1-5

TOOL: delete_memory
DESCRIPTION: Elimina un ricordo non più valido
PARAMS:
  - id (string, required): ID del ricordo da eliminare

TOOL: list_memories
DESCRIPTION: Elenca tutti i ricordi salvati (uso su richiesta utente)
PARAMS: nessuno
```

---

### 3. Tool Base — Reminder

```
TOOL: set_reminder
DESCRIPTION: Imposta un promemoria che il robot annuncerà vocalmente alla scadenza
PARAMS:
  - text (string, required): Testo del promemoria
  - trigger_at_millis (long, optional): Timestamp Unix ms assoluto
  - delay_minutes (int, optional): Minuti da adesso (alternativo a trigger_at_millis)
  - repeat_daily (boolean, optional, default false): Ripeti ogni giorno alla stessa ora

TOOL: get_reminders
DESCRIPTION: Elenca i promemoria attivi
PARAMS: nessuno

TOOL: delete_reminder
DESCRIPTION: Cancella un promemoria
PARAMS:
  - id (string, required)
```

**Implementazione:**
- Persistenza Room, tabella `reminders`
- `AlarmManager` per la scadenza → al trigger emette `RobotInput.ReminderFired` nel dispatcher
- Il LLM riceve l'input e decide come annunciarlo (es. con emozione `happy` o `surprised`)
- `ReminderFired` ha priorità `BLOCKING` se il robot è in standby, `DEFERRED` se è occupato

**RobotInput:**
```kotlin
data class ReminderFired(
    val reminderId: String,
    val text: String,
    override val timestamp: Long = System.currentTimeMillis(),
) : RobotInput() {
    override val sourceId: String = "reminder"
    override val priority: InputPriority = InputPriority.BLOCKING
}
```

---

### 4. Tool Base — Note

```
TOOL: take_notes
DESCRIPTION: Salva un appunto testuale
PARAMS:
  - title (string, optional): Titolo breve
  - content (string, required): Contenuto dell'appunto
  - tags (list<string>, optional): Tag per categorizzare

TOOL: read_notes
DESCRIPTION: Legge gli appunti salvati, opzionalmente filtrati
PARAMS:
  - tag (string, optional): Filtra per tag
  - limit (int, optional, default 5): Numero massimo di appunti da restituire

TOOL: delete_note
DESCRIPTION: Elimina un appunto
PARAMS:
  - id (string, required)
```

**Implementazione:** Room, tabella `notes`. Nessun trigger temporale, solo lettura/scrittura su richiesta.

---

### 5. Tool Base — Meteo (`get_weather`)

```
TOOL: get_weather
DESCRIPTION: Ottieni il meteo attuale o le previsioni per una città
PARAMS:
  - city (string, optional): Città (default: città configurata in Settings)
  - forecast_days (int, optional, default 0): 0 = solo oggi, 1-7 = previsioni

RETURNS:
  - temperature_c (float)
  - feels_like_c (float)
  - condition (string): "sunny" | "cloudy" | "rainy" | "snowy" | "windy"
  - humidity_percent (int)
  - forecast (list, se richiesto)
```

**Provider:** Open-Meteo (gratuito, no API key). Geocoding con Nominatim.
**Città default:** configurabile in Settings, salvata in DataStore.

---

### 6. Tool Base — Ricerca Web (`web_search`)

```
TOOL: web_search
DESCRIPTION: Cerca informazioni aggiornate sul web
PARAMS:
  - query (string, required): Query di ricerca
  - max_results (int, optional, default 3): Numero risultati

RETURNS:
  - results: list di { title, url, snippet }
```

**Provider:** DuckDuckGo Instant Answer API (gratuito, no API key) oppure SerpApi (configurabile).

---

### 7. Tool Base — Fetch URL (`fetch_url`)

```
TOOL: fetch_url
DESCRIPTION: Legge il contenuto di una pagina web e lo restituisce come testo pulito
PARAMS:
  - url (string, required)
  - max_chars (int, optional, default 2000): Tronca il contenuto

RETURNS:
  - title (string)
  - content (string): Testo estratto, HTML stripped
```

**Implementazione:** OkHttp + Jsoup per parsing HTML → testo pulito.

---

### 8. Tool Base — Lista della Spesa (`shopping_list`)

```
TOOL: shopping_list_add
DESCRIPTION: Aggiunge uno o più item alla lista della spesa
PARAMS:
  - items (list<string>, required): Lista di prodotti da aggiungere

TOOL: shopping_list_read
DESCRIPTION: Legge la lista della spesa attuale
PARAMS: nessuno

TOOL: shopping_list_remove
DESCRIPTION: Rimuove un item dalla lista
PARAMS:
  - item (string, required): Nome del prodotto

TOOL: shopping_list_clear
DESCRIPTION: Svuota tutta la lista della spesa
PARAMS: nessuno
```

**Implementazione:** Room, tabella `shopping_items` con campo `checked: Boolean`.

---

### 9. Tool Visione — Analisi Ambiente

Questi tool usano la fotocamera già implementata ma con prompt specifici al modello vision.

```
TOOL: detect_presence
DESCRIPTION: Scatta una foto e determina se l'utente è alla scrivania
PARAMS: nessuno
RETURNS:
  - present (boolean)
  - confidence (string): "high" | "medium" | "low"
  - description (string): breve descrizione scena

TOOL: analyze_environment
DESCRIPTION: Analizza l'ambiente: luce, ordine, presenza persone
PARAMS: nessuno
RETURNS:
  - light_level (string): "dark" | "dim" | "adequate" | "bright"
  - user_present (boolean)
  - notes (string): osservazioni rilevanti
```

**Implementazione:** Riusa `CameraXVisionImageCapture` esistente. Passa un prompt specifico al modello vision invece del prompt generico. Restituisce JSON strutturato.

---

## Aggiornamenti al System Prompt

Aggiungi queste sezioni al file `llm_system_prompt.txt`:

```
## COMPORTAMENTO AUTONOMO

Ricevi due tipi di input:
1. INPUT UTENTE: frasi vocali dirette → rispondi sempre
2. SYSTEM_INPUT: notifiche, heartbeat, reminder, sensori → valuta se agire

Per SYSTEM_INPUT valuta sempre:
- È rilevante per l'utente in questo momento?
- Il profilo attivo lo permette? (work/call/meeting → solo urgente)
- L'utente è presente? (se hai dati dalla fotocamera)
- Quante ore sono? (non disturbare di notte)
- Da quanto tempo non interagisce? (se > 2 ore, forse non c'è)

Se decidi di NON agire: `"action": {"type": "none"}` e `"reply": ""`

## HEARTBEAT

Quando ricevi [SYSTEM_INPUT: heartbeat]:
- Valuta il contesto completo (ora, giorno, ultima interazione, memoria utente)
- Puoi: suggerire una pausa, commentare il meteo, ricordare qualcosa di rilevante,
  scattare una foto per verificare la presenza, o semplicemente non fare nulla
- Non essere invadente: se hai già interagito di recente, preferisci il silenzio
- Usa detect_presence prima di parlare se non sei sicuro che l'utente ci sia

## MEMORIA

Salva automaticamente in memoria quando l'utente:
- Menziona una routine ricorrente ("ogni lunedì...", "di solito alle...")
- Esprime una preferenza ("preferisco...", "non mi piace...")
- Parla di un progetto in corso
- Fornisce informazioni personali rilevanti

Non chiedere conferma per salvare memorie di bassa importanza (1-2).
Chiedi conferma per importanza 4-5.

## TOOL DISPONIBILI

[lista tool aggiornata con tutti quelli implementati]
```

---

## Architettura File da Creare/Modificare

```
reasoning/model/
  RobotInput.kt                    → aggiungi Heartbeat, ReminderFired
  MemoryEntry.kt                   → nuovo
  ReminderEntry.kt                 → nuovo
  NoteEntry.kt                     → nuovo
  ShoppingItem.kt                  → nuovo

domain/
  memory/MemoryRepository.kt       → nuovo (interface)
  reminder/ReminderRepository.kt   → nuovo (interface)
  notes/NotesRepository.kt         → nuovo (interface)
  shopping/ShoppingRepository.kt   → nuovo (interface)

data/
  memory/MemoryRepositoryImpl.kt   → Room
  reminder/ReminderRepositoryImpl.kt → Room + AlarmManager
  notes/NotesRepositoryImpl.kt     → Room
  shopping/ShoppingRepositoryImpl.kt → Room

integration/
  input/heartbeat/
    HeartbeatInputSource.kt        → nuovo
    HeartbeatScheduler.kt          → AlarmManager / WorkManager
    HeartbeatReceiver.kt           → BroadcastReceiver
  input/reminder/
    ReminderFiredReceiver.kt       → BroadcastReceiver → emette ReminderFired
  tool/local/
    SetReminderTool.kt             → nuovo
    GetRemindersTool.kt            → nuovo
    DeleteReminderTool.kt          → nuovo
    TakeNotesTool.kt               → nuovo
    ReadNotesTool.kt               → nuovo
    DeleteNoteTool.kt              → nuovo
    ShoppingListAddTool.kt         → nuovo
    ShoppingListReadTool.kt        → nuovo
    ShoppingListRemoveTool.kt      → nuovo
    ShoppingListClearTool.kt       → nuovo
    SaveMemoryTool.kt              → nuovo
    DeleteMemoryTool.kt            → nuovo
    ListMemoriesTool.kt            → nuovo
    DetectPresenceTool.kt          → nuovo (usa CameraXVisionImageCapture)
    AnalyzeEnvironmentTool.kt      → nuovo (usa CameraXVisionImageCapture)
  tool/remote/
    GetWeatherTool.kt              → nuovo (Open-Meteo API)
    WebSearchTool.kt               → nuovo (DuckDuckGo API)
    FetchUrlTool.kt                → nuovo (OkHttp + Jsoup)

context/
  RobotContextPromptProviderImpl.kt → modifica: inietta memoria nel system prompt

assets/prompts/
  llm_system_prompt.txt            → modifica: aggiungi sezioni heartbeat, memoria, tool
```

---

## Vincoli e Note Implementative

- **Kotlin puro nel Reasoning Module** — nessuna dipendenza Android in `reasoning/` e `domain/`
- **Room** per tutta la persistenza locale (non SharedPreferences per dati strutturati)
- **AlarmManager** per timer precisi (reminder, heartbeat) — WorkManager per task flessibili
- **Open-Meteo** per meteo: `https://api.open-meteo.com/v1/forecast` — gratuito, no key
- **DuckDuckGo** per search: `https://api.duckduckgo.com/?q=QUERY&format=json` — gratuito, no key
- **Jsoup** per HTML parsing (aggiungi dipendenza `org.jsoup:jsoup:1.17.2`)
- Il heartbeat **non deve girare di notte**: aggiungi gate `currentHour in 7..23` nell'InputSource
- Tutti i tool devono implementare l'interfaccia `RobotTool` esistente
- Seguire il pattern `InputSource` esistente per `HeartbeatInputSource`
- La memoria viene iniettata da `RobotContextPromptProviderImpl` insieme al contesto robot esistente
- **Dedup heartbeat**: se il robot è in conversazione attiva, il tick viene messo in `DeferredInputQueue` normalmente — non serve logica speciale

---

## Priorità di Implementazione Suggerita

1. **Memoria** — sblocca subito personalizzazione, basso effort
2. **Reminder** — usa quotidianamente, alto valore percepito
3. **Heartbeat** — abilita proattività, core della "personalità" del robot
4. **Meteo** — primo tool remoto, proof-of-concept tool chaining
5. **Note** — semplice, molto usato
6. **detect_presence + analyze_environment** — riusa visione esistente
7. **Lista della spesa** — semplice CRUD
8. **web_search + fetch_url** — amplia molto le capacità del LLM