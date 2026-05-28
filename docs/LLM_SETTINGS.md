# Configurazione LLM dall'app

## Panoramica

L'app supporta due provider LLM configurabili dalle **Impostazioni** (icona ingranaggio in basso a sinistra):

| Provider | Uso tipico |
|----------|------------|
| **LM Studio** | Server locale OpenAI-compatible (default dev) |
| **Gemini** | Google AI API (cloud) |

## LM Studio

1. Avvia LM Studio e carica un modello (es. Gemma 4 E4B).
2. Avvia il server locale (Developers → Start server).
3. Nell'app: Impostazioni → LLM → LM Studio.
4. Imposta:
   - **Base URL**: `http://10.0.2.2:1234/v1/` (emulatore) o `http://IP_PC:1234/v1/` (device reale)
   - **Modello testo**: identificatore modello in LM Studio
   - **Modello visione** (opzionale): modello multimodale se diverso

## Gemini

1. Ottieni una API key da [Google AI Studio](https://aistudio.google.com/apikey).
2. Nell'app: Impostazioni → LLM → Gemini.
3. Imposta:
   - **API key**: la chiave Google AI
   - **Modello testo**: es. `gemini-2.0-flash` o `gemini-2.5-flash-preview`
   - **Modello visione** (opzionale): stesso o modello dedicato

4. Usa **Test connessione** per verificare prima di attivare il microfono.

## Sicurezza

- L'API key è salvata in **EncryptedSharedPreferences**.
- Gli altri campi sono in DataStore.
- `local.properties` resta utile come valori iniziali al primo avvio (seed), poi prevale la configurazione in app.

## Cambio provider a runtime

- Se il microfono è **spento**, le modifiche si applicano subito.
- Se c'è un turno assistente in corso, la configurazione viene salvata e applicata al prossimo ciclo di ascolto.

## Sviluppo

Valori seed da `local.properties` (vedi `local.properties.example`). Dopo il primo avvio, configurare dall'UI.
