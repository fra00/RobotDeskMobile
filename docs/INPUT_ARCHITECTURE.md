# Input Architecture — My Desk Robot

> Sistema di input esterni parallelo alla voce  
> Versione: 1.0  
> Data: 2026-05-28

---

## Indice

1. [Panoramica](#1-panoramica)
2. [Tassonomia degli Input](#2-tassonomia-degli-input)
3. [Architettura](#3-architettura)
4. [Priorità: Blocking vs Deferred](#4-priorità-blocking-vs-deferred)
5. [Policy di Elaborazione](#5-policy-di-elaborazione)
6. [Notifiche Android](#6-notifiche-android)
7. [Come Aggiungere una Nuova Sorgente](#7-come-aggiungere-una-nuova-sorgente)
8. [Privacy e Sicurezza](#8-privacy-e-sicurezza)
9. [Estensioni Future](#9-estensioni-future)

---

## 1. Panoramica

Il robot può ricevere input da diverse fonti oltre alla voce:

- **Notifiche di sistema** (WhatsApp, SMS, email, calendario)
- **Task schedulati** (promemoria utente a scadenza — vedi `docs/SCHEDULED_TASKS.md`)
- **Pulsanti hardware** (ESP32 futuro)
- **Sensori ambientali** (temperatura, luminosità — futuro)

Questi input vengono elaborati dal `ReasoningEngine` allo stesso modo delle frasi vocali, permettendo al LLM di decidere come rispondere o se usare tool.

### Voce dopo una notifica (standby)

Se il microfono è in **standby** (nessuna sessione vocale attiva), dopo l’annuncio TTS della notifica il robot **apre automaticamente** una sessione vocale: puoi rispondere a voce senza ripetere la hot word. Se eri già in conversazione attiva, la sessione continua come prima.

### Differenza Input vs Tool

| | **Input** (ingresso) | **Tool** (uscita) |
|---|---|---|
| Chi innesca | Sistema / hardware | LLM decide |
| Direzione | Verso il robot | Dal robot verso il mondo |
| Esempio | "Hai un messaggio WhatsApp" | `open_browser`, `set_reminder` |

---

## 2. Tassonomia degli Input

### 2.1 RobotInput

Sealed class in `reasoning/model/RobotInput.kt`:

```kotlin
sealed class RobotInput {
    abstract val sourceId: String
    abstract val timestamp: Long
    abstract val priority: InputPriority

    data class Notification(...) : RobotInput()
    data class HardwareButton(...) : RobotInput()
    data class SensorReading(...) : RobotInput()
}
```

### 2.2 InputPriority

```kotlin
enum class InputPriority {
    BLOCKING,   // Elaborazione immediata (es. pulsante hardware)
    DEFERRED,   // Elaborazione quando il robot è libero (es. notifiche)
}
```

---

## 3. Architettura

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Layer 3 - Integration                        │
│  ┌─────────────────────┐  ┌─────────────────────┐                   │
│  │ NotificationListener│  │ HardwareInputSource │ (futuro)          │
│  │       Service       │  │      (ESP32)        │                   │
│  └──────────┬──────────┘  └──────────┬──────────┘                   │
│             │                        │                              │
│             └────────────┬───────────┘                              │
│                          ▼                                          │
│              ┌───────────────────────┐                              │
│              │  NotificationInput    │                              │
│              │      Source           │                              │
│              └───────────┬───────────┘                              │
└──────────────────────────┼──────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Layer 1 - Presentation                        │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │                    SystemInputDispatcher                      │   │
│  │                    (SharedFlow bus)                           │   │
│  └───────────────────────────┬───────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │                    InputPolicyEngine                          │   │
│  │  - canProcessNow(priority, uiState)                           │   │
│  │  - shouldSuppressForNightMode()                               │   │
│  └───────────────────────────┬───────────────────────────────────┘   │
│                              │                                       │
│              ┌───────────────┴───────────────┐                       │
│              │                               │                       │
│              ▼                               ▼                       │
│  ┌─────────────────────┐      ┌──────────────────────┐               │
│  │   Process Now       │      │  DeferredInputQueue  │               │
│  │  (if BLOCKING or    │      │  (if DEFERRED and    │               │
│  │   idle)             │      │   robot busy)        │               │
│  └──────────┬──────────┘      └──────────┬───────────┘               │
│             │                            │                           │
│             └────────────┬───────────────┘                           │
│                          │ drain on idle                             │
│                          ▼                                           │
│              ┌───────────────────────┐                               │
│              │  ConversationViewModel │                              │
│              │  sendSystemInputToLlm()│                              │
│              └───────────┬───────────┘                               │
└──────────────────────────┼───────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Layer 2 - Reasoning                           │
│              ┌───────────────────────┐                               │
│              │    ReasoningEngine    │                               │
│              │  processSystemInput() │                               │
│              └───────────┬───────────┘                               │
│                          │                                           │
│                          ▼                                           │
│              ┌───────────────────────┐                               │
│              │ ToolChainOrchestrator │                               │
│              │  + LLM + Tools        │                               │
│              └───────────────────────┘                               │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. Priorità: Blocking vs Deferred

### BLOCKING

- **Quando**: Elaborazione immediata, anche se il robot sta parlando
- **Uso**: Pulsanti hardware, comandi di emergenza
- **Comportamento**: Può interrompere TTS o accodarsi con priorità massima

### DEFERRED

- **Quando**: Solo quando il robot è libero (non Thinking, non Speaking, non CapturingImage)
- **Uso**: Notifiche, letture sensori periodiche
- **Coda**: `DeferredInputQueue` con dedup e TTL (5 minuti)

---

## 5. Policy di Elaborazione

### Gate principale: Microfono attivo

```kotlin
if (!uiState.isHotwordListeningActive) {
    // Drop input - mic is off
    return
}
```

Include sia `WaitingForHotword` (standby) che `ActiveListening`.

### Controllo fase robot

```kotlin
fun canProcessNow(priority, uiState): Boolean {
    if (!uiState.isHotwordListeningActive) return false
    if (priority == BLOCKING) return true
    return !isAssistantTurnInProgress(uiState.phase)
}
```

### Modalità notte

```kotlin
if (uiState.isNightMode && priority == DEFERRED) {
    // Soppresso - modalità notte
    return
}
```

---

## 6. Notifiche Android

### 6.1 Permessi

L'utente deve abilitare manualmente l'accesso alle notifiche:
`Impostazioni → App → My Desk Robot → Accesso alle notifiche`

### 6.2 Configurazione Settings

- **Master switch**: abilita/disabilita lettura notifiche
- **Whitelist app**: solo app selezionate (WhatsApp, Telegram, SMS, Gmail, Calendario)
- **Deep link**: pulsante per aprire direttamente le impostazioni di sistema

### 6.3 Filtri

| Filtro | Descrizione |
|--------|-------------|
| Blacklist sistema | `android`, `systemui`, `gms`, etc. |
| Whitelist utente | Solo app abilitate in Settings |
| Contenuto vuoto | Notifiche senza testo ignorate |
| Gruppi summary | `FLAG_GROUP_SUMMARY` ignorato |
| Dedup temporale | Stessa notifica entro 60s ignorata |

### 6.4 Sanitizzazione privacy

Parole chiave sensibili vengono nascoste:
- OTP, codice, verifica, password, PIN
- Banca, bank, carta, credit, debit

Il testo viene sostituito con `[contenuto sensibile nascosto]`.

### 6.5 Formato per LLM

```
[SYSTEM_INPUT: notification]
App: WhatsApp
Titolo: Mario
Testo: Ci vediamo alle 20?
```

---

## 7. Come Aggiungere una Nuova Sorgente

### 7.1 Implementare InputSource

Creare `integration/input/mysource/MyInputSource.kt`:

```kotlin
class MyInputSource : InputSource {
    override val id: String = "my_source"
    override val priority: InputPriority = InputPriority.DEFERRED
    override val displayName: String = "La mia sorgente"

    override fun isEnabled(): Boolean { ... }
    override fun normalize(raw: Any): RobotInput? { ... }
    override fun shouldAccept(input: RobotInput): Boolean { ... }
    override fun toEnvelope(input: RobotInput): SystemInputEnvelope { ... }
    override fun toDedupKey(input: RobotInput): String { ... }
}
```

### 7.2 Aggiungere tipo RobotInput

In `reasoning/model/RobotInput.kt`:

```kotlin
data class MyCustomInput(
    val field1: String,
    override val timestamp: Long = System.currentTimeMillis(),
) : RobotInput() {
    override val sourceId: String = "my_source"
    override val priority: InputPriority = InputPriority.DEFERRED
}
```

### 7.3 Aggiornare SystemInputEnvelope

In `reasoning/model/SystemInputEnvelope.kt`, aggiungere builder e case nel `from()`.

### 7.4 Aggiornare prompt LLM (se necessario)

In `llm_system_prompt.txt`, aggiungere sezione `SYSTEM_INPUT` per il nuovo tipo.

### 7.5 Emettere eventi

Dalla sorgente, chiamare:

```kotlin
SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
```

---

## 8. Privacy e Sicurezza

### Consensi richiesti

| Sorgente | Permesso |
|----------|----------|
| Notifiche | `BIND_NOTIFICATION_LISTENER_SERVICE` (consenso manuale) |
| Sensori | `BODY_SENSORS` (se sensori biometrici) |
| Hardware | N/A (comunicazione locale) |

### Dati non loggati

- Contenuto completo notifiche in release build
- OTP e codici temporanei
- Dati bancari

### Controllo utente

- Master switch per ogni sorgente
- Whitelist granulare per notifiche
- Possibilità di disabilitare senza disinstallare

---

## 9. Estensioni Future

### 9.1 Pulsante hardware (ESP32)

```kotlin
data class HardwareButton(
    val buttonId: String,
    val action: String,  // "press", "long_press", "double_press"
    val payload: Map<String, Any?>,
)
```

Priorità: `BLOCKING` per azioni immediate.

### 9.2 Sensori ambientali

```kotlin
data class SensorReading(
    val sensorType: String,  // "temperature", "light", "humidity"
    val value: Double,
    val unit: String,
    val thresholdCrossed: Boolean,
)
```

Priorità: `DEFERRED`, solo se soglia superata.

### 9.3 Batch notifiche

Aggregare più notifiche WhatsApp in un unico messaggio:
"Hai 3 messaggi da Mario"

### 9.4 Tool get_recent_notifications

Permettere al LLM di interrogare lo storico notifiche su richiesta vocale.
