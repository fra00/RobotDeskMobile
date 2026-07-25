# Umore del robot — panoramica

Il robot ha un **umore persistente** (come un coinquilino che si ricorda se la giornata va bene o male) e, durante ogni risposta, un’**espressione momentanea** sugli occhi (e sul corpo ESP32, se collegato).

## Due livelli (non confonderli)

| | Umore di fondo | Espressione del turno |
|---|----------------|----------------------|
| **Cosa vedi** | Occhi in standby (noia, sonno, neutro, felice…) | Occhi mentre parla o ascolta (es. sorpresa, tristezza sulla frase appena detta) |
| **Durata** | Persistente (salvato); il tempo riporta al baseline in ≤ ~30 min da un picco tipico | ~15–30 secondi dopo la risposta |
| **Chi decide** | Regole Kotlin (idle hotword, notte, poke, presenza vocale, fatigue) + LLM su eventi emotivi | Campo `emotion` nel JSON del LLM |

In dialogo attivo l’espressione del turno **prevale** sugli occhi; in standby vedi l’umore di fondo.

**Anche la voce cambia:** quando parla, tono e velocità del TTS seguono l'emozione espressa — un po' più brillante e veloce se felice, più bassa e lenta se triste o assonnato. Variazioni leggere, da coinquilino, non da cartone animato.

**Parlare ≠ sorridere:** ogni turno vocale può alzare leggermente il benessere, ma la faccia resta **neutral/thinking** salvo un evento (elogio, affetto, buona notizia).

## Cosa cambia l’umore di fondo

- **Mic attivo, nessun turno vocale:** dopo ~10 min in ascolto hotword senza che tu parli → noia (`IDLE_LISTENING`).
- **Dopo la noia:** può **guardarsi intorno** (occhi/corpo), poi per qualche minuto si “svaga” in modo **solo simbolico** (cuffie, libro, cartello “Torno subito”, TV con Pong). Non parte musica vera né letture reali. Questo **toglie la noia situazionale** (timer idle) ma **non alza** l’umore di fondo: se era giù di morale, resta giù. A fine pausa il timer noia riparte da zero.
- **Mic spento:** il loop umore **non gira** — nessun cambiamento nel tempo.
- **Tempo senza interazione (sessione):** dopo ~30 min senza dialogo → noia; molto più a lungo → sonnolenza (se l’umore era già basso).
- **Notte:** in modalità notte gli occhi tendono a chiudersi / sonno.
- **Presenza vocale:** ogni frase utile alza leggermente la valenza; un elogio esplicito conta di più (con cap/ora) perché sblocca l'effetto pieno dello happy del robot su quel turno. Il tono della tua frase (elogio, insulto, scuse) lo **giudica il LLM stesso**, non una lista di parole chiave.
- **Task utile completato:** piccolo boost.
- **Burst / ripetizione:** molte domande di fila o stessa richiesta ripetuta → leggero calo + hint nel prompt.
- **Poke occhi:** tap ripetuti → fastidio; scuse sincere possono ammorbidire.
- **Emozione LLM:** `sad`/`angry`/`bored` abbassano; `happy`/`loving` alzano solo su **eventi reali**, non su ogni ack post-tool.

L’umore **torna sempre** verso un baseline (+0.1): gli eventi **spostano** la valenza, il tempo la **riporta**. Il timer del decay **non si resetta** se parli, ricevi un boost vocale o un’emozione LLM — solo i passi di decay lo avanzano. Passi ~0.15 ogni 5 min se alto, ogni 6 min se basso; da un picco felice (~0.85) al normale servono circa **25 minuti** di orologio (mic acceso). Anche la noia da idle non blocca più il recupero.

## Cosa vede l’LLM

Nel prompt compare **STATO ROBOT** (valenza, emozione di fondo, motivo, **espressione occhi attuale** se effimera attiva, stile risposta). Se gli occhi sono bored/sad mentre il fondo è felice, **la faccia vince** su “come stai?” — non “va tutto bene”. Default `emotion` ancora `neutral`/`thinking`, `happy` solo quando ha senso emotivo.

## Corpo ESP32

Se il corpo è configurato, poke occhi, noia, sonno e alcune emozioni effimere muovono testa/display in modo **automatico** (Kotlin), senza che tu debba chiedere movimenti espliciti.

Dettaglio hardware: [`BODY_INTEGRATION.md`](../BODY_INTEGRATION.md).

## Cosa puoi fare tu

- **«Sii felice»** → picco visivo happy + valenza.
- **Dialogo tecnico neutro** → occhi neutral, valenza sale lentamente.
- **Modalità notte** → sonno visivo.
- **Evitare poke occhi** se non vuoi vederlo arrabbiato.
- **Scuse** dopo poke → recupero graduale.

Soglie idle/burst/praise cap: `MoodConfig` / `MoodValenceConfig` (codice, no UI impostazioni).

## Smoke test

Checklist manuale: [`UMORE_SMOKE.md`](UMORE_SMOKE.md).

## Spec tecnica

Per implementatori e agenti AI: [`docs/MOOD.md`](../MOOD.md), token occhi: [`ROBOT_EXPRESSIONS.md`](../ROBOT_EXPRESSIONS.md).
