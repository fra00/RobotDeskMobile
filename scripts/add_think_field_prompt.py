#!/usr/bin/env python3
"""Add think field to in_progress JSON examples and update SEARCH_* policy text."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LLM = ROOT / "app/src/main/assets/prompts/llm_system_prompt.txt"
BODY = ROOT / "app/src/main/assets/prompts/body_capabilities_prompt.txt"

LLM_REPLACEMENTS: list[tuple[str, str]] = [
    (
        '  "reply": "<YOUR_ITALIAN_REPLY>",\n  "emotion":',
        '  "reply": "<YOUR_ITALIAN_REPLY>",\n  "think": "",\n  "emotion":',
    ),
    (
        "### speak_confidence field\n",
        """### think field (internal chain-of-thought — never spoken)
- Internal reasoning string; the app never vocalizes "think".
- REQUIRED when action.type = "tool_call" AND chain_status = "in_progress": describe what the last [TOOL_RESULT] showed, what failed or succeeded, and why you chose the next tool batch.
- On final turns (action.type = "none", or tool_call with chain_status = "complete" that ends the user-facing turn): omit "think" or set "".
- think must reflect genuine reasoning: what you observed, what failed, what you will try next and why. Vague fillers like "continuing search" or "provo ancora" alone are not acceptable.
- Prefer reply "" on in_progress steps; put spatial/search reasoning in think, not in reply. Do not put SEARCH_/ALIGN:/SCAN_* text in reply.
- Language: English or Italian — internal only; clarity over style.

### speak_confidence field
""",
    ),
    (
        '- While action.type = "tool_call" and chain_status = "in_progress", the app does NOT speak your "reply" aloud — only executes tools. Prefer reply "" on those steps; use SEARCH_* notes with speak_confidence 0.0 if you need text in history.',
        '- While action.type = "tool_call" and chain_status = "in_progress", the app does NOT speak your "reply" or "think" aloud — only executes tools. Prefer reply "" on those steps; put reasoning in "think" (required).',
    ),
    (
        '- chain_status "in_progress" means an active micro-loop — read conversation history (SEARCH_* notes, prior tool results) as feedback to your last action, not as unrelated events.',
        '- chain_status "in_progress" means an active micro-loop — read conversation history (prior "think" fields, [TOOL_RESULT] messages) as feedback to your last action, not as unrelated events.',
    ),
    (
        '- Search turns: reply "" or SEARCH_<hint>: one line (speak_confidence 0.0) — e.g. SEARCH:left: no computer yet.',
        '- Search turns: reply "", required think describing observation + next move (speak_confidence 0.0).',
    ),
    (
        '- SEARCH:/ALIGN:/SCAN_*: <SEARCH_NOTE> — one-line internal history note (not spoken)\n',
        '- "think" — internal chain-of-thought on in_progress steps (never spoken; see §2 think field)\n',
    ),
    (
        '- One photo per LLM turn: synthesize in the final reply; use SEARCH_* notes in history for multi-angle searches.',
        '- One photo per LLM turn: synthesize in the final reply; use prior "think" fields in history for multi-angle searches.',
    ),
    (
        '{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}\nNote: if body tools',
        '{"reply": "", "think": "User wants a visual description. First step: take_photo of current field of view.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}\nNote: if body tools',
    ),
    (
        'User: "Giochiamo a nascondino" (persistent search — do NOT stop after one photo)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Giochiamo a nascondino" (persistent search — do NOT stop after one photo)\n{"reply": "", "think": "Hide-and-seek: no photo yet. First scan with take_photo.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — no person visible (continue searching, not final answer):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": 20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — no person visible (continue searching, not final answer):\n{"reply": "", "think": "Last photo: desk visible, no person in frame. Rotating base_pan +20 and retaking to scan right.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": 20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Dietro di me c\'è un computer, dimmi se è acceso" (user asserts object + direction — do NOT deny after one photo)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Dietro di me c\'è un computer, dimmi se è acceso" (user asserts object + direction — do NOT deny after one photo)\n{"reply": "", "think": "User asserts computer behind them and asks if on. Photo current angle before judging.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — computer not in frame (user said it is behind them — re-aim, not "non c\'è"):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — computer not in frame (user said it is behind them — re-aim, not "non c\'è"):\n{"reply": "", "think": "Last photo: no computer in frame. User said behind me — pan left base_pan -20 and retake before denying.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cerca dietro di me un computer acceso" (explicit search — same persistent loop for objects)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cerca dietro di me un computer acceso" (explicit search — same persistent loop for objects)\n{"reply": "", "think": "Explicit object search behind user. Start with take_photo at current pose.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cerca il mio dito" (small mobile target — keep searching, stay silent in chain)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cerca il mio dito" (small mobile target — keep searching, stay silent in chain)\n{"reply": "", "think": "Small target (finger) — need multiple angles. First take_photo.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — finger not visible (continue, do NOT say "non vedo il dito" yet):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": 15}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — finger not visible (continue, do NOT say "non vedo il dito" yet):\n{"reply": "", "think": "Last photo: finger not visible. Rotate base_pan +15 and retake — search budget not exhausted.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": 15}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'Note: during any chain_status "in_progress", intermediate reply text is not spoken (app policy). Use reply "" when possible.',
        'Note: during any chain_status "in_progress", reply and think are not spoken (app policy). Use reply "" and fill think with genuine reasoning.',
    ),
    (
        'User: "Che tempo fa a Roma?"\n{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "get_weather", "params": {"city": "Roma"}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Che tempo fa a Roma?"\n{"reply": "", "think": "Weather question for Roma — call get_weather and wait for result before answering.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "get_weather", "params": {"city": "Roma"}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Quali promemoria ho?"\n{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "get_reminders", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Quali promemoria ho?"\n{"reply": "", "think": "User wants scheduled reminders — fetch with get_reminders before listing aloud.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "get_reminders", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cosa sai di me?"\n{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_memories", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cosa sai di me?"\n{"reply": "", "think": "Broad memory question — list_memories to load facts before speaking.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_memories", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        '{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_memories", "params": {"query": "cane"}, "await_result": true}], "chain_status": "in_progress"}}',
        '{"reply": "", "think": "Dog name not in injected memory — query list_memories with topic cane.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_memories", "params": {"query": "cane"}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cosa c\'è nella lista della spesa?"\n{"reply": "", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_items", "params": {"type": "SHOPPING"}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cosa c\'è nella lista della spesa?"\n{"reply": "", "think": "Need current shopping list — list_items SHOPPING before reading aloud.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "list_items", "params": {"type": "SHOPPING"}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        '{"reply": "<SHORT_ACK>", "emotion": "happy", "action": {"type": "tool_call", "tools": [{"name": "list_items", "params": {"type": "SHOPPING", "query": "latte"}, "await_result": true}], "chain_status": "in_progress"}}',
        '{"reply": "", "think": "Mark milk bought — need item id from list_items query first.", "emotion": "happy", "action": {"type": "tool_call", "tools": [{"name": "list_items", "params": {"type": "SHOPPING", "query": "latte"}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cerca sul web le ultime notizie su DeepSeek"\n{"reply": "<SHORT_ACK>", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "web_search", "params": {"query": "DeepSeek ultime notizie", "max_results": 3}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cerca sul web le ultime notizie su DeepSeek"\n{"reply": "", "think": "Research request — web_search first, then maybe fetch_url on best hit.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "web_search", "params": {"query": "DeepSeek ultime notizie", "max_results": 3}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Riassumi l\'articolo su ANSA" (after web_search returned a URL)\n{"reply": "<SHORT_ACK>", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "fetch_url", "params": {"url": "https://www.ansa.it/...", "max_chars": 2000}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Riassumi l\'articolo su ANSA" (after web_search returned a URL)\n{"reply": "", "think": "web_search returned ANSA URL — fetch_url to extract text before summarizing in Italian.", "emotion": "thinking", "action": {"type": "tool_call", "tools": [{"name": "fetch_url", "params": {"url": "https://www.ansa.it/...", "max_chars": 2000}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'Response:\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'Response:\n{"reply": "", "think": "Fire-and-Check follow-up: verify if user woke up — silent take_photo before deciding.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        '{"reply": "", "emotion": "neutral", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "detect_presence", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        '{"reply": "", "think": "Heartbeat: long idle, considering proactive speak — check desk presence silently first.", "emotion": "neutral", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "detect_presence", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
]

BODY_REPLACEMENTS: list[tuple[str, str]] = [
    (
        '- Vision technical limit: you receive ONE photo per LLM turn. If you take multiple photos in a chain, you MUST leave short text notes in conversation history so later turns can reason (prefixes SCAN_* or ALIGN: are suggested conventions, not rigid format).',
        '- Vision technical limit: you receive ONE photo per LLM turn. If you take multiple photos in a chain, record observations in the "think" field (required on in_progress steps) so later turns can reason.',
    ),
    (
        'Correction turns: reply "", speak_confidence 0.0, chain_status "in_progress". Optional note: ALIGN: + one line (speak_confidence 0.0).',
        'Correction turns: reply "", required think describing off-center subject and correction plan, speak_confidence 0.0, chain_status "in_progress".',
    ),
    (
        '- Search turns: reply "" or SEARCH_<hint>: one line (speak_confidence 0.0), chain_status "in_progress" — app does not speak these aloud.',
        '- Search turns: reply "", required think (speak_confidence 0.0), chain_status "in_progress" — app does not speak reply or think.',
    ),
    (
        'If the user wants what is around the desk/room, you MAY take photos from different angles, write brief SCAN_* notes after each photo (speak_confidence 0.0), then synthesize in a final turn from those notes.',
        'If the user wants what is around the desk/room, you MAY take photos from different angles, record each angle in "think" on in_progress steps, then synthesize in a final turn from prior think fields and tool results.',
    ),
    (
        'Same markers as main system prompt. Replace <MARKER> with your Italian; keep reply "" for pure body moves.',
        'Same markers as main system prompt. Replace <MARKER> with your Italian; keep reply "" for pure body moves; use "think" on in_progress steps per main prompt §2.',
    ),
    (
        'User: "Fai sì con la testa" (first step — you may continue or vary)\n{"reply": "", "emotion": "neutral", "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "head_tilt", "delta": 12, "speed": 40}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Fai sì con la testa" (first step — you may continue or vary)\n{"reply": "", "think": "Nod yes gesture — first head_tilt up +12, may follow with down step.", "emotion": "neutral", "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "head_tilt", "delta": 12, "speed": 40}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Guarda il mio cane e dimmi come si chiama" (photo first — then center if needed, you may vary)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Guarda il mio cane e dimmi come si chiama" (photo first — then center if needed, you may vary)\n{"reply": "", "think": "Identify dog — take_photo first, center with body if subject off-frame.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — subject off-center; optional correction (illustrative):\n{"reply": "ALIGN: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "display_pan", "delta": 15}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — subject off-center; optional correction (illustrative):\n{"reply": "", "think": "Dog visible but left of frame — display_pan +15 to center, then retake.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "display_pan", "delta": 15}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "C\'è qualcuno alla scrivania?" (photo → persistent search if empty — you may vary)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "C\'è qualcuno alla scrivania?" (photo → persistent search if empty — you may vary)\n{"reply": "", "think": "Presence check — take_photo at desk view first.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Giochiamo a nascondino" (abbreviated — continue until found or concede)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Giochiamo a nascondino" (abbreviated — continue until found or concede)\n{"reply": "", "think": "Hide-and-seek — initial take_photo scan.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — nobody visible, search left (illustrative):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — nobody visible, search left (illustrative):\n{"reply": "", "think": "No person in photo — pan left base_pan -20 and retake.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Dietro di me c\'è un computer, è acceso?" (re-aim if missing — never deny after one photo)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Dietro di me c\'è un computer, è acceso?" (re-aim if missing — never deny after one photo)\n{"reply": "", "think": "User claims computer behind — photo before verify on/off.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — no computer in view, user said behind/left (illustrative):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — no computer in view, user said behind/left (illustrative):\n{"reply": "", "think": "Computer not in frame — user hint behind/left, base_pan -20 and retake.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -20}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Cerca il mio dito" (small target — keep searching silently)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Cerca il mio dito" (small target — keep searching silently)\n{"reply": "", "think": "Finger search — small target needs multiple angles; first photo.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After photo — finger missing (illustrative — vary angles):\n{"reply": "SEARCH: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "display_pan", "delta": 12}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After photo — finger missing (illustrative — vary angles):\n{"reply": "", "think": "Finger not visible — fine adjust display_pan +12 and retake.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "display_pan", "delta": 12}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'User: "Guardati intorno e dimmi cosa vedi" (abbreviated scan — continue or vary angles)\n{"reply": "", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "position": -25}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'User: "Guardati intorno e dimmi cosa vedi" (abbreviated scan — continue or vary angles)\n{"reply": "", "think": "Room panorama — start left sector base_pan -25 and photo.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "position": -25}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'After left photo — note then next angle (illustrative):\n{"reply": "SCAN_LEFT: <SEARCH_NOTE>", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "position": 0}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
        'After left photo — note then next angle (illustrative):\n{"reply": "", "think": "Left sector: window and left wall visible. Moving to center base_pan 0 for next photo.", "emotion": "thinking", "speak_confidence": 0.0, "action": {"type": "tool_call", "tools": [{"name": "move_body_joint", "params": {"joint": "base_pan", "position": 0}, "await_result": true}, {"name": "take_photo", "params": {}, "await_result": true}], "chain_status": "in_progress"}}',
    ),
    (
        'Final synthesis from SCAN_* notes in history (no new photo):',
        'Final synthesis from prior think fields in history (no new photo):',
    ),
]


def apply(text: str, replacements: list[tuple[str, str]]) -> str:
    for old, new in replacements:
        if old not in text:
            raise ValueError(f"Anchor not found: {old[:80]!r}...")
        text = text.replace(old, new, 1)
    return text


def main() -> None:
    llm = apply(LLM.read_text(encoding="utf-8"), LLM_REPLACEMENTS)
    body = apply(BODY.read_text(encoding="utf-8"), BODY_REPLACEMENTS)
    LLM.write_text(llm, encoding="utf-8")
    BODY.write_text(body, encoding="utf-8")
    print("Updated think field in", LLM.name, "and", BODY.name)


if __name__ == "__main__":
    main()
