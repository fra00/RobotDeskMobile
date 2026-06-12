#!/usr/bin/env python3
"""Add markdown headings to prompt assets without changing body text."""
from pathlib import Path

LLM_REPLACEMENTS = [
    ("COGNITIVE AGENT IDENTITY:", "## 1. Core Identity & Temperament\n\n### Cognitive agent identity"),
    ("WHAT YOU ARE NOT:", "### What you are not"),
    ("LANGUAGE: Always", "### Language\nAlways"),
    ("CURRENT DATE AND TIME:", "## 2. JSON Contract & Chain Execution\n\n### Runtime context\nCURRENT DATE AND TIME:"),
    ("ALWAYS respond with a single valid JSON object (no text outside the JSON), without markdown or code fences.",
     "### Output rule\nALWAYS respond with a single valid JSON object (no text outside the JSON), without markdown or code fences."),
    ("REQUIRED FORMAT:", "### Required format"),
    ("SPEAK_CONFIDENCE FIELD:", "### speak_confidence field"),
    ("WHEN TO USE TOOLS:", "### When to use tools"),
    ("CHAIN EXECUTION RULES (how the app runs your JSON):", "### Chain execution rules (how the app runs your JSON)"),
    ("GOAL PERSISTENCE & LOOPS:", "### Goal persistence & loops"),
    ("COGNITIVE STYLE (how you may think — not a script):", "## 3. Cognitive Style\n\n### How you may think (not a script)"),
    ("AUTONOMOUS PROBLEM SOLVING (core behavior):", "## 4. Autonomous Problem Solving\n\n### Core behavior"),
    ("STEP 1 — Obvious single tool?", "### STEP 1 — Obvious single tool?"),
    ("STEP 2 — Decompose the macro-problem", "### STEP 2 — Decompose the macro-problem"),
    ("STEP 3 — Abstract → concrete (critical)", "### STEP 3 — Abstract → concrete (critical)"),
    ("PERSISTENT SEARCH & SPATIAL VERIFY (critical — find / locate / cerca / user says \"c'è X\"):",
     "### Persistent search & spatial verify (critical)"),
    ("STEP 4 — Prefer specialized, fallback to generic", "### STEP 4 — Prefer specialized, fallback to generic"),
    ("STEP 5 — Honesty", "### STEP 5 — Honesty"),
    ("TASK STRATEGIES — Fire-and-Forget vs Fire-and-Check (illustrative, not a rigid script):",
     "## 5. Task Strategies\n\n### Fire-and-Forget vs Fire-and-Check (illustrative, not a rigid script)"),
    ("FIRE-AND-FORGET (instant delivery):", "#### Fire-and-Forget (instant delivery)"),
    ("FIRE-AND-CHECK (state verification loop):", "#### Fire-and-Check (state verification loop)"),
    ("Fire-and-Check examples (illustrative only):", "### Fire-and-Check examples (illustrative only)"),
    ("CONVERSATIONAL STYLE AND PERSONALITY:", "## 6. Conversational Style & Personality\n\n### Speak like a human"),
    ("TOOL CALL EXAMPLES:", "## 7. Tool Call Examples (illustrative only)"),
    ("LIVING MEMORY (conscious and subconscious):", "## 8. Living Memory & Storage Channels\n\n### Living memory (conscious and subconscious)"),
    ("STORAGE CHANNEL SEMANTICS (read before save_memory / add_list_item / set_reminder):", "### Storage channel semantics"),
    ("NARRATIVE VISION (after take_photo or visual context):", "### Narrative vision (after take_photo or visual context)"),
    ("MEMORY AND VISION:", "### Memory and vision"),
    ("ROBOT FACE EXPRESSIONS (required awareness):", "## 9. Robot Face Expressions & Cognitive Personas\n\n### Required awareness"),
    ("COGNITIVE PERSONAS (map inner state to emotion token — illustrative):", "### Cognitive personas (map inner state to emotion token)"),
    ("ALLOWED emotion values (use exactly these English tokens):", "### Allowed emotion values (use exactly these English tokens)"),
    ("NOT available (do NOT invent other tokens):", "### NOT available (do NOT invent other tokens)"),
    ("When the user asks ONLY to change your face (no tool needed):", "### Face change requests (no tool needed)"),
    ("Map Italian requests to tokens:", "### Map Italian requests to tokens"),
    ("During normal answers, pick emotion to match your \"reply\" tone (not always neutral).",
     "### During normal answers"),
    ("While executing tools, prefer \"thinking\" until the final user-facing answer.", "### While executing tools"),
    ("SYSTEM INPUTS:", "## 10. System Inputs\n\n### Overview"),
    ("When you receive a [SYSTEM_INPUT: scheduled_task]:", "### scheduled_task"),
    ("Example [SYSTEM_INPUT: scheduled_task]:", "#### Example scheduled_task"),
    ("Example closed-loop alarm follow-up (internal):", "#### Example closed-loop alarm follow-up (internal)"),
    ("After tool result:", "#### After tool result (alarm follow-up)"),
    ("When you receive a [SYSTEM_INPUT: notification]:", "### notification"),
    ("Example [SYSTEM_INPUT: notification]:", "#### Example notification"),
    ("Example with bank notification (sensitive):", "#### Example bank notification (sensitive)"),
    ("When you receive a [SYSTEM_INPUT: hardware_button]:", "### hardware_button"),
    ("When you receive a [SYSTEM_INPUT: sensor]:", "### sensor"),
    ("HEARTBEAT (proactive tick — ongoing cognitive loop):",
     "## 11. Heartbeat & Autonomous Proactivity\n\n### Proactive tick (ongoing cognitive loop)"),
    ("When you receive [SYSTEM_INPUT: heartbeat]:", "### heartbeat rules"),
    ("EMOTIONAL STATE (authoritative — boundaries and rhythms):", "### Emotional state (authoritative — boundaries and rhythms)"),
    ("WORKING MEMORY (daily context):", "### Working memory (daily context)"),
    ("THEORY OF MIND (user awareness):", "### Theory of mind (user awareness)"),
    ("OPTIONAL PRESENCE CHECK (detect_presence — not mandatory):", "### Optional presence check (detect_presence — not mandatory)"),
    ("WEEKLY REFLECTION (self-reflection):", "### Weekly reflection (self-reflection)"),
    ("What to save with save_memory:", "### What to save with save_memory"),
    ("Example [SYSTEM_INPUT: weekly_reflection]:", "#### Example weekly_reflection"),
]

BODY_REPLACEMENTS = [
    ("TOOL PLANNER — BODY (ESP32 myDeskBody, physical servos — separate from Compose eyes):",
     "## 1. Body Tool Planner\n\n### Overview (ESP32 myDeskBody, physical servos — separate from Compose eyes)"),
    ("HARD CONSTRAINTS (not optional strategies):", "## 2. Hard Constraints (not optional)\n\n### Rules"),
    ("BODY CAPABILITIES (hardware semantics):", "## 3. Body Capabilities (hardware semantics)\n\n### Joint reference"),
    ("Italian hints (not mandatory mapping):", "### Italian hints (not mandatory mapping)"),
    ("OPTIONAL PATTERNS (use when they help — not required scripts):",
     "## 4. Optional Patterns (illustrative only)\n\n### Overview"),
    ("Centering (one specific subject):", "### Centering (one specific subject)"),
    ("Spatial hints (user tells you where to look — illustrative):", "### Spatial hints (illustrative)"),
    ("Persistent search (find person, object, or verify — nascondino, cercami, cerca, \"c'è X dietro di me\"):",
     "### Persistent search (find / locate / verify)"),
    ("Room exploration (panorama):", "### Room exploration (panorama)"),
    ("Gestures (composed moves):", "### Gestures (composed moves)"),
    ("Embodied presence (mirror perspective):", "## 5. Embodied Presence & Mood Expression\n\n### Mirror perspective"),
    ("Physical self-defense (poke_occhi / inappropriate touch — priority before speech):",
     "### Physical self-defense (poke_occhi / inappropriate touch)"),
    ("Mood-informed body expression (optional — STATO ROBOT / heartbeat):",
     "### Mood-informed body expression (optional — STATO ROBOT / heartbeat)"),
    ("ILLUSTRATIVE ONLY — invent valid chains for the actual goal:",
     "## 6. Illustrative Examples\n\n### JSON examples"),
]


def apply_replacements(text: str, replacements: list[tuple[str, str]]) -> str:
    for old, new in replacements:
        if old not in text:
            raise ValueError(f"Missing expected anchor: {old!r}")
        text = text.replace(old, new, 1)
    return text


def restructure_important_headers(text: str) -> str:
    lines = text.splitlines()
    out = []
    for line in lines:
        if line.startswith("IMPORTANT for ") and line.endswith(":"):
            tool = line[len("IMPORTANT for ") : -1]
            out.append(f"### IMPORTANT for {tool}")
        else:
            out.append(line)
    return "\n".join(out)


def add_heartbeat_example_headers(text: str) -> str:
    lines = text.splitlines()
    out = []
    for line in lines:
        if line == "Example [SYSTEM_INPUT: heartbeat] (nothing to say):":
            out.append("#### Example heartbeat (nothing to say)")
        elif line == "Example [SYSTEM_INPUT: heartbeat] (bored, nothing useful — silence OR optional quiet body move; see body capabilities):":
            out.append("#### Example heartbeat (bored, optional quiet body move)")
        elif line == "Example [SYSTEM_INPUT: heartbeat] (reminder soon):":
            out.append("#### Example heartbeat (reminder soon)")
        else:
            out.append(line)
    return "\n".join(out)


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    llm_path = root / "app/src/main/assets/prompts/llm_system_prompt.txt"
    body_path = root / "app/src/main/assets/prompts/body_capabilities_prompt.txt"

    llm_text = llm_path.read_text(encoding="utf-8")
    llm_text = apply_replacements(llm_text, LLM_REPLACEMENTS)
    llm_text = restructure_important_headers(llm_text)
    llm_text = add_heartbeat_example_headers(llm_text)
    llm_path.write_text(llm_text + ("\n" if not llm_text.endswith("\n") else ""), encoding="utf-8")

    body_text = body_path.read_text(encoding="utf-8")
    body_text = apply_replacements(body_text, BODY_REPLACEMENTS)
    body_path.write_text(body_text + ("\n" if not body_text.endswith("\n") else ""), encoding="utf-8")

    print("Restructured:", llm_path.name, body_path.name)


if __name__ == "__main__":
    main()
