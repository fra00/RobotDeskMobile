# Guide (documentazione human-first)

Questa cartella contiene guide orientate alle **persone**: chi configura l'app, fa test manuali o vuole capire *come funziona* il robot senza leggere il codice.

## `docs/` vs `docs/guides/`

| | [`docs/`](../) | [`docs/guides/`](.) |
|---|----------------|---------------------|
| **Pubblico** | Agenti AI, implementatori, PR | Umano (configuratore, tester) |
| **Lingua** | Prevalentemente inglese | **Italiano** |
| **Stile** | Contratti, path file, tabelle SSOT, checklist QA | Narrativo, esempi, diagrammi, FAQ |
| **Quando usarla** | Implementare o modificare una feature | Capire comportamento, fare smoke test, spiegare il sistema |

**Regola:** per implementare una modifica al codice, la fonte tecnica resta sempre `docs/` (e `AGENTS.md`). Le guide spiegano e collegano; non sostituiscono i contratti.

## Manutenzione

Se cambia il comportamento runtime di una feature documentata qui:

1. Aggiorna la guida in `docs/guides/`.
2. Aggiorna il contratto corrispondente in `docs/` (e, se serve, `AGENTS.md`).

## Indice guide

| Guida | Contenuto |
|-------|-----------|
| [MEMORIA.md](MEMORIA.md) | Panoramica funzionale: tutti i tipi di memoria, Settings, comandi vocali, FAQ |
| [MEMORIA_TECNICA.md](MEMORIA_TECNICA.md) | Agente cognitivo: indice unificato, recall, scrittura, snippet codice |

*Guide future (heartbeat, impostazioni LLM, …) verranno aggiunte qui.*

## Spec tecniche correlate

| Argomento | Guide (umano) | Spec (agente) |
|-----------|---------------|---------------|
| Memoria | [MEMORIA.md](MEMORIA.md), [MEMORIA_TECNICA.md](MEMORIA_TECNICA.md) | [MEMORY.md](../MEMORY.md), [MEMORY_ACCESS.md](../MEMORY_ACCESS.md) |
| Log Day | sezione in MEMORIA.md | [ACTIVITY_LOG.md](../ACTIVITY_LOG.md) |
| Stanze | sezione in MEMORIA.md | [SPATIAL_MEMORY.md](../SPATIAL_MEMORY.md) |
