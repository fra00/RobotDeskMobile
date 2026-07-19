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
| [MEMORIA.md](MEMORIA.md) | Panoramica funzionale: tipi memoria, dedup exact, Riorganizza auto/manuale, Settings, FAQ |
| [MEMORIA_TECNICA.md](MEMORIA_TECNICA.md) | Agente cognitivo: indice unificato, recall, write path, `isPinned`, consolidation |
| [PROATTIVITA.md](PROATTIVITA.md) | Predittività, wellness, ordine ambientale (panoramica IT) |
| [PROATTIVITA_SMOKE.md](PROATTIVITA_SMOKE.md) | Checklist smoke test predittività e wellness |
| [UMORE.md](UMORE.md) | Umore persistente vs espressione turno, poke, notte, corpo |
| [UMORE_SMOKE.md](UMORE_SMOKE.md) | Checklist smoke test occhi + ESP32 |

*Guide future (impostazioni LLM, …) verranno aggiunte qui.*

## Spec tecniche correlate

| Argomento | Guide (umano) | Spec (agente) |
|-----------|---------------|---------------|
| Memoria | [MEMORIA.md](MEMORIA.md), [MEMORIA_TECNICA.md](MEMORIA_TECNICA.md) | [MEMORY.md](../MEMORY.md), [MEMORY_ACCESS.md](../MEMORY_ACCESS.md) |
| Log Day | sezione in MEMORIA.md | [ACTIVITY_LOG.md](../ACTIVITY_LOG.md) |
| Proattività | [PROATTIVITA.md](PROATTIVITA.md) | [PROACTIVE_ARCHITECTURE.md](../PROACTIVE_ARCHITECTURE.md) |
| Umore / occhi | [UMORE.md](UMORE.md) | [MOOD.md](../MOOD.md), [ROBOT_EXPRESSIONS.md](../ROBOT_EXPRESSIONS.md) |
| Stanze | sezione in MEMORIA.md | [SPATIAL_MEMORY.md](../SPATIAL_MEMORY.md) |
