# Fire-and-check UI

Tracks LLM **fire-and-check** loops (action + later verification) and shows indicators in the top-left stack (below STT / robot context icons).

## Kinds

| Kind | Meaning |
|------|---------|
| `PHYSICAL` | Classic loop: later presence/photo verify (`Verifica…`) |
| `COMMITMENT` | Urgent user task — timed dialogic follow-up **or** sticky pin |

## Registration

| Source | How |
|--------|-----|
| `set_reminder` with `fire_and_check`, `check_goal`, `trigger_reason` | Physical loop (LLM often schedules a second “Verifica…” reminder) |
| `set_reminder` with `commitment_follow_up` **+ time** | Timed commitment — Kotlin auto-schedules `Verifica impegno: …` at primary + 60 min |
| `set_reminder` with `commitment_follow_up` **and no** `delay`/`hour` | **Sticky** commitment — Room row only (no AlarmManager); icon until Elimina |
| Second `set_reminder` whose message starts with "Verifica" / contains "controllo" | Linked physical verification step |
| User phrase after scheduling | Backfills `trigger_reason` if LLM omitted it |

## Sticky vs timed commitment

| | Sticky (caso 1) | Timed (caso 2) |
|--|-----------------|----------------|
| User cue | «ricordami che devo assolutamente…» **without** a clock time | Same urgency **with** hour / «domani…» / delay |
| Tool | `commitment_follow_up` + `commitment_goal`, **omit** time params | Same + `hour`/`minute` or `delay_minutes` |
| Storage | `fire_and_check` ACTIVE, dues null (`isSticky`) | Primary + verification reminders |
| Visibility | Eyes UI icon until **Elimina** | Icon while ACTIVE; may complete after verify cycle |
| Auto-close | No | After verification handled / rememorize cap (max 1) |
| NOT | `add_list_item` TODO (not on main eyes UI) | Invent +60 verify relative to an unrelated deadline (known limit) |

Infer sticky: `kind == COMMITMENT && primaryDueAtMillis == null && verificationDueAtMillis == null`.

## Commitment follow-up policy (timed only)

See `CommitmentFollowUpPolicy`:

- Follow-up delay: **+60 min** after primary due
- Speak gate: verification voice uses `ProactiveSpeakGate` (work/silent → defer +20 min, no LLM announce)
- After “no”: ask rememorize once; max **1** rememorize cycle (`reRemindCount`)
- Primary reminder announce unchanged (not gated as proactive)
- Sticky commitments are ignored by `prepareNewCommitmentFollowUp` (stay until Elimina)

## UI

- One **icon per active** loop (schedule or fact-check icon by phase; sticky uses fact-check + sticky label).
- **Tap** → dialog with kind, goal, verification step (timed), trigger phrase, due times (timed), phase.
- Sticky dialog: «Impegno importante (senza scadenza)» / «aperto fino a eliminazione».
- **Chiudi** — dismisses the dialog only (loop stays active).
- **Elimina** — cancels the loop (`CANCELLED`) and linked primary/verification reminders + alarms (if any).
- Icon removed when verification reminder is handled, reminder cancelled, or user taps Elimina.

## Phases (derived from due times)

| Phase | Meaning |
|-------|---------|
| `SCHEDULED` | Primary due still in the future |
| `AWAITING_VERIFICATION` | Primary due passed / fired; verification due still in the future |
| `CHECK_PENDING` | Verification due has passed (or was never set) — UI “scaduta / ancora aperta”; sticky also lands here |

Verification **runs** when the verification reminder AlarmManager fires → voice/LLM announce. There is **no** auto-close if the alarm was missed or the speak cycle never completed: the loop stays `ACTIVE` until Completata (LLM handled verification) or Elimina / `delete_reminder`. Sticky never auto-completes.

## Files

- `domain/check/FireAndCheckKind.kt`, `CommitmentFollowUpPolicy.kt`, `FireAndCheckEntry.isSticky`
- `data/check/FireAndCheckRepository.kt` (`createStickyCommitment`)
- `integration/tool/local/ReminderTool.kt`, `ReminderAlarmReceiver.kt`
- `ui/components/FireAndCheckIndicators.kt`, `FireAndCheckDetailDialog.kt`

See `llm_system_prompt.txt` **FIRE_AND_CHECK** (§4 gate + §5 execution) and `docs/SCHEDULED_TASKS.md`.

Classification (forget vs check vs commitment) is prompt-mandated before tools; this doc covers Kotlin UI/tracking and commitment auto-scheduling.
