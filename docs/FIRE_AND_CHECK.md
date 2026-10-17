# Fire-and-check UI

Tracks LLM **fire-and-check** loops (action + later verification) and shows indicators in the top-left stack (below STT / robot context icons).

## Kinds

| Kind | Meaning |
|------|---------|
| `PHYSICAL` | Classic loop: later presence/photo verify (`Verifica…`) |
| `COMMITMENT` | Urgent user task: dialogic “Hai fatto X?” (`Verifica impegno:`) |

## Registration

| Source | How |
|--------|-----|
| `set_reminder` with `fire_and_check`, `check_goal`, `trigger_reason` | Physical loop (LLM often schedules a second “Verifica…” reminder) |
| `set_reminder` with `commitment_follow_up` (+ optional `commitment_goal`) | Commitment loop — **Kotlin** auto-schedules `Verifica impegno: …` at primary + 60 min |
| Second `set_reminder` whose message starts with "Verifica" / contains "controllo" | Linked physical verification step |
| User phrase after scheduling | Backfills `trigger_reason` if LLM omitted it |

## Commitment follow-up policy

See `CommitmentFollowUpPolicy`:

- Follow-up delay: **+60 min** after primary due
- Speak gate: verification voice uses `ProactiveSpeakGate` (work/silent → defer +20 min, no LLM announce)
- After “no”: ask rememorize once; max **1** rememorize cycle (`reRemindCount`)
- Primary reminder announce unchanged (not gated as proactive)

## UI

- One **icon per active** loop (schedule or fact-check icon by phase).
- **Tap** → dialog with kind, check/commitment goal, verification step, trigger phrase, phase.
- Icon removed when verification reminder is handled or reminder cancelled.

## Files

- `domain/check/FireAndCheckKind.kt`, `CommitmentFollowUpPolicy.kt`
- `data/check/FireAndCheckRepository.kt` (+ Room migration `kind`, `reRemindCount`)
- `integration/tool/local/ReminderTool.kt`, `ReminderAlarmReceiver.kt`
- `ui/components/FireAndCheckIndicators.kt`, `FireAndCheckDetailDialog.kt`

See `llm_system_prompt.txt` **FIRE_AND_CHECK** (§4 gate + §5 execution) and `docs/SCHEDULED_TASKS.md`.

Classification (forget vs check vs commitment) is prompt-mandated before tools; this doc covers Kotlin UI/tracking and commitment auto-scheduling.
