# Smoke test — umore ed espressioni

Checklist manuale dopo modifiche a mood, occhi o corpo ESP32. LLM configurato; opzionale corpo myDeskBody.

## Standby (mic attivo, nessun dialogo)

1. Avvia sessione hotword → occhi **neutral** (non happy fisso).
2. Attendi **~10 min** senza hotword/turno vocale (o abbassa `MoodConfig.hotwordIdleToBoredMinutes` in dev) → occhi **bored**, motivo `ascolto_hotword_senza_voce` in STATO ROBOT.
3. Attendi **~30 min** senza alcuna interazione → noia sessione (`IDLE_LONG`) se valenza bassa.
4. Attiva **modalità notte** → occhi **sleeping** / chiusi.
5. Disattiva notte → ritorno graduale da sonno.
6. **Spegni mic** → attendi 15+ min → valenza/occhi **invariati** (loop mood fermo).

## Dialogo attivo

7. Domande informative neutre (meteo, ore) → occhi **neutral/thinking** durante il turno; valenza sale lentamente, non happy continuo.
8. Elogio esplicito («bravissimo») → picco **happy** visibile; valenza +; decay verso baseline.
9. Critica sincera («sei inutile») → `emotion` **angry/sad** coerente; valenza non torna subito felice.
10. Fase **thinking** (domanda che usa tool) → occhi **thinking**, non bored.
11. **5 domande rapide** di fila → hint fatigue in STATO ROBOT; leggero calo valenza; niente sorriso continuo.
12. Ripeti 3× la stessa domanda breve («che ore sono») → hint ripetizione; occhi preferibilmente neutral/bored leggero.

## Poke occhi

13. Tap singolo → reazione (confusione / fastidio leggero).
14. Tap ripetuti (tier 2–3) → **angry**, corpo (se ESP32) gira display.
15. Subito dopo, fai una domanda breve («che ore sono») → **voce più rapida / leggermente più grave** rispetto al neutral (prosodia TTS sull'umore di fondo, anche se l'emotion del turno è neutral).
16. Scusa («scusa, non volevo») → tono ammorbidisce nel tempo; non entusiasmo immediato.
17. Con valenza alta / happy (dopo elogi reali) → voce un filo più acuta e vivace; confronta a orecchio con lo step 15.

## Tool e ack

18. Tool completato (lista, promemoria) → valenza modesta +; ack LLM con `happy` routinario **non** alza ulteriormente la valenza (ephemeral happy attenuato o neutral).
19. Dopo interazione positiva: valenza tende a salire (STATO ROBOT aggiornato al turno successivo).

## Corpo ESP32 (se configurato)

20. Dopo poke → movimento **display_pan** o testa entro pochi secondi.
21. Durante tool LLM body (`move_body_joint`) → **no** coreografia mood in conflitto (`BodyHardwareBusyGate`).

## Prompt

22. Blocco **STATO ROBOT** con valenza, emozione di fondo, profilo stile.
23. Dopo burst/ripetizione: righe **Contesto turno** nel blocco STATO ROBOT.

## Regressioni note

- Tono utente: giudicato dal LLM (`user_tone` nel JSON), nessuna keyword Kotlin.
- Wellness/predittività **non** cambiano valenza direttamente.
- Errori rete / STT vuoto **non** alterano valenza.

Spec: [`MOOD.md`](../MOOD.md).
