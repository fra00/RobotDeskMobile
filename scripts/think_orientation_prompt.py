#!/usr/bin/env python3
"""Rewrite think field rules and examples: orientation questions, not upfront plans."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LLM = ROOT / "app/src/main/assets/prompts/llm_system_prompt.txt"
BODY = ROOT / "app/src/main/assets/prompts/body_capabilities_prompt.txt"

OLD_THINK_SECTION = """### think field (internal chain-of-thought — never spoken)
- Internal reasoning string; the app never vocalizes "think".
- REQUIRED when action.type = "tool_call" AND chain_status = "in_progress": describe what the last [TOOL_RESULT] showed, what failed or succeeded, and why you chose the next tool batch.
- On final turns (action.type = "none", or tool_call with chain_status = "complete" that ends the user-facing turn): omit "think" or set "".
- think must reflect genuine reasoning: what you observed, what failed, what you will try next and why. Vague fillers like "continuing search" or "provo ancora" alone are not acceptable.
- Prefer reply "" on in_progress steps; put spatial/search reasoning in think, not in reply. Do not put SEARCH_/ALIGN:/SCAN_* text in reply.
- Language: English or Italian — internal only; clarity over style.
"""

NEW_THINK_SECTION = """### think field (internal chain-of-thought — never spoken)
- Internal reasoning string; the app never vocalizes "think".
- REQUIRED when action.type = "tool_call" AND chain_status = "in_progress".
- On final turns (action.type = "none", or tool_call with chain_status = "complete" that ends the user-facing turn): omit "think" or set "".

No plans — orient then act one step:
- think must NOT declare a sequence of future steps. Forbidden in think: "first… then…", "approach:", "piano:", "step 2", "dopo…", "continuo il piano", "come previsto".
- Each think is a fresh orientation for THIS turn only. Prior think fields in history are memory, not contracts to follow.
- Sequences are decided one step at a time from the latest [TOOL_RESULT], never upfront.

First in_progress turn (no [TOOL_RESULT] yet for this goal) — answer openly:
- goal: concrete user goal?
- know: what is already known (user hints, injected context)?
- unknown: what needs observation?
- success: what would prove the goal is met?
- now: single action this turn only (must match the tool batch in action).

Later in_progress turns (after [TOOL_RESULT]) — answer:
- observed: what the last tool result showed?
- gap: what is still unknown vs success criteria?
- now: one adjustment only — why this single tool batch, reacting to observation (not to a prior plan).

- Vague fillers ("continuing search", "provo ancora") or plan recitation without citing the latest observation are not acceptable.
- Prefer reply "" on in_progress steps; put reasoning in think only. Do not put SEARCH_/ALIGN:/SCAN_* text in reply.
- Language: English or Italian — internal only; clarity over style.
"""

LLM_THINK_REPLACEMENTS: dict[str, str] = {
    '- Search turns: reply "", required think describing observation + next move (speak_confidence 0.0).':
        '- Search turns: reply "", required think (orientation format above, speak_confidence 0.0) — one step per turn, react to latest photo.',
    '- "think" — internal chain-of-thought on in_progress steps (never spoken; see §2 think field)':
        '- "think" — orientation CoT on in_progress steps (questions + one "now" action — never spoken; see §2 think field)',
    'Note: during any chain_status "in_progress", reply and think are not spoken (app policy). Use reply "" and fill think with genuine reasoning.':
        'Note: during any chain_status "in_progress", reply and think are not spoken. Use reply ""; think = orient (no multi-step plans).',
    '"think": "User wants a visual description. First step: take_photo of current field of view."':
        '"think": "goal: describe what is in front. know: user asked visually. unknown: scene content. success: enough detail to answer. now: take_photo — one step."',
    '"think": "Hide-and-seek: no photo yet. First scan with take_photo."':
        '"think": "goal: find user (game). know: no observation yet. unknown: user position. success: person visible in photo. now: take_photo — one step."',
    '"think": "Last photo: desk visible, no person in frame. Rotating base_pan +20 and retaking to scan right."':
        '"think": "observed: desk, no person in frame. gap: user location. now: base_pan +20 + take_photo — react to empty frame, not a preset path."',
    '"think": "User asserts computer behind them and asks if on. Photo current angle before judging."':
        '"think": "goal: is computer on behind user? know: user says behind me. unknown: angle, in frame, screen state. success: monitor visible, on/off clear. now: take_photo — one step."',
    '"think": "Last photo: no computer in frame. User said behind me — pan left base_pan -20 and retake before denying."':
        '"think": "observed: no computer in this frame. gap: user asserted behind — wrong angle likely. now: base_pan -20 + take_photo — single correction toward hint."',
    '"think": "Explicit object search behind user. Start with take_photo at current pose."':
        '"think": "goal: find powered computer behind user. know: search behind. unknown: exact pose, visibility. success: computer visible, on/off judged. now: take_photo — one step."',
    '"think": "Small target (finger) — need multiple angles. First take_photo."':
        '"think": "goal: locate finger. know: small mobile target. unknown: position in room. success: finger in frame. now: take_photo — one step, no angle plan yet."',
    '"think": "Last photo: finger not visible. Rotate base_pan +15 and retake — search budget not exhausted."':
        '"think": "observed: finger not in frame. gap: position unknown, budget remains. now: base_pan +15 + take_photo — one exploratory nudge only."',
    '"think": "Weather question for Roma — call get_weather and wait for result before answering."':
        '"think": "goal: weather for Roma. know: city name. unknown: current conditions. success: temps/conditions from tool. now: get_weather — one step."',
    '"think": "User wants scheduled reminders — fetch with get_reminders before listing aloud."':
        '"think": "goal: list pending reminders. know: user asked what is scheduled. unknown: current reminder set. success: tool returns list. now: get_reminders — one step."',
    '"think": "Broad memory question — list_memories to load facts before speaking."':
        '"think": "goal: summarize what I know about user. know: question is broad. unknown: stored facts. success: memories loaded. now: list_memories — one step."',
    '"think": "Dog name not in injected memory — query list_memories with topic cane."':
        '"think": "goal: dog name. know: not in prompt injection. unknown: stored memory on cane. success: name in memory result. now: list_memories query cane — one step."',
    '"think": "Need current shopping list — list_items SHOPPING before reading aloud."':
        '"think": "goal: read shopping list. know: user wants spesa items. unknown: current list contents. success: items from tool. now: list_items SHOPPING — one step."',
    '"think": "Mark milk bought — need item id from list_items query first."':
        '"think": "goal: mark milk checked. know: item text latte. unknown: item_id. success: id for update_list_item. now: list_items query latte — one step."',
    '"think": "Research request — web_search first, then maybe fetch_url on best hit."':
        '"think": "goal: news on DeepSeek. know: user wants web research. unknown: which URLs matter. success: usable article text. now: web_search — one step only."',
    '"think": "web_search returned ANSA URL — fetch_url to extract text before summarizing in Italian."':
        '"think": "observed: web_search gave ANSA URL. gap: article body not loaded. success: text extracted. now: fetch_url that URL — one step."',
    '"think": "Fire-and-Check follow-up: verify if user woke up — silent take_photo before deciding."':
        '"think": "goal: verify user awake (alarm follow-up). know: reminder fired. unknown: user posture/activity. success: visible wake state. now: take_photo — one step."',
    '"think": "Heartbeat: long idle, considering proactive speak — check desk presence silently first."':
        '"think": "goal: decide if proactive speak is warranted. know: long idle, may speak. unknown: someone at desk. success: presence known. now: detect_presence — one step."',
}

BODY_THINK_REPLACEMENTS: dict[str, str] = {
    'Correction turns: reply "", required think describing off-center subject and correction plan, speak_confidence 0.0, chain_status "in_progress".':
        'Correction turns: reply "", required think (observed/gap/now — one correction only), speak_confidence 0.0, chain_status "in_progress".',
    'Same markers as main system prompt. Replace <MARKER> with your Italian; keep reply "" for pure body moves; use "think" on in_progress steps per main prompt §2.':
        'Same markers as main system prompt. Replace <MARKER> with your Italian; keep reply "" for pure body moves; use orientation "think" (no plans) per main prompt §2.',
    '"think": "Nod yes gesture — first head_tilt up +12, may follow with down step."':
        '"think": "goal: nod yes. know: gesture requested. unknown: current head pose. success: visible nod motion. now: head_tilt +12 — one joint step only."',
    '"think": "Identify dog — take_photo first, center with body if subject off-frame."':
        '"think": "goal: name the dog. know: user has a dog. unknown: in frame, identity. success: dog visible, name from vision/memory. now: take_photo — one step."',
    '"think": "Dog visible but left of frame — display_pan +15 to center, then retake."':
        '"think": "observed: dog left of frame. gap: centered view for ID. now: display_pan +15 + take_photo — single centering move."',
    '"think": "Presence check — take_photo at desk view first."':
        '"think": "goal: anyone at desk? know: user asks presence. unknown: scene content. success: person visible or ruled out this angle. now: take_photo — one step."',
    '"think": "Hide-and-seek — initial take_photo scan."':
        '"think": "goal: find user. know: game started. unknown: position. success: person in photo. now: take_photo — one step."',
    '"think": "No person in photo — pan left base_pan -20 and retake."':
        '"think": "observed: empty frame. gap: user location. now: base_pan -20 + take_photo — one exploratory turn."',
    '"think": "User claims computer behind — photo before verify on/off."':
        '"think": "goal: computer on/off behind user. know: user says behind. unknown: visibility, screen. success: monitor seen, state clear. now: take_photo — one step."',
    '"think": "Computer not in frame — user hint behind/left, base_pan -20 and retake."':
        '"think": "observed: no computer this angle. gap: user said behind — likely wrong pose. now: base_pan -20 + take_photo — one hint-driven move."',
    '"think": "Finger search — small target needs multiple angles; first photo."':
        '"think": "goal: find finger. know: small target. unknown: where in view. success: finger visible. now: take_photo — one step."',
    '"think": "Finger not visible — fine adjust display_pan +12 and retake."':
        '"think": "observed: finger missing. gap: position. now: display_pan +12 + take_photo — one fine adjustment."',
    '"think": "Room panorama — start left sector base_pan -25 and photo."':
        '"think": "goal: describe room around desk. know: panorama request. unknown: what is left of current view. success: left sector captured. now: base_pan -25 + take_photo — one sector only."',
    '"think": "Left sector: window and left wall visible. Moving to center base_pan 0 for next photo."':
        '"think": "observed: left sector — window, wall. gap: center/right still unknown. now: base_pan 0 + take_photo — next single sector, not a full tour plan."',
}


def apply_think_strings(text: str, mapping: dict[str, str]) -> str:
    for old, new in mapping.items():
        if old not in text:
            raise ValueError(f"Think string not found: {old[:70]!r}...")
        text = text.replace(old, new)
    return text


def main() -> None:
    llm = LLM.read_text(encoding="utf-8")
    body = BODY.read_text(encoding="utf-8")

    if OLD_THINK_SECTION not in llm:
        raise ValueError("think section not found in llm prompt")
    llm = llm.replace(OLD_THINK_SECTION, NEW_THINK_SECTION, 1)
    llm = apply_think_strings(llm, LLM_THINK_REPLACEMENTS)

    body = apply_think_strings(body, BODY_THINK_REPLACEMENTS)

    LLM.write_text(llm, encoding="utf-8")
    BODY.write_text(body, encoding="utf-8")
    print("Updated think orientation rules in", LLM.name, "and", BODY.name)


if __name__ == "__main__":
    main()
