# LM Studio + TTS Android

## LM Studio sul PC

1. Scarica e avvia [LM Studio](https://lmstudio.ai/).
2. Carica un modello (tab **Chat** o **Models**).
3. Vai su **Developers** → **Local Server** → **Start server** (porta default `1234`).
4. Copia il **model identifier** mostrato dal server (es. `qwen2.5-7b-instruct`).

## `local.properties`

```properties
# Emulatore (10.0.2.2 = localhost del PC)
LLM_BASE_URL=http://10.0.2.2:1234/v1/

# Dispositivo fisico: IP LAN del PC
# LLM_BASE_URL=http://192.168.1.50:1234/v1/

LLM_MODEL=nome-modello-da-lm-studio
# Opzionale: modello vision (Gemini, ecc.). Se vuoto, usa LLM_MODEL.
LLM_VISION_MODEL=nome-modello-vision
LLM_API_KEY=
```

`LLM_API_KEY` è opzionale (solo se l’hai abilitata nel server LM Studio).

## System prompt

Modifica il file testo (leggibile, senza escape XML):

`app/src/main/assets/prompts/llm_system_prompt.txt`

Dopo ogni modifica: ricompila e reinstalla l’app (gli asset sono inclusi nell’APK).

Il modello deve rispondere con **JSON** (non testo libero):

```json
{
  "reply": "testo pronunciato dal robot",
  "emotion": "happy",
  "imageRequired": false
}
```

- `reply`: testo letto dal TTS (se `imageRequired` è true, solo un breve ack prima dello scatto).
- `emotion` (opzionale): `happy` | `sad` | `angry` | `surprised` | `confused` | `neutral`.
- `imageRequired` (opzionale): se `true`, l’app scatta una foto e invia un secondo turno al modello vision.

Se il modello non rispetta il JSON, l’app usa l’intera risposta come testo (fallback).

Dettaglio visione: [VISION.md](VISION.md).

## Flusso app

1. Frase utente (dopo pausa 5 s) → **LM Studio** (`POST /v1/chat/completions` con system + user).
2. Se `imageRequired: true` → TTS ack → foto → seconda chiamata multimodale (modello `LLM_VISION_MODEL`).
3. Parsing JSON → cronologia + emozione occhi.
4. **TextToSpeech** legge il `reply` finale.
5. Durante il TTS l’ascolto hotword è in pausa (anti-eco).

## Risoluzione problemi

- **Connection refused**: server LM Studio non avviato o IP/porta errati.
- **Model not found**: `LLM_MODEL` deve coincidere con il modello caricato nel server.
- **Emulatore vs device**: non usare `localhost` sul telefono; usa l’IP del PC.
