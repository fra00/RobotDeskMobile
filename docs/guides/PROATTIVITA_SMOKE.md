# Smoke test — Proattività (Predittività + Wellness)

Checklist manuale. Eseguire con LLM configurato. Spec: [`PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md).

## Predittività

1. **Mining abitudine** — Per 3+ giorni distinti, stessa attività `PHYSICAL_NOW` nella stessa fascia (es. passeggiata ~8:30).
2. **Riapertura app** — Mining incrementa hitCount (PATTERN / UI Abitudini dedotte).
3. **Deviazione** — Giorno senza attività, mic ON, presenza OK, finestra ±45 min → domanda breve a voce.
4. **Dedup** — Stesso giorno, non ripete la stessa abitudine.
5. **Delete habit** — «non lo faccio più» → pattern rimosso.
6. **Profilo SILENT** — WORK/CALL → nessuna deviazione.
7. **Toggle off** — Predittività off → nessuna deviazione.

## Wellness v2

1. Dopo ~N min dal primo avvio sessione / giorno (default **15**), con LLM libero: parte il **calcolo** anche con mic OFF, senza idle 5′ e senza presenza volto.
2. Risultato **persistito** (snapshot); Impostazioni → Proattività → Riepilogo wellness mostra i domini.
3. Se un dominio è in attenzione → **una notifica** (non TTS). Secondo tick stesso giorno → no secondo notify.
4. Chiedere a voce «come sto?» / «wellness» → risposta dallo snapshot, senza ricalcolo obbligatorio.
5. Profilo SILENT → calcolo ok, **niente** notifica.
6. Toggle Wellness off → nessun tick.
7. Con corpo + dominio ordine: 3 angoli + foto **senza TTS**; poi score → snapshot.
8. Tap sulla notifica → apre il riepilogo wellness.

## Legacy dismesso

- Foto utente «cosa vedi» non scatena `ordine_ambiente`.
- Domini TimeDaily legacy non schedulati; wellness li sostituisce.
