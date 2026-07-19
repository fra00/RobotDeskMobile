# Fire-and-check UI

Tracks LLM **fire-and-check** loops (action + later verification) and shows indicators in the top-left stack (below STT / robot context icons).

## Registration

| Source | How |
|--------|-----|
| `set_reminder` with `fire_and_check`, `check_goal`, `trigger_reason` | Primary entry |
| Second `set_reminder` whose message starts with "Verifica" / contains "controllo" | Linked verification step |
| User phrase after scheduling | Backfills `trigger_reason` if LLM omitted it |

## UI

- One **icon per active** loop (schedule or fact-check icon by phase).
- **Tap** → dialog with check goal, verification step, trigger phrase, phase.
- Icon removed when verification reminder is handled or reminder cancelled.

## Files

- `data/check/FireAndCheckRepository.kt`
- `ui/components/FireAndCheckIndicators.kt`, `FireAndCheckDetailDialog.kt`

See `llm_system_prompt.txt` **FIRE_AND_CHECK** (§4 goal strategy gate + §5 execution) and `docs/SCHEDULED_TASKS.md`.

Classification (forget vs check) is prompt-mandated before tools; this doc covers the Kotlin UI/tracking when the LLM schedules a check loop. Declared physical actions ("adesso vado") may open a check when later observable — not every utterance.
