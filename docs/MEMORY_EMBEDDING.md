# Memory embeddings (Phase 0)

On-device semantic search for unified memory uses **paraphrase-multilingual-MiniLM-L12-v2** (384-dim, max 128 tokens) via ONNX Runtime.

## Setup

1. Download model assets (not in git — ~120–470 MB):

```powershell
# Se PowerShell blocca gli script, usa il .cmd (nessuna modifica alle policy):
.\scripts\download_embedding_model.cmd

# Oppure:
powershell -ExecutionPolicy Bypass -File .\scripts\download_embedding_model.ps1
```

Default variant `quantized_avx2` (~118 MB, Windows/Linux x86). Files: `model.onnx`, `tokenizer.json`, `tokenizer_config.json`.

This model uses **tokenizer.json** (SentencePiece), not vocab.txt.

2. Point the app/tests to the folder in `local.properties`:

```properties
embedding.model.dir=C\:\\Path\\To\\MyDeskRobot\\models\\embedding
```

Or set env var for JVM unit tests:

```powershell
$env:EMBEDDING_MODEL_DIR="C:\Path\To\MyDeskRobot\models\embedding"
```

3. Run benchmark (skipped automatically if model missing):

```powershell
.\gradlew :app:testDebugUnitTest --tests "com.example.mydeskrobot.memory.unified.embedding.MemoryEmbeddingBenchmarkTest"
```

## Quality gate

Italian paraphrase pairs must reach **cosine ≥ 0.55** (raw semantic, before hybrid):

| Memory | Query |
|--------|-------|
| Il venerdì lavora dalle 9 alle 13 | quando lavoro il venerdì |
| Il cane si chiama Brina | come si chiama il mio animale |
| Ogni mattina fa colazione alle 8 | abitudini mattutine utente |
| Lun-gio lavora anche il pomeriggio 14-18 | orari pomeridiani settimana |

If the gate fails → change model before enabling embeddings in production.

## Hybrid retrieval

`MemorySearchScorer` combines:

- **0.7** cosine similarity (when embeddings available)
- **0.3** token overlap (`MemoryTopicMatcher`)

Default **`minScore`**: `0.25` token-only (no model), `0.40` hybrid when embeddings are active (`MemorySearchScorer.HYBRID_MIN_SCORE`). Re-run `MemorySearchCalibrator` after benchmark to refine.

Out-of-domain queries (e.g. «chi era Garibaldi?») must score **below** `minScore` against unrelated memories.

## Runtime behaviour

| Model present | Behaviour |
|---------------|-----------|
| Yes | Semantic + hybrid search in voice recall, tools, contact resolution |
| No | Token-only fallback; background download retries on app start |

**Production (Phase 2):** on first app start, `EmbeddingModelManager` downloads model files to app private storage (~118 MB on arm64). No manual script required. Existing memories are backfilled in background via `reindexMissingEmbeddings`.

Developer benchmark on PC remains optional — see setup above.

## Code

| Component | Path |
|-----------|------|
| `TextEmbedder` | `memory/unified/embedding/TextEmbedder.kt` |
| ONNX impl | `memory/unified/embedding/OnnxTextEmbedder.kt` |
| Tokenizer | `memory/unified/embedding/BertWordPieceTokenizer.kt` |
| Golden set | `memory/unified/embedding/MemoryEmbeddingGoldenSet.kt` |
| Calibrator | `memory/unified/embedding/MemorySearchCalibrator.kt` |

See also `docs/Drafts/UNIFIED_MEMORY_RAG_PLAN.md` §7 and §11 (Fase 0).
