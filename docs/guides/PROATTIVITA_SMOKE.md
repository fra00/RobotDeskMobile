# Smoke test — Proattività (Predittività + Wellness)

Checklist manuale dopo modifiche a H7. Eseguire con LLM configurato, mic attivo, heartbeat/proattività abilitati.

## Predittività

1. **Mining abitudine** — Per 3+ giorni distinti, registrare la stessa attività `PHYSICAL_NOW` nella stessa fascia oraria (es. passeggiata ~8:30 via dialogo o `log_daily_activity`).
2. **Riapertura app** — All’avvio sessione vocale, verificare che il mining incrementi hitCount (memoria PATTERN o log debug).
3. **Deviazione** — Un giorno senza l’attività, con mic ON, presenza OK (parlato negli ultimi 10 min o corpo trova volto), nella finestra ±45 min: il robot pone una domanda breve.
4. **Dedup** — Stesso giorno, non deve chiedere di nuovo la stessa abitudine.
5. **Delete habit** — Dire «non lo faccio più» e verificare che `delete_memory` rimuova il pattern; deviazione stop.
6. **Profilo SILENT** — Con contesto WORK/CALL, nessuna deviazione.
7. **Toggle off** — Disattivare «Predittività» in impostazioni → nessuna deviazione.

## Wellness (text-only, senza corpo)

1. Accendere hotword; attendere N min (default 60) configurati in impostazioni.
2. Restare senza turni vocali per almeno i minuti idle configurati (default **5**).
3. Presenza: parlato entro W min (default 45) **oppure** corpo trova volto.
4. Al massimo **una** frase wellness al giorno; secondo tick stesso giorno → silenzio.
5. Toggle «Wellness» off → nessun tick.

## Wellness (con corpo ESP32)

1. Corpo configurato e raggiungibile.
2. Dopo i gate sopra, fase visiva: scan + foto **senza TTS**; OBSERVATION ordine in memoria.
3. Poi fase score: eventuale frase se dominio carente (es. ordine disordinato).

## Legacy dismesso

- Foto utente «cosa vedi» **non** deve più scatenare dominio `ordine_ambiente`.
- Domini TimeDaily legacy (pasti, movimento, …) disabilitati di default; wellness li sostituisce.

Spec: [`PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md)
