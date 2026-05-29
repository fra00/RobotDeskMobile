# My Desk Robot — Istruzioni per agenti AI

App Android (Kotlin + Jetpack Compose) per dialogo vocale con LLM e interfaccia a **due occhi animati**.

## Funzionalità

1. Speech-to-Text (input vocale)
2. Invio domanda a LLM e ricezione risposta
3. Text-to-Speech (lettura risposta)
4. UI occhi con emozioni animate (neutral, happy, angry, bored, …)
5. **Tool Architecture**: esecuzione di tool locali e remoti orchestrati dal LLM
6. **Input Architecture**: input esterni paralleli alla voce (notifiche, sensori, hardware)

## Architettura a 3 Layer

```
Layer 1 (Robot UI)     → Android-specific: STT, TTS, UI, ViewModel
Layer 2 (Reasoning)    → Kotlin puro, riutilizzabile: reasoning/, tool chain
Layer 3 (Integrations) → LLM providers, tool implementations: integration/
```

Per dettagli vedere `docs/TOOL_ARCHITECTURE.md`.

### Package Structure

```
com.example.mydeskrobot/
├── reasoning/           # Layer 2 - Kotlin puro, NO Android
│   ├── ReasoningEngine.kt
│   ├── ToolChainOrchestrator.kt
│   ├── llm/LlmClient.kt
│   ├── tool/ToolExecutor.kt
│   └── model/           # LlmAction, ToolInvocation, etc.
│
├── integration/         # Layer 3 - Implementations
│   ├── llm/LmStudioClient.kt
│   └── tool/
│       ├── ToolRouter.kt
│       ├── local/       # BrowserTool, CameraTool, etc.
│       └── remote/      # WeatherTool, etc.
│
└── presentation/        # Layer 1 - Android UI
```

## Regole Cursor

Le linee guida dettagliate sono in **`.cursor/rules/*.mdc`**:

| File | Contenuto |
|------|-----------|
| `00-project-overview.mdc` | Visione, vincoli globali (sempre attiva) |
| `10-architecture.mdc` | MVVM, layer, package structure (sempre attiva) |
| `20-kotlin-standards.mdc` | Stile Kotlin, coroutine, naming |
| `30-jetpack-compose.mdc` | Compose, Material 3, schermata |
| `40-speech-llm-tts.mdc` | STT, LLM, TTS, permessi |
| `50-robot-eyes-ui.mdc` | Occhi, emozioni, animazioni |
| `60-security-and-config.mdc` | API key, privacy |
| `70-testing-and-quality.mdc` | Test e checklist |

## Prima di implementare

1. Leggere `00` e `10`.
2. Per feature vocali/LLM → `40`; per occhi → `50`.
3. Per tool → `docs/TOOL_ARCHITECTURE.md`.
4. Per input esterni → `docs/INPUT_ARCHITECTURE.md`.
5. Per contesto robot / silenzio notifiche → `docs/ROBOT_CONTEXT.md`.
5. Scope minimo: una capability per volta.
6. Non committare segreti; usare `local.properties.example`.

## Tool disponibili

| Tool | Località | Descrizione |
|------|----------|-------------|
| `take_photo` | LOCAL | Scatta foto con la camera |
| `get_weather` | REMOTE | Meteo da OpenWeatherMap |
| `web_search` | REMOTE | Ricerca web via SearXNG JSON (config: `SEARX_BASE_URL`) |
| `fetch_url` | REMOTE | Legge pagina web come testo (OkHttp + Jsoup) |
| `open_browser` | LOCAL | Apre URL nel browser |
| `play_spotify` | LOCAL | Apre Spotify con ricerca (artista, genere, musica) |
| `set_robot_context` | LOCAL | Contesto robot (lavoro/call/riunione) e silenzio notifiche robot-only |
| `set_reminder` | LOCAL | Imposta promemoria/sveglia |
| `set_volume` | LOCAL | Controlla volume media |
| `show_notification` | LOCAL | Mostra notifica di sistema |

## Input esterni disponibili

| Input | Priorità | Descrizione |
|-------|----------|-------------|
| `notification` | DEFERRED | Notifiche da app (WhatsApp, SMS, etc.) |
| `hardware_button` | BLOCKING | Pulsante fisico ESP32 (futuro) |
| `sensor_reading` | DEFERRED | Sensori ambientali (futuro) |

Gli input esterni vengono elaborati dal LLM quando il microfono è attivo. Dettagli: `docs/INPUT_ARCHITECTURE.md`.

## Configurazione LLM (runtime)

- **Impostazioni** (ingranaggio in basso a sinistra) → LLM
- Provider: **LM Studio** (locale) o **Gemini** (Google AI)
- Persistenza: DataStore + EncryptedSharedPreferences per API key
- Dettagli: `docs/LLM_SETTINGS.md`

## Stack

- `minSdk` 24, `compileSdk` 35, Compose BOM, Material 3
- Package attuale: `com.example.mydeskrobot`
- LLM: `LlmClient` → `LmStudioClient` | `GeminiClient` (factory da settings)
