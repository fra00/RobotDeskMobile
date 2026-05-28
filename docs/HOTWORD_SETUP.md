# Ascolto, frasi e LLM

## Parametri `strings.xml`

| Risorsa | Default | Effetto |
|---------|---------|---------|
| `wake_phrase` | `ehi robot` | Attiva sessione |
| `out_phrase` | `stop robot` | Chiude sessione → standby |
| `utterance_pause_seconds` | `5` | Pausa senza voce → **frase completa** → invio LLM |
| `conversation_silence_timeout_seconds` | `15` | Silenzio totale (buffer vuoto) → standby |
| `bored_idle_before_seconds` | `60` | In standby, dopo questo tempo → espressione **annoiata** |
| `bored_expression_seconds` | `4` | Durata occhi annoiati prima di tornare neutri |
| `bored_repeat_interval_seconds` | `90` | Intervallo tra un episodio annoiato e il successivo |

## Noia in standby

Con microfono attivo ma **nessuna wake word** per un po’:
1. Dopo `bored_idle_before_seconds` gli occhi passano a **BORED** per `bored_expression_seconds`.
2. Tornano **NEUTRAL**; l’episodio si ripete ogni `bored_repeat_interval_seconds` finché resti in standby.
3. Durante una sessione attiva (dopo la wake word) la noia **non** compare.

## Flusso frase → LLM

1. In sessione attiva tutto viene **trascritto** (testo in costruzione con `…`).
2. **5 s** di pausa senza nuova voce → frase chiusa → **LLM** → **TTS**.
3. Dopo la risposta resti in **sessione attiva**: puoi fare un’altra domanda **senza** ripetere la wake word.
4. Solo se per **15 s** (`conversation_silence_timeout_seconds`) non arriva nessuna frase (buffer vuoto) → **standby** (serve di nuovo `wake_phrase`).
5. `out_phrase` chiude la sessione in qualsiasi momento.

## LLM e voce

Configurazione **LM Studio** e TTS: vedi [LM_STUDIO.md](LM_STUDIO.md).  
Visione («cosa vedi?»): [VISION.md](VISION.md).
