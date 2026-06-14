#!/usr/bin/env python3
"""Add \"think\": \"\" to one-line JSON examples with chain_status complete and no think field."""
from pathlib import Path
import re

PROMPT = Path(__file__).resolve().parents[1] / "app/src/main/assets/prompts/llm_system_prompt.txt"

def main() -> None:
    text = PROMPT.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    changed = 0
    for line in lines:
        stripped = line.strip()
        if (
            stripped.startswith('{"reply":')
            and '"chain_status": "complete"' in stripped
            and '"think"' not in stripped
        ):
            # Insert after opening brace
            new_line = line.replace('{"reply":', '{"reply":', 1)
            new_line = new_line.replace('{"reply":', '{"reply":', 1)
            idx = new_line.find('{"reply":')
            if idx >= 0:
                insert_at = idx + 1  # after {
                new_line = new_line[:insert_at] + '"think": "", ' + new_line[insert_at:]
                changed += 1
                out.append(new_line)
                continue
        out.append(line)
    PROMPT.write_text("".join(out), encoding="utf-8")
    print(f"Updated {changed} lines in {PROMPT}")

if __name__ == "__main__":
    main()
