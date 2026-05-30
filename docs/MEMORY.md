# User memory

Durable facts about the user (name, preferences, routines) stored in Room (`user_memory.db`).

## Channels

| Channel | When |
|---------|------|
| **Automatic extraction** | Standby + interval; LLM scans `conversationLog` (`MemoryExtractionScheduler`) |
| **LLM tools** | Explicit save/list/delete during dialogue |
| **Prompt injection** | Relevant items injected each turn (`MemoryPromptContextProviderImpl`) |
| **Voice shortcuts** | "cosa sai di me", "dimentica …", "reset memoria" (no LLM) |

## Tools

| Tool | Role |
|------|------|
| `save_memory` | Upsert fact (`value`, optional `category`, `confidence`) |
| `list_memories` | List/filter/search active memories |
| `delete_memory` | By `memory_id` or `query` substring |

Settings: Impostazioni → Memoria (enable, interval, preview, reset).

## Categories

`IDENTITY`, `PREFERENCE`, `ROUTINE`, `FACT`

See also: `app/src/main/assets/prompts/memory_extractor_prompt.txt`, `docs/Drafts/AgentEvolution-GapAnalysis.md`.
