# Tool Architecture — My Desk Robot

> Documento di design per l'infrastruttura di gestione dei tool  
> Versione: 1.1  
> Data: 2026-05-25  
> Ultimo aggiornamento: 2026-05-25

---

## Indice

1. [Contesto e Obiettivi](#1-contesto-e-obiettivi)
2. [Glossario e Terminologia](#2-glossario-e-terminologia)
3. [Tassonomia dei Tool](#3-tassonomia-dei-tool)
4. [Architettura di Esecuzione](#4-architettura-di-esecuzione)
5. [Stato Attuale dell'Architettura](#5-stato-attuale-dellarchitettura)
6. [Approccio A: Function Calling OpenAI-Style](#6-approccio-a-function-calling-openai-style)
7. [Approccio B: JSON Strutturato con Tool Actions](#7-approccio-b-json-strutturato-con-tool-actions)
8. [Approccio C: Model Context Protocol (MCP)](#8-approccio-c-model-context-protocol-mcp)
9. [Analisi Comparativa](#9-analisi-comparativa)
10. [Raccomandazione Finale](#10-raccomandazione-finale)
11. [Tool Chaining: Catene di Azioni Autonome](#11-tool-chaining-catene-di-azioni-autonome)
12. [Piano di Implementazione](#12-piano-di-implementazione)
13. [Appendice: Schemi JSON e Contratti](#13-appendice-schemi-json-e-contratti)

---

## 1. Contesto e Obiettivi

### 1.1 Visione

My Desk Robot è un assistente vocale Android che deve evolvere da semplice Q&A a **agente capace di eseguire azioni** nel mondo reale. L'LLM non deve solo rispondere, ma anche:

- Interrogare servizi esterni (meteo, news, calendario)
- Controllare dispositivi locali (fotocamera, notifiche, media player)
- Comandare hardware fisico (ESP32 per movimenti servo/LED)

### 1.2 Requisiti Chiave

| Requisito | Descrizione |
|-----------|-------------|
| **R1 - Modularità** | Aggiungere un nuovo tool senza modificare core LLM o ViewModel |
| **R2 - Località trasparente** | L'LLM non deve sapere se un tool è locale o remoto |
| **R3 - Composabilità** | Un singolo turno può richiedere più tool in sequenza |
| **R4 - Feedback sincrono** | Il robot deve poter comunicare risultati intermedi (es. "Sto cercando...") |
| **R5 - Graceful degradation** | Se un tool fallisce, l'LLM riceve l'errore e può reagire |
| **R6 - Sicurezza** | Tool sensibili (es. pagamenti) richiedono conferma utente |
| **R7 - Estensibilità hardware** | Supporto futuro ESP32 senza riscrittura architettura |
| **R8 - Testabilità** | Tool mockabili per unit/integration test |

### 1.3 Vincoli

- **Backend LLM**: Qualsiasi provider compatibile OpenAI API (LM Studio, OpenAI, Claude, Gemini, Groq, Together, ecc.)
- **Modello minimo**: Gemma 4 E4B o equivalenti (8B+ parametri consigliati). Modelli più leggeri (< 4B) non garantiscono output JSON strutturato affidabile
- Nessun cloud proprietario obbligatorio (tutto self-hosted possibile)
- Latenza accettabile: < 3s per tool semplici, feedback per tool lunghi
- Android minSdk 24

### 1.4 Principi Architetturali

| Principio | Descrizione |
|-----------|-------------|
| **Separazione UI/Reasoning** | La logica di orchestrazione tool è completamente separata dalla UI Android e dal robot |
| **Portabilità** | Il modulo reasoning/tool può essere estratto e usato in altri contesti (server, CLI, altro client) |
| **Provider-agnostic** | Cambiare LLM provider richiede solo configurazione, non modifica codice |
| **Testabilità isolata** | Tool orchestrator testabile senza Android framework |

---

## 2. Glossario e Terminologia

Per evitare ambiguità, questo documento utilizza i seguenti termini:

| Termine | Significato |
|---------|-------------|
| **Device mobile** | Il dispositivo Android su cui gira l'app My Desk Robot |
| **LLM Provider** | Servizio che espone un modello LLM (LM Studio, OpenAI, Claude API, Gemini API, Groq, Together, ecc.) |
| **LLM / Modello** | Il Large Language Model specifico (es. Gemma 4 E4B, GPT-4, Claude 3, Llama 3.1) |
| **Servizio esterno** | API di terze parti (meteo, news, YouTube) raggiungibili via HTTP |
| **Hardware ESP32** | Il microcontrollore per movimenti fisici del robot (futuro) |
| **Tool locale** | Tool eseguito direttamente sul device mobile (camera, intent, notifiche) |
| **Tool remoto** | Tool che richiede chiamata HTTP a servizio esterno |
| **Tool hardware** | Tool che invia comandi all'ESP32 via BLE/WiFi |
| **Catena / Chain** | Sequenza di tool eseguiti in più turni LLM |
| **Reasoning Module** | Componente autonomo che gestisce orchestrazione tool e ragionamento LLM |
| **Robot UI** | Interfaccia utente specifica del robot (occhi, TTS, STT) |

### 2.2 Requisiti Minimi Modello

| Categoria | Modelli Compatibili | Note |
|-----------|---------------------|------|
| **Consigliati** | Gemma 4 E4B, Llama 3.1 8B+, GPT-4, Claude 3, Mistral 7B+ | Output JSON affidabile |
| **Limite inferiore** | ~4B parametri con fine-tuning JSON | Potrebbero avere errori di formato |
| **Non supportati** | Phi-2, TinyLlama, modelli < 3B | JSON troppo instabile |

Il modello deve essere in grado di:
- Generare JSON valido in modo consistente
- Seguire istruzioni strutturate nel system prompt
- Ragionare su risultati tool per decidere il prossimo step

### 2.3 Architettura a Layer Separati

L'architettura è divisa in **tre layer indipendenti**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ARCHITETTURA MODULARE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    LAYER 1: ROBOT UI (Android-specific)              │   │
│  │                                                                      │   │
│  │   Responsabilità:                                                    │   │
│  │   • STT (Speech-to-Text) - input vocale utente                       │   │
│  │   • TTS (Text-to-Speech) - output vocale robot                       │   │
│  │   • UI occhi animati e feedback visivo                               │   │
│  │   • Gestione permessi Android                                        │   │
│  │   • Lifecycle Activity/Service                                       │   │
│  │                                                                      │   │
│  │   ⚠️ NON contiene logica di reasoning o tool orchestration          │   │
│  └────────────────────────────────┬─────────────────────────────────────┘   │
│                                   │                                         │
│                                   │ ToolRequest / ToolResponse              │
│                                   │ (interfaccia astratta)                  │
│                                   ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │              LAYER 2: REASONING MODULE (Platform-agnostic)           │   │
│  │                                                                      │   │
│  │   ┌────────────────────────────────────────────────────────────┐    │   │
│  │   │                  ToolChainOrchestrator                      │    │   │
│  │   │                                                             │    │   │
│  │   │   • Gestisce conversazione multi-turn con LLM               │    │   │
│  │   │   • Parsa risposte JSON e decide azioni                     │    │   │
│  │   │   • Mantiene conversation history                           │    │   │
│  │   │   • Applica safeguard (max steps, timeout)                  │    │   │
│  │   │   • Delega esecuzione tool al ToolRouter                    │    │   │
│  │   └────────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │   │
│  │   │   LlmClient     │  │   ToolRouter    │  │  ResponseParser │     │   │
│  │   │   (interface)   │  │   (interface)   │  │                 │     │   │
│  │   └────────┬────────┘  └────────┬────────┘  └─────────────────┘     │   │
│  │            │                    │                                    │   │
│  │   ⚠️ Kotlin puro, NESSUNA dipendenza Android                        │   │
│  │   ⚠️ Riutilizzabile in server, CLI, altri client                    │   │
│  └────────────┼────────────────────┼────────────────────────────────────┘   │
│               │                    │                                        │
│               ▼                    ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                LAYER 3: EXTERNAL INTEGRATIONS                        │   │
│  │                                                                      │   │
│  │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │   │
│  │   │ LLM Provider│  │   Remote    │  │   Local     │  │  Hardware │  │   │
│  │   │             │  │   Tools     │  │   Tools     │  │   Tools   │  │   │
│  │   │ • LM Studio │  │             │  │             │  │           │  │   │
│  │   │ • OpenAI    │  │ • Meteo API │  │ • Camera    │  │ • ESP32   │  │   │
│  │   │ • Claude    │  │ • News API  │  │ • Intents   │  │ • Servo   │  │   │
│  │   │ • Gemini    │  │ • YouTube   │  │ • Alarms    │  │ • LED     │  │   │
│  │   │ • Groq      │  │ • TV Guide  │  │ • Notifiche │  │ • Sensori │  │   │
│  │   └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 Separazione delle Responsabilità

| Layer | Package | Dipendenze | Sostituibile |
|-------|---------|------------|--------------|
| **Robot UI** | `com.example.mydeskrobot.ui`, `.presentation` | Android SDK, Compose | Sì (altra UI) |
| **Reasoning Module** | `com.example.mydeskrobot.reasoning` | Kotlin stdlib, Coroutines, Moshi | Sì (altro client) |
| **External Integrations** | `com.example.mydeskrobot.integration` | Retrofit, Android SDK (per local tools) | Sì (altri provider) |

### 2.5 Interfacce di Confine

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// LAYER 2 → LAYER 3: LLM Provider Interface
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Interfaccia per qualsiasi LLM provider (LM Studio, OpenAI, Claude, Gemini, ecc.)
 * Implementazioni: LmStudioClient, OpenAiClient, ClaudeClient, GeminiClient
 */
interface LlmClient {
    suspend fun chat(
        messages: List<ConversationMessage>,
        systemPrompt: String,
    ): Result<LlmResponse>
    
    suspend fun chatWithImage(
        messages: List<ConversationMessage>,
        systemPrompt: String,
        image: ByteArray,
    ): Result<LlmResponse>
}

// ═══════════════════════════════════════════════════════════════════════════
// LAYER 2 → LAYER 3: Tool Execution Interface
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Interfaccia per eseguire tool. Il Reasoning Module non sa DOVE vengono eseguiti.
 * L'implementazione decide se locale, remoto o hardware.
 */
interface ToolExecutor {
    suspend fun execute(invocation: ToolInvocation): ToolResult
    fun getAvailableTools(): List<ToolDefinition>
}

// ═══════════════════════════════════════════════════════════════════════════
// LAYER 1 ↔ LAYER 2: Reasoning Module Interface
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Interfaccia principale esposta dal Reasoning Module al Robot UI.
 * Il Robot UI chiama questo per processare input utente.
 */
interface ReasoningEngine {
    suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult
    
    suspend fun processUserInputWithImage(
        userText: String,
        image: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult
    
    fun reset()  // Pulisce conversation history
}

data class IntermediateResponse(
    val text: String,
    val emotion: String?,
    val isToolExecuting: Boolean,
    val toolName: String? = null,
)

sealed class ReasoningResult {
    data class Success(
        val finalText: String,
        val emotion: String?,
    ) : ReasoningResult()
    
    data class NeedsConfirmation(
        val prompt: String,
        val pendingAction: suspend (confirmed: Boolean) -> ReasoningResult,
    ) : ReasoningResult()
    
    data class Error(val message: String) : ReasoningResult()
}
```

### 2.6 Vantaggi della Separazione

| Vantaggio | Descrizione |
|-----------|-------------|
| **Testabilità** | `ReasoningEngine` testabile con mock `LlmClient` e `ToolExecutor`, senza Android |
| **Riusabilità** | Stesso `ReasoningEngine` usabile in app iOS, server backend, CLI |
| **Manutenibilità** | Cambiare LLM provider = nuova implementazione `LlmClient`, zero modifiche al reasoning |
| **Scalabilità** | Reasoning può girare su server, device mobile fa solo UI |
| **Sostituibilità** | Robot UI sostituibile con altra interfaccia (web, voice-only, ecc.) |

---

## 3. Tassonomia dei Tool

```
┌─────────────────────────────────────────────────────────────────┐
│                         TOOL UNIVERSE                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   SOFTWARE-ONLY                          │   │
│  │  ┌─────────────────────┐  ┌────────────────────────────┐ │   │
│  │  │     REMOTE-ONLY     │  │     LOCAL + REMOTE         │ │   │
│  │  │  (Server-side)      │  │  (Local HW + optional API) │ │   │
│  │  │                     │  │                            │ │   │
│  │  │  • get_weather      │  │  • take_photo (camera)     │ │   │
│  │  │  • get_tv_guide     │  │  • play_spotify (intent)   │ │   │
│  │  │  • search_web       │  │  • set_reminder (alarm)    │ │   │
│  │  │  • get_news         │  │  • send_notification       │ │   │
│  │  │  • translate_text   │  │  • read_clipboard          │ │   │
│  │  │  • wolfram_alpha    │  │  • control_volume          │ │   │
│  │  └─────────────────────┘  └────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   HARDWARE (ESP32)                       │   │
│  │                                                          │   │
│  │  • move_head(pan, tilt)     • set_led_color(r, g, b)    │   │
│  │  • wave_hand()              • play_sound_effect(id)     │   │
│  │  • nod_yes() / shake_no()   • get_sensor_data()         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.1 Caratteristiche per Categoria

| Categoria | Esecuzione | Latenza | Stato | Esempio |
|-----------|------------|---------|-------|---------|
| Remote-only | Server LLM o proxy | 100ms-2s | Stateless | `get_weather("Milano")` |
| Local+Remote | App Android | 50ms-5s | Può avere stato | `take_photo()` → JPEG |
| Hardware ESP32 | BLE/WiFi verso MCU | 20ms-500ms | Stateful | `move_head(45, -10)` |

### 3.2 Mappatura Tool → Località

| Tool | Locality | Eseguito da | Meccanismo |
|------|----------|-------------|------------|
| `get_weather` | REMOTE | Servizio esterno | HTTP API (OpenWeatherMap) |
| `get_tv_guide` | REMOTE | Servizio esterno | HTTP API |
| `web_search` | REMOTE | Servizio esterno | HTTP API (Google/DuckDuckGo) |
| `youtube_search` | REMOTE | Servizio esterno | YouTube Data API |
| `get_news` | REMOTE | Servizio esterno | HTTP API |
| `translate_text` | REMOTE | Servizio esterno | HTTP API |
| `take_photo` | LOCAL | Device mobile | CameraX |
| `open_browser` | LOCAL | Device mobile | `Intent.ACTION_VIEW` |
| `play_spotify` | LOCAL | Device mobile | Intent a Spotify app |
| `play_youtube` | LOCAL | Device mobile | Intent a YouTube app |
| `set_reminder` | LOCAL | Device mobile | AlarmManager |
| `send_notification` | LOCAL | Device mobile | NotificationManager |
| `control_volume` | LOCAL | Device mobile | AudioManager |
| `move_head` | HARDWARE | ESP32 | BLE/WiFi command |
| `set_led` | HARDWARE | ESP32 | BLE/WiFi command |
| `wave_hand` | HARDWARE | ESP32 | BLE/WiFi command |

### 3.3 Flusso di Dati

```
┌─────────┐    voice    ┌─────────┐   prompt   ┌─────────┐
│  User   │ ──────────► │   STT   │ ─────────► │   LLM   │
└─────────┘             └─────────┘            └────┬────┘
                                                    │
                        ┌───────────────────────────┴───────────────────────────┐
                        │                    TOOL DECISION                       │
                        │  { "action": "tool_call", "tool": "get_weather", ...} │
                        └───────────────────────────┬───────────────────────────┘
                                                    │
                        ┌───────────────────────────▼───────────────────────────┐
                        │                  TOOL EXECUTOR                         │
                        │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────────┐  │
                        │  │ Remote  │  │ Local   │  │ Vision  │  │ Hardware │  │
                        │  │ Service │  │ Android │  │ Camera  │  │  ESP32   │  │
                        │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬─────┘  │
                        │       │            │            │            │         │
                        │       └────────────┴────────────┴────────────┘         │
                        │                         │                              │
                        │                   Tool Result                          │
                        └─────────────────────────┬──────────────────────────────┘
                                                  │
                                                  ▼
                        ┌─────────────────────────────────────────────────────────┐
                        │  LLM (continuation) → generates final reply with result │
                        └───────────────────────────┬─────────────────────────────┘
                                                    │
                                                    ▼
                        ┌─────────┐   audio    ┌─────────┐
                        │   TTS   │ ◄───────── │  Reply  │
                        └─────────┘            └─────────┘
```

---

## 4. Architettura di Esecuzione

### 4.1 Il Device Mobile come Orchestratore

Il device mobile (Android) è il **centro di controllo**. L'LLM non sa e non deve sapere dove viene eseguito un tool — chiede solo `"name": "get_weather"`. È il device mobile che:

1. Riceve la risposta LLM con la richiesta tool
2. Consulta il **Tool Router** per determinare la località
3. Esegue il tool nel posto appropriato
4. Raccoglie il risultato
5. Lo reinvia all'LLM per continuare la conversazione

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DEVICE MOBILE (Android)                           │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         TOOL ROUTER                                  │   │
│  │                                                                     │   │
│  │   Per ogni tool ricevuto dall'LLM, decide:                          │   │
│  │                                                                     │   │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐    │   │
│  │   │     LOCALE      │  │     REMOTO      │  │    HARDWARE     │    │   │
│  │   │  (sul device)   │  │   (server ext)  │  │     (ESP32)     │    │   │
│  │   └────────┬────────┘  └────────┬────────┘  └────────┬────────┘    │   │
│  │            │                    │                    │              │   │
│  │            ▼                    ▼                    ▼              │   │
│  │   ┌────────────────┐   ┌────────────────┐   ┌────────────────┐     │   │
│  │   │ LocalExecutor  │   │ RemoteExecutor │   │ HardwareExec.  │     │   │
│  │   │                │   │                │   │                │     │   │
│  │   │ • Camera       │   │ HTTP Client    │   │ BLE/WiFi       │     │   │
│  │   │ • Intent       │   │ → API esterne  │   │ → ESP32        │     │   │
│  │   │ • Notifications│   │                │   │                │     │   │
│  │   │ • AudioManager │   │                │   │                │     │   │
│  │   └────────────────┘   └────────────────┘   └────────────────┘     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Tool Router: Implementazione in Layer 3

Il `ToolRouter` è nel **Layer 3 (Integration)** e implementa l'interfaccia `ToolExecutor` definita nel **Layer 2 (Reasoning)**:

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// LAYER 2: Interface (reasoning/tool/ToolExecutor.kt)
// ═══════════════════════════════════════════════════════════════════════════

interface ToolExecutor {
    suspend fun execute(invocation: ToolInvocation): ToolResult
    fun getAvailableTools(): List<ToolDefinition>
}

// ═══════════════════════════════════════════════════════════════════════════
// LAYER 3: Implementation (integration/tool/ToolRouter.kt)
// ═══════════════════════════════════════════════════════════════════════════

class ToolRouter(
    private val localExecutor: LocalToolExecutor,       // Camera, Intents, Notifiche...
    private val remoteExecutor: RemoteToolExecutor,     // Meteo, TV Guide, Web Search...
    private val hardwareExecutor: HardwareToolExecutor?, // ESP32 (nullable se non connesso)
) : ToolExecutor {
    
    private val toolRegistry = mapOf(
        // REMOTE - Servizi esterni (HTTP)
        "get_weather" to ToolLocality.REMOTE,
        "get_tv_guide" to ToolLocality.REMOTE,
        "web_search" to ToolLocality.REMOTE,
        "youtube_search" to ToolLocality.REMOTE,
        "get_news" to ToolLocality.REMOTE,
        
        // LOCAL - Eseguiti sul device mobile
        "take_photo" to ToolLocality.LOCAL,
        "open_browser" to ToolLocality.LOCAL,
        "play_spotify" to ToolLocality.LOCAL,
        "play_youtube" to ToolLocality.LOCAL,
        "set_reminder" to ToolLocality.LOCAL,
        "send_notification" to ToolLocality.LOCAL,
        "control_volume" to ToolLocality.LOCAL,
        
        // HARDWARE - ESP32
        "move_head" to ToolLocality.HARDWARE,
        "set_led" to ToolLocality.HARDWARE,
        "wave_hand" to ToolLocality.HARDWARE,
    )
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val locality = toolRegistry[invocation.name]
            ?: return ToolResult.Error("Tool sconosciuto: ${invocation.name}", recoverable = false)
        
        return when (locality) {
            ToolLocality.LOCAL -> localExecutor.execute(invocation)
            ToolLocality.REMOTE -> remoteExecutor.execute(invocation)
            ToolLocality.HARDWARE -> {
                hardwareExecutor?.execute(invocation)
                    ?: ToolResult.Error("Hardware ESP32 non connesso", recoverable = true)
            }
        }
    }
    
    override fun getAvailableTools(): List<ToolDefinition> {
        return buildList {
            addAll(localExecutor.getToolDefinitions())
            addAll(remoteExecutor.getToolDefinitions())
            hardwareExecutor?.getToolDefinitions()?.let { addAll(it) }
        }
    }
}

enum class ToolLocality {
    LOCAL,      // Eseguito sul device mobile
    REMOTE,     // Chiamata HTTP a servizio esterno
    HARDWARE,   // Comando BLE/WiFi a ESP32
}
```

**Punto chiave**: Il Reasoning Module (Layer 2) vede solo `ToolExecutor`. Non sa che esistono `LOCAL`, `REMOTE`, `HARDWARE`. Questa separazione permette di testare il reasoning con un mock `ToolExecutor`.

### 4.3 Esempio: Catena Mista (Remoto + Locale)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  USER: "Cosa c'è di bello stasera in TV? Se c'è un film aprimi il link"    │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 1:                                                                │
│  {                                                                          │
│    "reply": "Controllo la programmazione...",                               │
│    "action": {                                                              │
│      "type": "tool_call",                                                   │
│      "tools": [                                                             │
│        { "name": "get_tv_guide", "params": {...}, "await_result": true }    │
│      ]                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                         │                                   │
│                        Tool Router: get_tv_guide → REMOTE                   │
│                                         │                                   │
│                                         ▼                                   │
│                        ┌────────────────────────────────┐                   │
│                        │    SERVIZIO ESTERNO (HTTP)     │                   │
│                        │    api.guidatv.it/programs     │                   │
│                        └────────────────────────────────┘                   │
│                                         │                                   │
│                        Response: [{title: "Il Gladiatore", url: "..."}]     │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 2:                                                                │
│  {                                                                          │
│    "reply": "Su Rai 1 c'è Il Gladiatore! Apro il link?",                    │
│    "action": {                                                              │
│      "type": "confirm_required",                                            │
│      "tools": [                                                             │
│        { "name": "open_browser", "params": {"url": "..."} }                 │
│      ],                                                                     │
│      "confirmPrompt": "Apro RaiPlay?"                                       │
│    }                                                                        │
│  }                                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                              USER: "Sì, apri"
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 3:                                                                │
│  {                                                                          │
│    "reply": "Fatto, buona visione!",                                        │
│    "action": {                                                              │
│      "type": "tool_call",                                                   │
│      "tools": [                                                             │
│        { "name": "open_browser", "params": {"url": "..."}, "await": false } │
│      ]                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                         │                                   │
│                        Tool Router: open_browser → LOCAL                    │
│                                         │                                   │
│                                         ▼                                   │
│                        ┌────────────────────────────────┐                   │
│                        │    DEVICE MOBILE (Intent)      │                   │
│                        │    Intent.ACTION_VIEW → Chrome │                   │
│                        └────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Nota**: In questa catena, `get_tv_guide` è REMOTO (HTTP), mentre `open_browser` è LOCALE (Intent Android). L'LLM non conosce questa distinzione — vede solo i nomi dei tool.

---

## 5. Stato Attuale dell'Architettura

### 5.1 Componenti Esistenti

```kotlin
// Domain layer
interface LlmRepository {
    suspend fun ask(prompt: String): Result<LlmAssistantReply>
    suspend fun askWithImage(userPrompt: String, image: CapturedImage): Result<LlmAssistantReply>
}

data class LlmAssistantReply(
    val text: String,
    val emotion: RobotEmotion? = null,
    val imageRequired: Boolean = false,  // ← Proto-tool: richiesta immagine
)
```

### 5.2 Sistema Prompt Attuale

```json
{
  "reply": "testo parlato",
  "emotion": "happy | sad | angry | ...",
  "imageRequired": true | false
}
```

**Osservazione**: `imageRequired` è già un **proto-tool**. Quando è `true`, l'app esegue un'azione locale (scatto foto) e reinvia il risultato all'LLM. Questo pattern può essere generalizzato.

### 5.3 Flusso Vision Esistente

```
User: "Cosa vedi?"
     │
     ▼
LLM: { "reply": "Ok, do un'occhiata", "imageRequired": true }
     │
     ▼
App: esegue take_photo() → CapturedImage
     │
     ▼
LLM: askWithImage(prompt, image) → { "reply": "Vedo una scrivania..." }
     │
     ▼
TTS: parla la risposta
```

Questo è esattamente il pattern **tool call → tool result → LLM continuation** che dobbiamo generalizzare.

---

## 6. Approccio A: Function Calling OpenAI-Style

### 6.1 Descrizione

Utilizzo del meccanismo nativo di **function calling** dell'API OpenAI (supportato da LM Studio per alcuni modelli). L'LLM riceve una lista di funzioni disponibili e può decidere di chiamarne una invece di rispondere direttamente.

### 6.2 Flusso

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Request con tools definition                                 │
├──────────────────────────────────────────────────────────────────┤
│ POST /v1/chat/completions                                        │
│ {                                                                │
│   "model": "...",                                                │
│   "messages": [...],                                             │
│   "tools": [                                                     │
│     {                                                            │
│       "type": "function",                                        │
│       "function": {                                              │
│         "name": "get_weather",                                   │
│         "description": "Get current weather for a city",         │
│         "parameters": {                                          │
│           "type": "object",                                      │
│           "properties": {                                        │
│             "city": { "type": "string" }                         │
│           },                                                     │
│           "required": ["city"]                                   │
│         }                                                        │
│       }                                                          │
│     }                                                            │
│   ]                                                              │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 2. Response con tool_call                                        │
├──────────────────────────────────────────────────────────────────┤
│ {                                                                │
│   "choices": [{                                                  │
│     "message": {                                                 │
│       "role": "assistant",                                       │
│       "tool_calls": [{                                           │
│         "id": "call_abc123",                                     │
│         "type": "function",                                      │
│         "function": {                                            │
│           "name": "get_weather",                                 │
│           "arguments": "{\"city\": \"Milano\"}"                  │
│         }                                                        │
│       }]                                                         │
│     },                                                           │
│     "finish_reason": "tool_calls"                                │
│   }]                                                             │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 3. App esegue il tool, poi continua la conversazione             │
├──────────────────────────────────────────────────────────────────┤
│ POST /v1/chat/completions                                        │
│ {                                                                │
│   "messages": [                                                  │
│     ...previous...,                                              │
│     { "role": "assistant", "tool_calls": [...] },                │
│     {                                                            │
│       "role": "tool",                                            │
│       "tool_call_id": "call_abc123",                             │
│       "content": "{\"temperature\": 22, \"condition\": \"sunny\"}│
│     }                                                            │
│   ]                                                              │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘
```

### 6.3 Implementazione Android

```kotlin
// domain/tool/ToolDefinition.kt
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonSchema,
)

// domain/tool/ToolExecutor.kt
interface ToolExecutor {
    val toolName: String
    suspend fun execute(arguments: JsonObject): ToolResult
}

sealed class ToolResult {
    data class Success(val data: JsonObject) : ToolResult()
    data class Error(val message: String, val recoverable: Boolean) : ToolResult()
    data class NeedsConfirmation(val prompt: String, val onConfirm: suspend () -> ToolResult) : ToolResult()
}

// data/tool/ToolRegistry.kt
class ToolRegistry {
    private val executors = mutableMapOf<String, ToolExecutor>()
    
    fun register(executor: ToolExecutor) {
        executors[executor.toolName] = executor
    }
    
    fun getDefinitions(): List<ToolDefinition> = executors.values.map { it.toDefinition() }
    
    suspend fun execute(name: String, args: JsonObject): ToolResult =
        executors[name]?.execute(args) ?: ToolResult.Error("Unknown tool: $name", false)
}
```

### 6.4 Pro e Contro

| Pro | Contro |
|-----|--------|
| ✅ Standard de-facto, ampia documentazione | ❌ Non tutti i modelli LM Studio supportano function calling |
| ✅ Parsing robusto (JSON schema validation) | ❌ Richiede modelli specifici (GPT-4, Llama 3.1+, Mistral) |
| ✅ Supporto nativo per tool multipli in un turno | ❌ Overhead di token per ogni request (tool definitions) |
| ✅ `finish_reason: "tool_calls"` chiaro | ❌ Complessità API maggiore |
| ✅ Tool call ID per tracciamento | ❌ Alcuni modelli locali hanno supporto parziale |

### 6.5 Rischi

1. **Compatibilità modelli**: Gemma, Phi-3, altri modelli leggeri potrebbero non supportarlo
2. **Dimensione context**: Le definizioni tool consumano token
3. **Debugging**: Più difficile ispezionare cosa sta facendo l'LLM

---

## 7. Approccio B: JSON Strutturato con Tool Actions

### 7.1 Descrizione

Estensione del sistema attuale: l'LLM risponde sempre con JSON strutturato, ma il campo `action` determina cosa deve fare l'app. Simile a come `imageRequired` funziona oggi, ma generalizzato.

### 7.2 Schema Proposto

```json
{
  "reply": "testo da pronunciare (può essere vuoto se action richiede silenzio)",
  "emotion": "happy | sad | angry | surprised | confused | neutral",
  "action": {
    "type": "none | tool_call | multi_tool",
    "tools": [
      {
        "name": "get_weather",
        "params": { "city": "Milano" },
        "await_result": true
      }
    ]
  }
}
```

### 7.3 Tipi di Action

```typescript
// Pseudo-schema per chiarezza

type Action = 
  | { type: "none" }                          // Risposta diretta, nessun tool
  | { type: "tool_call", tools: ToolCall[] }  // Uno o più tool da eseguire
  | { type: "confirm_required", tool: ToolCall, confirmPrompt: string }

type ToolCall = {
  name: string;           // es. "get_weather", "take_photo", "move_head"
  params: object;         // parametri specifici del tool
  await_result: boolean;  // true = LLM vuole vedere il risultato; false = fire-and-forget
}
```

### 7.4 Flusso

```
User: "Che tempo fa a Roma e fammi vedere cosa c'è davanti"
     │
     ▼
LLM: {
  "reply": "Controllo il meteo e scatto una foto...",
  "emotion": "neutral",
  "action": {
    "type": "tool_call",
    "tools": [
      { "name": "get_weather", "params": { "city": "Roma" }, "await_result": true },
      { "name": "take_photo", "params": {}, "await_result": true }
    ]
  }
}
     │
     ▼
App: esegue entrambi i tool in parallelo (o sequenza se dipendenti)
     │
     ▼
App: invia risultati all'LLM
     │
     ▼
LLM: {
  "reply": "A Roma ci sono 25 gradi con sole. Nella foto vedo una scrivania con un monitor.",
  "emotion": "happy",
  "action": { "type": "none" }
}
```

### 7.5 System Prompt Esteso

```text
You are a voice assistant for a desk robot with tools.
ALWAYS respond with a single valid JSON object.

Required format:
{
  "reply": "text to speak (Italian)",
  "emotion": "happy | sad | angry | surprised | confused | neutral",
  "action": {
    "type": "none | tool_call",
    "tools": []
  }
}

AVAILABLE TOOLS:
- get_weather(city: string): Returns temperature and conditions
- take_photo(): Captures image from robot camera, returns image for analysis
- set_reminder(text: string, minutes: int): Sets a reminder
- play_music(query: string): Plays music matching query
- move_head(pan: int[-90,90], tilt: int[-45,45]): Moves robot head
- get_news(category?: string): Returns latest news headlines

RULES:
1. If you need information from a tool, set action.type = "tool_call"
2. If await_result = true, you will receive tool output and must respond again
3. If await_result = false, the action is fire-and-forget
4. For take_photo, always await_result = true (you need to see the image)
5. reply can be a brief acknowledgment when tools are pending
```

### 7.6 Implementazione Android

```kotlin
// domain/model/LlmAssistantReply.kt (esteso)
data class LlmAssistantReply(
    val text: String,
    val emotion: RobotEmotion? = null,
    val action: LlmAction = LlmAction.None,
)

sealed class LlmAction {
    object None : LlmAction()
    
    data class ToolCall(
        val tools: List<ToolInvocation>,
    ) : LlmAction()
    
    data class ConfirmRequired(
        val tool: ToolInvocation,
        val confirmPrompt: String,
    ) : LlmAction()
}

data class ToolInvocation(
    val name: String,
    val params: Map<String, Any?>,
    val awaitResult: Boolean,
)

// domain/tool/Tool.kt
interface Tool<P, R> {
    val name: String
    val locality: ToolLocality
    
    suspend fun execute(params: P): Result<R>
    fun serializeResult(result: R): JsonObject
}

enum class ToolLocality {
    REMOTE,      // Eseguito solo lato server
    LOCAL,       // Eseguito solo su Android
    HYBRID,      // Può essere eseguito in entrambi i modi
    HARDWARE,    // Richiede comunicazione ESP32
}
```

### 7.7 Tool Executor Unificato

```kotlin
class UnifiedToolExecutor(
    private val remoteToolService: RemoteToolService,
    private val localToolRegistry: LocalToolRegistry,
    private val hardwareController: HardwareController?,
) {
    suspend fun execute(invocation: ToolInvocation): ToolExecutionResult {
        val tool = findTool(invocation.name)
            ?: return ToolExecutionResult.UnknownTool(invocation.name)
        
        return when (tool.locality) {
            ToolLocality.REMOTE -> remoteToolService.execute(invocation)
            ToolLocality.LOCAL -> localToolRegistry.execute(invocation)
            ToolLocality.HARDWARE -> {
                hardwareController?.execute(invocation)
                    ?: ToolExecutionResult.HardwareUnavailable
            }
            ToolLocality.HYBRID -> executeHybrid(tool, invocation)
        }
    }
}
```

### 7.8 Pro e Contro

| Pro | Contro |
|-----|--------|
| ✅ Compatibile con QUALSIASI modello (solo JSON) | ❌ Richiede prompt engineering accurato |
| ✅ Evoluzione naturale del sistema attuale | ❌ Nessuna validazione schema nativa |
| ✅ Facile da debuggare (tutto è JSON leggibile) | ❌ Modelli deboli potrebbero generare JSON malformato |
| ✅ Funziona con LM Studio senza requisiti | ❌ Tool definition nel prompt = più token |
| ✅ Controllo totale lato app | ❌ Multi-tool in un turno richiede parsing attento |
| ✅ Retrofit del codice esistente minimo | |

---

## 8. Approccio C: Model Context Protocol (MCP)

### 8.1 Descrizione

MCP è un protocollo open-source di Anthropic per connettere LLM a fonti dati e tool esterni. Definisce un layer di trasporto standardizzato (stdio, HTTP SSE, WebSocket) e un formato messaggi per:

- **Resources**: dati che l'LLM può leggere
- **Tools**: azioni che l'LLM può invocare
- **Prompts**: template riutilizzabili

### 8.2 Architettura MCP

```
┌─────────────────────────────────────────────────────────────────┐
│                         MCP HOST (App Android)                  │
│  ┌─────────────┐                                                │
│  │ MCP Client  │◄────────► LLM API (LM Studio)                  │
│  └──────┬──────┘                                                │
│         │                                                        │
│         │ MCP Protocol (JSON-RPC 2.0)                           │
│         │                                                        │
│  ┌──────▼──────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ MCP Server  │  │ MCP Server   │  │ MCP Server   │            │
│  │ (Weather)   │  │ (Camera)     │  │ (ESP32)      │            │
│  │   :8081     │  │  in-process  │  │   :8083      │            │
│  └─────────────┘  └──────────────┘  └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

### 8.3 MCP Tool Definition

```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": 1
}

// Response
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "get_weather",
        "description": "Get current weather for a location",
        "inputSchema": {
          "type": "object",
          "properties": {
            "city": { "type": "string", "description": "City name" }
          },
          "required": ["city"]
        }
      }
    ]
  },
  "id": 1
}
```

### 8.4 Tool Invocation MCP

```json
// Request
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_weather",
    "arguments": { "city": "Milano" }
  },
  "id": 2
}

// Response
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Temperature: 22°C, Condition: Sunny"
      }
    ]
  },
  "id": 2
}
```

### 8.5 Implementazione Android

```kotlin
// MCP Client implementation
interface McpClient {
    suspend fun listTools(): List<McpToolDefinition>
    suspend fun callTool(name: String, arguments: JsonObject): McpToolResult
}

class McpClientImpl(
    private val transport: McpTransport,
) : McpClient {
    
    override suspend fun listTools(): List<McpToolDefinition> {
        val response = transport.sendRequest(
            method = "tools/list",
            params = null,
        )
        return response.parseToolList()
    }
    
    override suspend fun callTool(name: String, arguments: JsonObject): McpToolResult {
        val response = transport.sendRequest(
            method = "tools/call",
            params = mapOf("name" to name, "arguments" to arguments),
        )
        return response.parseToolResult()
    }
}

// MCP Server per tool locali (in-process)
class LocalMcpServer : McpServer {
    private val tools = mutableMapOf<String, McpToolHandler>()
    
    fun registerTool(handler: McpToolHandler) {
        tools[handler.name] = handler
    }
    
    override suspend fun handleRequest(request: McpRequest): McpResponse {
        return when (request.method) {
            "tools/list" -> McpResponse.success(tools.values.map { it.definition })
            "tools/call" -> {
                val name = request.params["name"] as String
                val args = request.params["arguments"] as JsonObject
                tools[name]?.execute(args) ?: McpResponse.error("Unknown tool")
            }
            else -> McpResponse.error("Unknown method")
        }
    }
}
```

### 8.6 Pro e Contro

| Pro | Contro |
|-----|--------|
| ✅ Standard aperto, community in crescita | ❌ Overhead di un protocollo aggiuntivo |
| ✅ Separazione netta host/server | ❌ Complessità architetturale maggiore |
| ✅ Server riutilizzabili tra progetti | ❌ Pochi SDK Android maturi |
| ✅ Supporto nativo per streaming | ❌ LM Studio non ha integrazione MCP nativa |
| ✅ Resource system per context injection | ❌ Richiede bridge tra MCP e OpenAI API |
| ✅ Ecosistema di server pre-built | ❌ Over-engineering per progetto singolo |

### 8.7 Rischi

1. **Maturità**: MCP è relativamente nuovo (2024), SDK Android non ufficiale
2. **Bridge complexity**: Serve tradurre tra MCP tools e OpenAI function calling o JSON custom
3. **Latenza**: Layer aggiuntivo = ms in più

---

## 9. Analisi Comparativa

### 9.1 Matrice Requisiti vs Approcci

| Requisito | Approccio A (Function Calling) | Approccio B (JSON Strutturato) | Approccio C (MCP) |
|-----------|-------------------------------|-------------------------------|-------------------|
| **R1 - Modularità** | ⭐⭐⭐ Registry pattern | ⭐⭐⭐ Registry pattern | ⭐⭐⭐⭐ Server separation |
| **R2 - Località trasparente** | ⭐⭐⭐ Executor abstraction | ⭐⭐⭐⭐ Unified executor | ⭐⭐⭐ Transport layer |
| **R3 - Composabilità** | ⭐⭐⭐⭐ Native parallel calls | ⭐⭐⭐ Manual orchestration | ⭐⭐⭐ Sequential by design |
| **R4 - Feedback sincrono** | ⭐⭐ Richiede estensione | ⭐⭐⭐⭐ reply + action | ⭐⭐⭐ Notifications |
| **R5 - Graceful degradation** | ⭐⭐⭐⭐ Structured errors | ⭐⭐⭐ Custom error handling | ⭐⭐⭐⭐ JSON-RPC errors |
| **R6 - Sicurezza** | ⭐⭐ Custom layer needed | ⭐⭐⭐ confirm_required action | ⭐⭐⭐ Authorization layer |
| **R7 - Estensibilità HW** | ⭐⭐⭐ New executor | ⭐⭐⭐⭐ HardwareController | ⭐⭐⭐⭐ New MCP server |
| **R8 - Testabilità** | ⭐⭐⭐ Mock executors | ⭐⭐⭐⭐ Mock tutto | ⭐⭐⭐ Mock servers |

### 9.2 Matrice Complessità

| Fattore | A | B | C |
|---------|---|---|---|
| Modifica LlmRepository | Media | Bassa | Alta |
| Modifica System Prompt | Bassa | Media | Media |
| Nuove dipendenze | Nessuna | Nessuna | MCP SDK |
| Curva apprendimento | Media | Bassa | Alta |
| Debugging | Difficile | Facile | Media |
| Compatibilità modelli | Limitata | Totale | Richiede bridge |

### 9.3 Matrice Rischio

| Rischio | A | B | C |
|---------|---|---|---|
| Modello non supporta feature | **ALTO** | Basso | Medio |
| JSON malformato | Basso | Medio | Basso |
| Latenza aggiuntiva | Bassa | Bassa | Media |
| Lock-in tecnologico | Medio (OpenAI) | Nessuno | Basso (open) |
| Manutenzione futura | Media | Bassa | Media |

### 9.4 Effort Stimato

| Fase | A | B | C |
|------|---|---|---|
| Infrastruttura base | 3-4 giorni | 2-3 giorni | 5-7 giorni |
| Primo tool (weather) | 1 giorno | 0.5 giorni | 1-2 giorni |
| Tool camera (esistente) | 0.5 giorni | 0.5 giorni | 1 giorno |
| Tool ESP32 | 2 giorni | 2 giorni | 2-3 giorni |
| Testing completo | 2 giorni | 1-2 giorni | 2-3 giorni |
| **TOTALE** | **8-10 giorni** | **6-8 giorni** | **11-15 giorni** |

---

## 10. Raccomandazione Finale

### 10.1 Scelta: **Approccio B — JSON Strutturato con Tool Actions**

### 10.2 Motivazioni

1. **Compatibilità universale**: Funziona con qualsiasi modello che genera JSON (Gemma, Llama, Mistral, Phi, ecc.). Non dipende da feature specifiche dell'API.

2. **Evoluzione naturale**: Il sistema `imageRequired` esistente è già un proto-tool. Estenderlo a un sistema completo richiede modifiche minime.

3. **Controllo totale**: L'app Android ha pieno controllo su parsing, validazione, esecuzione e error handling. Nessuna black-box.

4. **Debugging semplice**: Tutto il flusso è ispezionabile come JSON. Log chiari, facile riprodurre problemi.

5. **Effort minimo**: 6-8 giorni vs 8-10 o 11-15 degli altri approcci.

6. **Flessibilità futura**: Se in futuro servisse MCP o function calling, il layer di astrazione (Tool interface) permette di migrare senza riscrivere i tool.

### 10.3 Mitigazioni per i Contro

| Contro | Mitigazione |
|--------|-------------|
| Prompt engineering | Template testati + fallback parser robusto |
| No schema validation nativa | Validazione manuale in `LlmResponseParser` |
| JSON malformato | Fallback a risposta testuale (già implementato) |
| Token overhead | Tool descriptions concise, lazy loading nel prompt |

### 10.4 Architettura Target (3 Layer Separati)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║           LAYER 1: ROBOT UI (Android-specific, sostituibile)          ║  │
│  ║                                                                       ║  │
│  ║   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  ║  │
│  ║   │ MainActivity│  │ RobotScreen │  │ RobotEyes   │  │ STT / TTS  │  ║  │
│  ║   │ ViewModel   │  │ (Compose)   │  │ (Compose)   │  │ Android    │  ║  │
│  ║   └──────┬──────┘  └─────────────┘  └─────────────┘  └────────────┘  ║  │
│  ║          │                                                            ║  │
│  ║          │  Chiama ReasoningEngine, riceve risposte                   ║  │
│  ╚══════════╪════════════════════════════════════════════════════════════╝  │
│             │                                                               │
│             │  ReasoningEngine interface                                    │
│             ▼                                                               │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║      LAYER 2: REASONING MODULE (Kotlin puro, riutilizzabile)          ║  │
│  ║                                                                       ║  │
│  ║   ┌─────────────────────────────────────────────────────────────┐    ║  │
│  ║   │               ToolChainOrchestrator                          │    ║  │
│  ║   │                                                              │    ║  │
│  ║   │   • Gestisce conversazione multi-turn                        │    ║  │
│  ║   │   • Parsa JSON responses                                     │    ║  │
│  ║   │   • Decide prossimo step catena                              │    ║  │
│  ║   │   • Applica safeguard                                        │    ║  │
│  ║   └─────────────────────────────────────────────────────────────┘    ║  │
│  ║                                                                       ║  │
│  ║   ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   ║  │
│  ║   │ LlmClient     │  │ ToolExecutor  │  │ LlmResponseParser     │   ║  │
│  ║   │ (interface)   │  │ (interface)   │  │                       │   ║  │
│  ║   └───────┬───────┘  └───────┬───────┘  └───────────────────────┘   ║  │
│  ║           │                  │                                       ║  │
│  ║   ⚠️ NESSUNA dipendenza Android - può girare ovunque                ║  │
│  ╚═══════════╪══════════════════╪═══════════════════════════════════════╝  │
│              │                  │                                          │
│              ▼                  ▼                                          │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║           LAYER 3: EXTERNAL INTEGRATIONS (sostituibili)               ║  │
│  ║                                                                       ║  │
│  ║   ┌─────────────────┐         ┌─────────────────────────────────────┐║  │
│  ║   │  LLM PROVIDERS  │         │          TOOL IMPLEMENTATIONS       │║  │
│  ║   │                 │         │                                     │║  │
│  ║   │ ┌─────────────┐ │         │  ┌───────────┐  ┌───────────┐      │║  │
│  ║   │ │ LmStudio    │ │         │  │  Remote   │  │   Local   │      │║  │
│  ║   │ │ Client      │ │         │  │  Tools    │  │   Tools   │      │║  │
│  ║   │ └─────────────┘ │         │  │           │  │           │      │║  │
│  ║   │ ┌─────────────┐ │         │  │ • Weather │  │ • Camera  │      │║  │
│  ║   │ │ OpenAI      │ │         │  │ • News    │  │ • Intents │      │║  │
│  ║   │ │ Client      │ │         │  │ • YouTube │  │ • Alarms  │      │║  │
│  ║   │ └─────────────┘ │         │  │ • TV Guide│  │ • Volume  │      │║  │
│  ║   │ ┌─────────────┐ │         │  └─────┬─────┘  └─────┬─────┘      │║  │
│  ║   │ │ Claude      │ │         │        │              │            │║  │
│  ║   │ │ Client      │ │         │  ┌─────▼──────────────▼─────┐      │║  │
│  ║   │ └─────────────┘ │         │  │    Hardware Tools        │      │║  │
│  ║   │ ┌─────────────┐ │         │  │    (ESP32 BLE/WiFi)      │      │║  │
│  ║   │ │ Gemini      │ │         │  │    • MoveHead • SetLed   │      │║  │
│  ║   │ │ Client      │ │         │  └──────────────────────────┘      │║  │
│  ║   │ └─────────────┘ │         │                                     │║  │
│  ║   └─────────────────┘         └─────────────────────────────────────┘║  │
│  ╚═══════════════════════════════════════════════════════════════════════╝  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.5 Package Structure Target

```
com.example.mydeskrobot/
│
├── ui/                          # LAYER 1: Robot UI (Android-specific)
│   ├── screen/
│   ├── components/
│   └── theme/
│
├── presentation/                # LAYER 1: ViewModel, UiState
│   └── conversation/
│
├── reasoning/                   # LAYER 2: Reasoning Module (Kotlin puro)
│   ├── ReasoningEngine.kt           # Interfaccia principale
│   ├── ReasoningEngineImpl.kt       # Implementazione
│   ├── ToolChainOrchestrator.kt     # Gestisce catene tool
│   ├── LlmResponseParser.kt         # Parsing JSON
│   ├── ConversationHistory.kt       # Gestione history
│   │
│   ├── llm/                         # LLM Client abstraction
│   │   ├── LlmClient.kt             # Interface
│   │   └── LlmResponse.kt           # Data classes
│   │
│   ├── tool/                        # Tool abstraction
│   │   ├── ToolExecutor.kt          # Interface
│   │   ├── ToolInvocation.kt        # Data classes
│   │   ├── ToolResult.kt
│   │   └── ToolDefinition.kt
│   │
│   └── model/                       # Domain models (Kotlin puro)
│       ├── LlmAction.kt
│       └── ChainStatus.kt
│
├── integration/                 # LAYER 3: External Integrations
│   │
│   ├── llm/                         # LLM Provider implementations
│   │   ├── LmStudioClient.kt
│   │   ├── OpenAiClient.kt
│   │   ├── ClaudeClient.kt
│   │   └── GeminiClient.kt
│   │
│   ├── tool/                        # Tool implementations
│   │   ├── ToolRouter.kt            # Routing locale/remoto/hardware
│   │   │
│   │   ├── remote/                  # HTTP APIs
│   │   │   ├── WeatherTool.kt
│   │   │   ├── NewsTool.kt
│   │   │   └── YouTubeTool.kt
│   │   │
│   │   ├── local/                   # Android APIs
│   │   │   ├── CameraTool.kt
│   │   │   ├── ReminderTool.kt
│   │   │   └── BrowserTool.kt
│   │   │
│   │   └── hardware/                # ESP32 BLE/WiFi
│   │       ├── Esp32Controller.kt
│   │       ├── MoveHeadTool.kt
│   │       └── SetLedTool.kt
│   │
│   └── speech/                      # STT/TTS (Android-specific)
│       ├── AndroidSpeechToText.kt
│       └── AndroidTextToSpeech.kt
│
└── di/                          # Dependency Injection / Factory
    └── AppModule.kt
```

### 10.6 Dipendenze tra Package

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐
│     ui/     │────►│  presentation/  │────►│   reasoning/    │
│  (Compose)  │     │   (ViewModel)   │     │ (Kotlin puro)   │
└─────────────┘     └─────────────────┘     └────────┬────────┘
                                                     │
                                            usa interfacce
                                                     │
                                                     ▼
                                            ┌─────────────────┐
                                            │  integration/   │
                                            │ (implementazioni)│
                                            └─────────────────┘

REGOLA: reasoning/ NON importa MAI da ui/, presentation/, integration/
        reasoning/ definisce INTERFACCE che integration/ implementa
```

---

## 11. Tool Chaining: Catene di Azioni Autonome

Una delle funzionalità più potenti è la capacità dell'LLM di eseguire **catene di azioni** autonomamente, dove ogni step dipende dal risultato del precedente.

### 11.1 Cos'è una Tool Chain

Una **catena** è una sequenza di tool eseguiti in più turni LLM, dove:
- L'LLM riceve il risultato di un tool
- Ragiona sul risultato
- Decide autonomamente il prossimo step
- Continua finché l'obiettivo non è raggiunto

### 11.2 Esempio: "Riproduci la canzone che fa..."

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  USER: "Riproduci la canzone che fa 'nel blu dipinto di blu'"               │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 1:                                                                │
│  {                                                                          │
│    "reply": "Cerco la canzone...",                                          │
│    "emotion": "thinking",                                                   │
│    "action": {                                                              │
│      "type": "tool_call",                                                   │
│      "chain_status": "in_progress",                                         │
│      "tools": [                                                             │
│        { "name": "web_search",                                              │
│          "params": { "query": "canzone nel blu dipinto di blu lyrics" },   │
│          "await_result": true,                                              │
│          "purpose": "find_song_title" }                                     │
│      ]                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                                                             │
│  Tool Router: web_search → REMOTE (HTTP API)                                │
│  Result: "Volare - Domenico Modugno, 1958"                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                   Device mobile invia risultato all'LLM
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 2:  ← L'LLM DECIDE AUTONOMAMENTE IL PROSSIMO STEP                 │
│  {                                                                          │
│    "reply": "Trovata! È 'Volare' di Domenico Modugno. La cerco...",         │
│    "emotion": "happy",                                                      │
│    "action": {                                                              │
│      "type": "tool_call",                                                   │
│      "chain_status": "in_progress",                                         │
│      "tools": [                                                             │
│        { "name": "youtube_search",                                          │
│          "params": { "query": "Volare Domenico Modugno official" },         │
│          "await_result": true,                                              │
│          "purpose": "find_video" }                                          │
│      ]                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                                                             │
│  Tool Router: youtube_search → REMOTE (YouTube Data API)                    │
│  Result: { "video_id": "abc123", "title": "Volare - Domenico Modugno" }     │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                   Device mobile invia risultato all'LLM
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LLM TURN 3:  ← L'LLM DECIDE DI ESEGUIRE L'AZIONE FINALE                    │
│  {                                                                          │
│    "reply": "Ecco 'Volare'!",                                               │
│    "emotion": "happy",                                                      │
│    "action": {                                                              │
│      "type": "tool_call",                                                   │
│      "chain_status": "complete",                                            │
│      "tools": [                                                             │
│        { "name": "play_youtube",                                            │
│          "params": { "video_id": "abc123" },                                │
│          "await_result": false }  ← fire-and-forget                         │
│      ]                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                                                             │
│  Tool Router: play_youtube → LOCAL (Intent.ACTION_VIEW)                     │
│  Device mobile apre YouTube con il video                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                                  ✅ CATENA COMPLETATA
```

### 11.3 Schema Esteso per Tool Chaining

Per supportare catene robuste, lo schema JSON include campi aggiuntivi:

```json
{
  "reply": "testo parlato",
  "emotion": "thinking | happy | ...",
  "action": {
    "type": "none | tool_call | confirm_required",
    
    "tools": [
      {
        "name": "web_search",
        "params": { "query": "..." },
        "await_result": true,
        "purpose": "find_song_title"    // Intent per debug/logging
      }
    ],
    
    "chain_status": "in_progress | complete | failed",
    "max_steps_remaining": 5,            // Auto-limite per evitare loop infiniti
    "parallel": false                    // Se true, esegui tutti i tools insieme
  }
}
```

| Campo | Descrizione |
|-------|-------------|
| `chain_status` | `in_progress` = altri step previsti; `complete` = catena finita; `failed` = errore irrecuperabile |
| `max_steps_remaining` | L'LLM può auto-limitarsi per evitare catene infinite |
| `purpose` | Descrizione intent del tool (utile per logging/debug) |
| `parallel` | Se `true`, i tool nell'array vengono eseguiti in parallelo |

### 11.4 Tool Chain Orchestrator

Il **Reasoning Module** (Layer 2) implementa l'orchestratore. È Kotlin puro, senza dipendenze Android:

```kotlin
// reasoning/ToolChainOrchestrator.kt
// ⚠️ NESSUNA dipendenza Android - riutilizzabile in qualsiasi contesto

class ToolChainOrchestrator(
    private val llmClient: LlmClient,           // Interfaccia, non implementazione
    private val toolExecutor: ToolExecutor,     // Interfaccia, non implementazione
    private val responseParser: LlmResponseParser,
    private val systemPrompt: String,
    private val maxChainSteps: Int = 10,
) {
    suspend fun executeWithTools(
        userPrompt: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        
        val conversationHistory = ConversationHistory()
        conversationHistory.addUserMessage(userPrompt)
        
        var step = 0
        while (step < maxChainSteps) {
            step++
            
            // 1. Chiedi all'LLM
            val llmResult = llmClient.chat(
                messages = conversationHistory.toMessages(),
                systemPrompt = systemPrompt,
            )
            
            if (llmResult.isFailure) {
                return ReasoningResult.Error(llmResult.exceptionOrNull()?.message ?: "LLM error")
            }
            
            val parsed = responseParser.parse(llmResult.getOrThrow().content)
            conversationHistory.addAssistantMessage(parsed)
            
            // 2. Notifica risposta intermedia (il chiamante decide cosa farne: TTS, UI, ecc.)
            if (parsed.text.isNotBlank()) {
                onIntermediateResponse(IntermediateResponse(
                    text = parsed.text,
                    emotion = parsed.emotion?.name,
                    isToolExecuting = false,
                ))
            }
            
            // 3. Controlla l'action
            when (val action = parsed.action) {
                is LlmAction.None -> {
                    return ReasoningResult.Success(parsed.text, parsed.emotion?.name)
                }
                
                is LlmAction.ToolCall -> {
                    // Notifica che stiamo eseguendo tool
                    action.tools.forEach { tool ->
                        onIntermediateResponse(IntermediateResponse(
                            text = "",
                            emotion = null,
                            isToolExecuting = true,
                            toolName = tool.name,
                        ))
                    }
                    
                    // Esegui i tool (parallelo o sequenziale)
                    val results = if (action.parallel) {
                        executeParallel(action.tools)
                    } else {
                        executeSequential(action.tools)
                    }
                    
                    // Aggiungi risultati alla history
                    results.forEach { (tool, result) ->
                        conversationHistory.addToolResult(tool.name, result)
                    }
                    
                    // Controlla condizioni di terminazione
                    if (action.tools.none { it.awaitResult }) {
                        return ReasoningResult.Success(parsed.text, parsed.emotion?.name)
                    }
                    if (action.chainStatus == ChainStatus.COMPLETE) {
                        return ReasoningResult.Success(parsed.text, parsed.emotion?.name)
                    }
                    
                    // Continua il loop
                }
                
                is LlmAction.ConfirmRequired -> {
                    return ReasoningResult.NeedsConfirmation(
                        prompt = action.confirmPrompt,
                        pendingAction = { confirmed ->
                            if (confirmed) {
                                // Riprendi la catena
                                conversationHistory.addUserMessage("Sì, procedi")
                                executeWithTools("", onIntermediateResponse) // Continua
                            } else {
                                ReasoningResult.Success("Ok, annullato.", "neutral")
                            }
                        }
                    )
                }
            }
        }
        
        return ReasoningResult.Error("Raggiunto limite massimo step ($maxChainSteps)")
    }
    
    private suspend fun executeParallel(
        tools: List<ToolInvocation>,
    ): List<Pair<ToolInvocation, ToolResult>> {
        return coroutineScope {
            tools.map { tool ->
                async { tool to toolExecutor.execute(tool) }
            }.awaitAll()
        }
    }
    
    private suspend fun executeSequential(
        tools: List<ToolInvocation>,
    ): List<Pair<ToolInvocation, ToolResult>> {
        return tools.map { tool -> tool to toolExecutor.execute(tool) }
    }
}
```

**Punto chiave**: `ToolChainOrchestrator` non sa nulla di Android, TTS, UI. Comunica tramite interfacce (`LlmClient`, `ToolExecutor`) e callback (`onIntermediateResponse`). Il chiamante (Robot UI) decide come gestire le risposte.
```

### 11.5 Conversation History per LLM

Per permettere catene multi-turn, il device mobile mantiene la **history** della conversazione e la invia all'LLM ad ogni turno:

```kotlin
// Messaggio utente iniziale
{ "role": "user", "content": "Riproduci la canzone che fa nel blu dipinto di blu" }

// Risposta LLM con richiesta tool
{ "role": "assistant", "content": "{\"reply\":\"Cerco...\",\"action\":{...}}" }

// Risultato tool (iniettato dal device mobile)
{ "role": "user", "content": "[TOOL_RESULT: web_search]\n{\"title\":\"Volare\",\"artist\":\"Modugno\"}" }

// LLM continua con il prossimo step
{ "role": "assistant", "content": "{\"reply\":\"Trovata!\",\"action\":{...}}" }

// ... e così via
```

### 11.6 Limiti e Safeguard

| Safeguard | Descrizione |
|-----------|-------------|
| `maxChainSteps` | Limite massimo di step per evitare loop infiniti (default: 10) |
| `max_steps_remaining` | L'LLM può auto-limitarsi nel JSON |
| Timeout globale | Timeout massimo per l'intera catena (es. 60s) |
| Context window | Se la history supera il limite token, tronca i messaggi più vecchi |
| Tool failure | Se un tool fallisce, l'LLM riceve l'errore e può decidere se continuare o abortire |

### 11.7 Catene Miste: Esempio Completo

```
USER: "Guarda cosa c'è davanti a me, se vedi un libro cercalo su Amazon"

STEP 1 - LOCAL: take_photo()
         ↓ Device mobile scatta foto con CameraX
         ↓ LLM riceve immagine

STEP 2 - LLM analizza l'immagine
         ↓ "Vedo un libro: 'Clean Code' di Robert Martin"

STEP 3 - REMOTE: web_search("Clean Code Robert Martin Amazon")
         ↓ Servizio esterno restituisce link Amazon
         ↓ LLM riceve risultato

STEP 4 - LLM decide di mostrare il link
         ↓ Chiede conferma: "Vuoi che apra Amazon?"

STEP 5 - USER: "Sì"

STEP 6 - LOCAL: open_browser(url: "https://amazon.it/...")
         ↓ Device mobile apre Chrome con il link
         ↓ chain_status: "complete"

✅ CATENA COMPLETATA (6 step, 3 tool, misti locale/remoto)
```

---

## 12. Piano di Implementazione

### Fase 1: Struttura Package e Interfacce (1-2 giorni)

```
□ 1.1 Creare package reasoning/ (Kotlin puro, no Android)
□ 1.2 Creare package integration/ per implementazioni
□ 1.3 Definire interfaccia LlmClient
□ 1.4 Definire interfaccia ToolExecutor
□ 1.5 Definire interfaccia ReasoningEngine
□ 1.6 Definire data classes in reasoning/model/
```

### Fase 2: Reasoning Module Core (2-3 giorni)

```
□ 2.1 Implementare LlmResponseParser (parsing JSON con fallback)
□ 2.2 Implementare ConversationHistory (gestione messaggi)
□ 2.3 Implementare ToolChainOrchestrator (loop multi-turn)
□ 2.4 Implementare ReasoningEngineImpl
□ 2.5 Unit test per tutto il modulo (senza Android)
```

### Fase 3: LLM Client Implementations (1-2 giorni)

```
□ 3.1 Implementare LmStudioClient (compatibile OpenAI API)
□ 3.2 Predisporre struttura per OpenAiClient, ClaudeClient, GeminiClient
□ 3.3 Configurare LLM provider in local.properties
□ 3.4 Test connessione con LM Studio
```

### Fase 4: Tool Router e Primo Tool (1-2 giorni)

```
□ 4.1 Implementare ToolRouter (routing locale/remoto/hardware)
□ 4.2 Implementare WeatherTool (primo tool remoto)
□ 4.3 Aggiornare system prompt con tool definition
□ 4.4 Test end-to-end: "che tempo fa a Milano?"
```

### Fase 5: Refactor Vision come Tool (1 giorno)

```
□ 5.1 Creare CameraTool che wrappa VisionImageCapture
□ 5.2 Migrare imageRequired → action.tools["take_photo"]
□ 5.3 Aggiornare system prompt
□ 5.4 Test catena: "cosa vedi?" → foto → risposta
```

### Fase 6: Tool Locali Android (2 giorni)

```
□ 6.1 ReminderTool (AlarmManager)
□ 6.2 BrowserTool (Intent.ACTION_VIEW)
□ 6.3 MusicTool (Intent a Spotify/Music app)
□ 6.4 VolumeTool (AudioManager)
□ 6.5 NotificationTool (NotificationManager)
```

### Fase 7: Integrazione Robot UI (1 giorno)

```
□ 7.1 Refactor ConversationViewModel per usare ReasoningEngine
□ 7.2 Gestire IntermediateResponse per TTS e UI
□ 7.3 Gestire NeedsConfirmation con dialog/voice
□ 7.4 Test end-to-end con catene complesse
```

### Fase 8: Hardware ESP32 myDeskBody (HTTP LAN) — implementato

```
✓ 8.1 BodyApiClient (OkHttp) + BodySettingsRepository
✓ 8.2 Tool HARDWARE: move_body_joint, move_body_joints, body_home, body_status
✓ 8.3 UI Impostazioni: URL, prova connessione, test movimento
✓ 8.4 Protocollo HTTP REST myDeskBody (GET /status, POST /joint/*, /home, /test)
```

Dettagli: `docs/BODY_INTEGRATION.md`. BLE generico resta fuori scope v1.

### Fase 9: Polish e Documentazione (1 giorno)

```
□ 9.1 Error handling robusto con retry
□ 9.2 Logging strutturato per debug
□ 9.3 Aggiornare AGENTS.md e cursor rules
□ 9.4 Documentare come aggiungere nuovi tool
□ 9.5 Documentare come cambiare LLM provider
```

### Effort Totale Stimato: 10-14 giorni

---

## 13. Appendice: Schemi JSON e Contratti

### A.1 LLM Response Schema (Esteso per Tool Chaining)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["reply", "action"],
  "properties": {
    "reply": {
      "type": "string",
      "description": "Text to be spoken by TTS (Italian)"
    },
    "emotion": {
      "type": "string",
      "enum": ["happy", "sad", "angry", "surprised", "confused", "neutral", "bored", "thinking"],
      "description": "Robot facial expression"
    },
    "action": {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": {
          "type": "string",
          "enum": ["none", "tool_call", "confirm_required"]
        },
        "tools": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "params"],
            "properties": {
              "name": { "type": "string" },
              "params": { "type": "object" },
              "await_result": { "type": "boolean", "default": true },
              "purpose": { 
                "type": "string", 
                "description": "Intent/reason for this tool call (for logging/debug)" 
              }
            }
          }
        },
        "chain_status": {
          "type": "string",
          "enum": ["in_progress", "complete", "failed"],
          "description": "Status of the tool chain execution"
        },
        "max_steps_remaining": {
          "type": "integer",
          "minimum": 0,
          "description": "Self-imposed limit on remaining chain steps"
        },
        "parallel": {
          "type": "boolean",
          "default": false,
          "description": "If true, execute all tools in parallel"
        },
        "confirmPrompt": {
          "type": "string",
          "description": "Question to ask user before executing tool"
        }
      }
    }
  }
}
```

### A.2 Tool Result Schema

```json
{
  "success": true,
  "data": {
    "temperature": 22,
    "condition": "sunny",
    "humidity": 65
  }
}

// or

{
  "success": false,
  "error": {
    "code": "NETWORK_ERROR",
    "message": "Unable to reach weather service",
    "recoverable": true
  }
}
```

### A.3 Tool Definition Template (per system prompt)

```text
TOOL: get_weather
DESCRIPTION: Get current weather for a city
PARAMS:
  - city (string, required): City name
RETURNS: temperature (int), condition (string), humidity (int)
EXAMPLE: {"name": "get_weather", "params": {"city": "Roma"}, "await_result": true}
```

### A.4 Conversazione Multi-Turn con Tool

```
USER: "Che tempo fa a Roma e metti un promemoria tra 10 minuti per uscire"

LLM (turn 1):
{
  "reply": "Controllo il meteo e imposto il promemoria...",
  "emotion": "thinking",
  "action": {
    "type": "tool_call",
    "tools": [
      {"name": "get_weather", "params": {"city": "Roma"}, "await_result": true},
      {"name": "set_reminder", "params": {"text": "Uscire", "minutes": 10}, "await_result": false}
    ]
  }
}

APP: esegue entrambi, invia risultato weather

LLM (turn 2):
{
  "reply": "A Roma ci sono 25 gradi con cielo sereno. Ho impostato il promemoria per tra 10 minuti.",
  "emotion": "happy",
  "action": {"type": "none"}
}
```

---

## Prossimi Passi

1. ✅ **Approvazione**: Approccio B approvato
2. ⬜ **Fase 1**: Implementare infrastruttura base (LlmAction, ToolRouter, ToolChainOrchestrator)
3. ⬜ **Refactor Vision**: Migrare `imageRequired` al nuovo sistema tool
4. ⬜ **Primo tool remoto**: Implementare `get_weather` come proof-of-concept
5. ⬜ **API esterne**: Scegliere provider per weather, news, TV guide, ecc.
6. ⬜ **Protocollo ESP32**: Definire formato comandi BLE/WiFi

---

*Documento generato per My Desk Robot — Tool Architecture Design*  
*Versione 1.1 — Aggiornato con Tool Chaining e architettura di esecuzione*
