#!/usr/bin/env python3
"""
Structural docs alignment checker for My Desk Robot.

Exit codes:
  0 = OK (warnings allowed)
  1 = failures found

Usage (from repo root):
  python scripts/check_docs_alignment.py
  python scripts/check_docs_alignment.py --json
  python scripts/check_docs_alignment.py --domain memory
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

AGENTS = ROOT / "AGENTS.md"
DOCS = ROOT / "docs"
GUIDES = DOCS / "guides"
DRAFTS = DOCS / "Drafts"
PROMPTS = ROOT / "app" / "src" / "main" / "assets" / "prompts"
TOOL_KT_GLOB = "app/src/main/java/com/example/mydeskrobot/integration/tool/**/*.kt"

TOOL_NAME_RE = re.compile(
    r'override\s+val\s+name\s*:\s*String\s*=\s*"([a-z][a-z0-9_]*)"'
)
# AGENTS tool table rows: | `name` | LOCAL/REMOTE/HARDWARE |
AGENTS_TOOL_ROW_RE = re.compile(
    r"^\|\s*`([a-z][a-z0-9_]*)`\s*\|\s*(LOCAL|REMOTE|HARDWARE)\s*\|",
    re.MULTILINE,
)
# Markdown links and backtick path refs
MD_LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
BACKTICK_PATH_RE = re.compile(
    r"`((?:docs|app|\.cursor)/[A-Za-z0-9_./\-]+\.(?:md|txt|kt|kts|mdc))`"
)
# docs/FOO.md style without backticks (conservative)
BARE_DOCS_RE = re.compile(r"(?<![`(\w./])(docs/[A-Za-z0-9_./\-]+\.md)")

# Domains for scoped checks (semantic phase uses these paths)
DOMAIN_MAP: dict[str, list[str]] = {
    "memory": [
        "docs/MEMORY.md",
        "docs/MEMORY_ACCESS.md",
        "docs/MEMORY_RECALL_PLANNER.md",
        "docs/MEMORY_EMBEDDING.md",
        "docs/guides/MEMORIA.md",
        "docs/guides/MEMORIA_TECNICA.md",
    ],
    "proactivity": [
        "docs/PROACTIVE_ARCHITECTURE.md",
        "docs/HEARTBEAT_ARCHITECTURE.md",
        "docs/guides/PROATTIVITA.md",
        "docs/TODO.md",
    ],
    "proxy": [
        "docs/NOTIFICATION_PROXY.md",
        "docs/PHONE_CAPABILITY_BRIDGE.md",
        "docs/guides/PROXY_NOTIFICHE.md",
        "docs/INPUT_ARCHITECTURE.md",
    ],
    "mood": [
        "docs/MOOD.md",
        "docs/ROBOT_EXPRESSIONS.md",
        "docs/guides/UMORE.md",
        "docs/guides/UMORE_SMOKE.md",
    ],
    "translation": [
        "docs/TRANSLATION_MODE.md",
        "AGENTS.md",
    ],
    "activity": [
        "docs/ACTIVITY_LOG.md",
        "docs/PROACTIVE_ARCHITECTURE.md",
    ],
    "tools": [
        "docs/TOOL_ARCHITECTURE.md",
        "AGENTS.md",
    ],
    "todo": [
        "docs/TODO.md",
        "AGENTS.md",
    ],
}


@dataclass
class Finding:
    severity: str  # fail | warn
    check: str
    message: str
    path: str = ""


@dataclass
class Report:
    failures: list[Finding] = field(default_factory=list)
    warnings: list[Finding] = field(default_factory=list)
    stats: dict[str, int] = field(default_factory=dict)

    def add(self, finding: Finding) -> None:
        if finding.severity == "fail":
            self.failures.append(finding)
        else:
            self.warnings.append(finding)


def iter_markdown_files() -> list[Path]:
    files: list[Path] = []
    if AGENTS.exists():
        files.append(AGENTS)
    for base in (DOCS,):
        if not base.exists():
            continue
        files.extend(sorted(base.rglob("*.md")))
    rules = ROOT / ".cursor" / "rules"
    if rules.exists():
        files.extend(sorted(rules.glob("*.mdc")))
    return files


def resolve_link(source: Path, target: str) -> Path | None:
    t = target.strip()
    if not t or t.startswith(("#", "http://", "https://", "mailto:")):
        return None
    # strip anchors / titles
    t = t.split()[0].strip("<>")
    if "#" in t:
        t = t.split("#", 1)[0]
    if not t:
        return None
    # absolute-from-repo style
    if t.startswith("docs/") or t.startswith("app/") or t.startswith(".cursor/"):
        return ROOT / t
    if t.startswith("/"):
        return None
    return (source.parent / t).resolve()


def collect_code_tools() -> set[str]:
    tools: set[str] = set()
    tool_root = ROOT / "app/src/main/java/com/example/mydeskrobot/integration/tool"
    if not tool_root.exists():
        return tools
    for path in tool_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in TOOL_NAME_RE.finditer(text):
            tools.add(m.group(1))
    return tools


def collect_agents_tools() -> set[str]:
    if not AGENTS.exists():
        return set()
    text = AGENTS.read_text(encoding="utf-8", errors="replace")
    # Prefer the "## Tool disponibili" section if present
    section = text
    marker = "## Tool disponibili"
    if marker in text:
        section = text.split(marker, 1)[1]
        # stop at next ## heading
        nxt = re.search(r"\n##\s+", section)
        if nxt:
            section = section[: nxt.start()]
    return {m.group(1) for m in AGENTS_TOOL_ROW_RE.finditer(section)}


def collect_prompt_files() -> set[str]:
    if not PROMPTS.exists():
        return set()
    return {p.name for p in PROMPTS.rglob("*.txt")}


def collect_prompt_refs(report: Report) -> set[str]:
    """Prompt filenames mentioned in docs/AGENTS/loader code."""
    refs: set[str] = set()
    prompt_name_re = re.compile(r"([a-z0-9_]+_prompt\.txt|[a-z0-9_]+_prompt\.txt)")
    # broader: any *.txt under prompts context
    any_prompt_re = re.compile(r"`([a-z0-9_]+(?:_prompt)?\.txt)`")
    known = collect_prompt_files()
    scan_paths = list(iter_markdown_files())
    loader = ROOT / "app/src/main/java/com/example/mydeskrobot/data/llm"
    if loader.exists():
        scan_paths.extend(loader.rglob("*.kt"))
    for path in scan_paths:
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in any_prompt_re.finditer(text):
            name = m.group(1)
            if name.endswith(".txt"):
                refs.add(name)
        # also bare filenames that match known prompts
        for name in known:
            if name in text:
                refs.add(name)
    return refs


def check_dead_links(report: Report) -> None:
    checked = 0
    for path in iter_markdown_files():
        text = path.read_text(encoding="utf-8", errors="replace")
        candidates: list[str] = []
        candidates.extend(MD_LINK_RE.findall(text))
        candidates.extend(BACKTICK_PATH_RE.findall(text))
        candidates.extend(BARE_DOCS_RE.findall(text))
        for raw in candidates:
            resolved = resolve_link(path, raw)
            if resolved is None:
                continue
            checked += 1
            try:
                resolved.relative_to(ROOT)
            except ValueError:
                continue
            if not resolved.exists():
                rel = str(path.relative_to(ROOT)).replace("\\", "/")
                report.add(
                    Finding(
                        "fail",
                        "dead_link",
                        f"Missing target: {raw}",
                        rel,
                    )
                )
    report.stats["links_checked"] = checked


def check_tools(report: Report) -> None:
    code = collect_code_tools()
    agents = collect_agents_tools()
    report.stats["tools_code"] = len(code)
    report.stats["tools_agents"] = len(agents)

    missing_in_agents = sorted(code - agents)
    extra_in_agents = sorted(agents - code)

    # Body tools are conditional — still should be documented if in code
    for name in missing_in_agents:
        report.add(
            Finding(
                "fail",
                "tool_missing_in_agents",
                f"Tool `{name}` registered in code but missing from AGENTS.md tool table",
            )
        )
    for name in extra_in_agents:
        report.add(
            Finding(
                "fail",
                "tool_extra_in_agents",
                f"Tool `{name}` listed in AGENTS.md but no `override val name` in integration/tool",
            )
        )


def check_drafts(report: Report) -> None:
    existing = set()
    if DRAFTS.exists():
        existing = {p.name for p in DRAFTS.glob("*.md")}
    # Known retired drafts that must not be cited as live SSOT
    retired = {
        "AgentEvolution-GapAnalysis.md",
        "STT-Analysis.md",
        "AnalysysClaude.md",
        "UNIFIED_MEMORY_RAG_PLAN.md",
        "UNIFIED_MEMORY_RAG_PLAN",
    }
    for path in iter_markdown_files():
        # Skip the "draft rimossi" inventory lines in AGENTS/TODO
        rel = str(path.relative_to(ROOT)).replace("\\", "/")
        text = path.read_text(encoding="utf-8", errors="replace")
        for name in retired:
            if name not in text:
                continue
            # Allow explicit "removed" / "rimosso" / "non più" context in same line
            for line in text.splitlines():
                if name not in line:
                    continue
                lower = line.lower()
                if any(
                    k in lower
                    for k in (
                        "rimosso",
                        "rimossi",
                        "removed",
                        "non più",
                        "obsoleto",
                        "eliminato",
                    )
                ):
                    continue
                if "Drafts/" + name in line or f"`{name}`" in line or name in line:
                    # Citing as if present
                    if name.endswith(".md") and name not in existing:
                        report.add(
                            Finding(
                                "fail",
                                "retired_draft_cite",
                                f"Cites removed draft `{name}` without removal wording",
                                rel,
                            )
                        )


def check_prompts(report: Report) -> None:
    on_disk = collect_prompt_files()
    refs = collect_prompt_refs(report)
    report.stats["prompts_disk"] = len(on_disk)
    report.stats["prompts_refs"] = len(refs)

    # Only fail if a *prompt-looking* ref is missing from disk
    for name in sorted(refs):
        if not name.endswith(".txt"):
            continue
        # Ignore non-prompt assets accidentally matched
        if name not in on_disk and (
            "_prompt" in name or name in {
                "llm_system_prompt.txt",
                "body_capabilities_prompt.txt",
                "heartbeat_playbook_prompt.txt",
            }
        ):
            report.add(
                Finding(
                    "fail",
                    "prompt_missing",
                    f"Referenced prompt `{name}` not found under assets/prompts",
                )
            )

    # Warn on orphan prompts never mentioned (skip wellness domain snippets)
    orphans = on_disk - refs
    for name in sorted(orphans):
        if name in {
            "activity_extractor_prompt.txt",  # alias of episodic in LlmPromptLoader
        }:
            continue
        # Domain prompt snippets loaded via AttentionDomainRepository paths
        rel_files = list(PROMPTS.rglob(name))
        if any("domains" in str(p.relative_to(PROMPTS)).replace("\\", "/") for p in rel_files):
            continue
        report.add(
            Finding(
                "warn",
                "prompt_orphan",
                f"Prompt `{name}` on disk but not referenced in docs/AGENTS/loader",
            )
        )


def check_agents_map_files(report: Report) -> None:
    """Files listed in AGENTS mappa documenti must exist."""
    if not AGENTS.exists():
        return
    text = AGENTS.read_text(encoding="utf-8", errors="replace")
    for m in re.finditer(r"`(docs/[^`]+)`", text):
        rel = m.group(1)
        if not rel.endswith(".md"):
            continue
        path = ROOT / rel
        if not path.exists():
            report.add(
                Finding(
                    "fail",
                    "agents_map_missing",
                    f"AGENTS cites `{rel}` but file missing",
                    "AGENTS.md",
                )
            )


def check_domain_files(report: Report, domain: str | None) -> None:
    if not domain:
        return
    if domain not in DOMAIN_MAP:
        report.add(
            Finding(
                "fail",
                "unknown_domain",
                f"Unknown domain `{domain}`. Known: {', '.join(sorted(DOMAIN_MAP))}",
            )
        )
        return
    for rel in DOMAIN_MAP[domain]:
        path = ROOT / rel
        if not path.exists():
            report.add(
                Finding(
                    "fail",
                    "domain_file_missing",
                    f"Domain `{domain}` expected file missing: {rel}",
                )
            )


def run(domain: str | None = None) -> Report:
    report = Report()
    check_dead_links(report)
    check_tools(report)
    check_drafts(report)
    check_prompts(report)
    check_agents_map_files(report)
    check_domain_files(report, domain)
    return report


def print_human(report: Report) -> None:
    print("=== Docs alignment (structural) ===")
    for k, v in sorted(report.stats.items()):
        print(f"  {k}: {v}")
    print()
    if report.failures:
        print(f"FAILURES ({len(report.failures)}):")
        for f in report.failures:
            loc = f" @ {f.path}" if f.path else ""
            print(f"  [FAIL][{f.check}]{loc} {f.message}")
        print()
    if report.warnings:
        print(f"WARNINGS ({len(report.warnings)}):")
        for f in report.warnings:
            loc = f" @ {f.path}" if f.path else ""
            print(f"  [WARN][{f.check}]{loc} {f.message}")
        print()
    if not report.failures and not report.warnings:
        print("OK - no structural issues found.")
    elif not report.failures:
        print("OK - no failures (warnings only).")
    else:
        print("RESULT: FAILED")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit machine-readable JSON",
    )
    parser.add_argument(
        "--domain",
        choices=sorted(DOMAIN_MAP.keys()),
        help="Also verify expected files for a semantic domain exist",
    )
    args = parser.parse_args()
    report = run(domain=args.domain)
    if args.json:
        payload = {
            "failures": [asdict(f) for f in report.failures],
            "warnings": [asdict(f) for f in report.warnings],
            "stats": report.stats,
            "ok": len(report.failures) == 0,
        }
        print(json.dumps(payload, indent=2, ensure_ascii=False))
    else:
        print_human(report)
    return 1 if report.failures else 0


if __name__ == "__main__":
    sys.exit(main())
