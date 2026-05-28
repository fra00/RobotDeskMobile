# Visione — «cosa vedi?»

## Flusso (due passaggi LLM)

1. L’utente parla in sessione (es. «dimmi cosa vedi», «descrivi cosa c’è davanti»).
2. **Passo 1 — solo testo** → LM Studio risponde in JSON con `"imageRequired": true` e un breve `reply` (ack, es. «Ok, do un’occhiata.»).
3. L’app scatta una foto (fotocamera **frontale**, JPEG ridimensionato).
4. **Passo 2 — testo + immagine** → LM Studio (modello vision) analizza e risponde con `reply` + `emotion`, sempre `imageRequired: false`.
5. TTS della descrizione.

Il modello decide se serve la foto (vedi `llm_system_prompt.txt`); non c’è una lista fissa di frasi nell’app.

## Configurazione

In `local.properties`:

```properties
LLM_MODEL=modello-testo
# Modello con visione (es. Gemini in LM Studio). Se vuoto, usa LLM_MODEL.
LLM_VISION_MODEL=modello-vision
```

In LM Studio carica un modello **multimodale** per `LLM_VISION_MODEL`.

## Permessi

All’attivazione del microfono l’app chiede anche **fotocamera**. Senza permesso, al passo scatto compare un messaggio di errore.

## File principali

| File | Ruolo |
|------|--------|
| `assets/prompts/llm_system_prompt.txt` | Regole `imageRequired` |
| `LlmRepositoryImpl.kt` | `ask()` / `askWithImage()` |
| `CameraXVisionImageCapture.kt` | Scatto CameraX |
| `ConversationViewModel.kt` | Orchestrazione due passaggi |

## Problemi comuni

- **Loop «dimmi cosa vedi»**: di solito eco del TTS o modello senza visione. L’app tiene il microfono spento durante scatto+analisi; in log LM Studio al passo 2 deve comparire `content` con `image_url` (non solo testo).
- **imageRequired sempre false**: il modello testo non segue il prompt — prova un modello più obbediente o rafforza il system prompt.
- **Errore al passo 2**: `LLM_VISION_MODEL` deve essere un modello **multimodale** (es. Gemini vision). `google/gemma-4-e4b` da solo spesso non analizza immagini.
- **Foto nera / sfocata**: puntare il device verso la scena (lente frontale, come uno specchio).
