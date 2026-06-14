#!/usr/bin/env python3
"""Replace literal Italian reply phrases in prompt JSON examples with semantic markers."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LLM = ROOT / "app/src/main/assets/prompts/llm_system_prompt.txt"
BODY = ROOT / "app/src/main/assets/prompts/body_capabilities_prompt.txt"

# Exact reply string -> marker (non-empty replies in JSON examples only)
LLM_REPLY_MAP: dict[str, str] = {
    "Le chiavi erano sul tavolo o sulla scrivania?": "<ASK_MISSING_PARAM>",
    "Ok, ti sveglio alle nove.": "<CONFIRM_REMINDER>",
    "Sono l'una e mezza… il codice può aspettare, vai a pranzo.": "<PROACTIVE_NUDGE>",
    "SEARCH: nessuno davanti.": "SEARCH: <SEARCH_NOTE>",
    "Trovato!": "<SEARCH_FOUND>",
    "Non ti vedo, hai vinto.": "<SEARCH_CONCEDE>",
    "SEARCH: niente in campo, giro a sinistra.": "SEARCH: <SEARCH_NOTE>",
    "Sì, il computer dietro di te è acceso.": "<INFORM_USER>",
    "SEARCH: dito non in campo, giro.": "SEARCH: <SEARCH_NOTE>",
    "Ecco, vedo il tuo dito.": "<INFORM_USER>",
    "Fatto, sveglia alle sette e mezza.": "<CONFIRM_REMINDER>",
    "Ok, ti ricordo tra 10 minuti.": "<CONFIRM_REMINDER>",
    "Ok Francesco, me lo segno.": "<SHORT_ACK>",
    "Il tuo cane si chiama Brina.": "<INFORM_USER>",
    "Cancello quella memoria.": "<SHORT_ACK>",
    "Cerco e rimuovo le memorie sul tuo cane.": "<SHORT_ACK>",
    "Aggiungo latte alla lista.": "<SHORT_ACK>",
    "Ok, segno il latte come comprato.": "<SHORT_ACK>",
    "Ok, prendo nota.": "<SHORT_ACK>",
    "Aggiungo alla todo list.": "<SHORT_ACK>",
    "Ok, lo metto nei task.": "<SHORT_ACK>",
    "Ok, ti ricordo domani alle nove.": "<CONFIRM_REMINDER>",
    "Apro Google.": "<SHORT_ACK>",
    "Apro ANSA.": "<SHORT_ACK>",
    "Apro Spotify.": "<SHORT_ACK>",
    "Apro country su Spotify.": "<SHORT_ACK>",
    "Cerco i Nirvana su Spotify.": "<SHORT_ACK>",
    "Ok, non ti disturberò con le notifiche per questa sessione.": "<SHORT_ACK>",
    "Perfetto, per un'ora non ti leggerò le notifiche.": "<SHORT_ACK>",
    "Capito, modalità lavoro.": "<SHORT_ACK>",
    "Ok, sei in call per un'ora.": "<SHORT_ACK>",
    "Registrato, riunione dalle 12 alle 13.": "<SHORT_ACK>",
    "Ok, torno alla modalità normale.": "<SHORT_ACK>",
    "Cerco sul web.": "<SHORT_ACK>",
    "Leggo la pagina.": "<SHORT_ACK>",
    "Aumento il volume.": "<SHORT_ACK>",
    "A che ora vuoi la sveglia?": "<ASK_MISSING_PARAM>",
    "Ciao! Sto bene, grazie. Tu come stai?": "<MOOD_REPLY: casual>",
    "Non benissimo… mi hai stuzzicato gli occhi.": "<MOOD_REPLY: angry>",
    "Va bene… però la prossima volta stai più attento.": "<MOOD_REPLY: softened>",
    "Promemoria: è ora di prendere le medicine.": "<ANNOUNCE_REMINDER>",
    "Hai un messaggio su WhatsApp da Mario: ci vediamo alle 20?": "<ANNOUNCE_NOTIFICATION>",
    "Occhio, è arrivata una notifica dalla banca.": "<ANNOUNCE_SENSITIVE>",
    "Fra poco è ora di pranzo!": "<PROACTIVE_NUDGE>",
}

BODY_REPLY_MAP: dict[str, str] = {
    "ALIGN: cane a sinistra, display_pan +15.": "ALIGN: <SEARCH_NOTE>",
    "Vedo un cane, si chiama Brina.": "<NARRATE_VISION>",
    "SEARCH: vuoto davanti.": "SEARCH: <SEARCH_NOTE>",
    "Ti ho trovato!": "<SEARCH_FOUND>",
    "SEARCH:left: niente ancora.": "SEARCH: <SEARCH_NOTE>",
    "SEARCH: dito fuori campo.": "SEARCH: <SEARCH_NOTE>",
    "SCAN_LEFT: finestra e parete sinistra.": "SCAN_LEFT: <SEARCH_NOTE>",
    "A sinistra la finestra, davanti la scrivania con il monitor, a destra la libreria.": "<NARRATE_VISION>",
}

MARKER_LEGEND_LLM = """### Reply markers in examples
Examples show JSON shape only. In real output, replace every <MARKER> with your own short Italian — never output the marker literally.
- <SHORT_ACK> — brief confirmation for a simple tool action
- <CONFIRM_REMINDER> — acknowledge alarm/reminder scheduled
- <ASK_MISSING_PARAM> — ask user for missing info (action none)
- <INFORM_USER> — factual answer after tools or memory
- <NARRATE_VISION> — interpret a scene (not an object inventory)
- SEARCH:/ALIGN:/SCAN_*: <SEARCH_NOTE> — one-line internal history note (not spoken)
- <SEARCH_FOUND> — target located after search
- <SEARCH_CONCEDE> — honest give-up after search budget exhausted
- <PROACTIVE_NUDGE> — brief optional heartbeat suggestion
- <ANNOUNCE_REMINDER> — scheduled_task fired
- <ANNOUNCE_NOTIFICATION> — relay notification content safely
- <ANNOUNCE_SENSITIVE> — bank/OTP — no sensitive details in voice
- <MOOD_REPLY: casual|angry|softened> — persona tone (match STATO ROBOT)
Keep reply "" exactly where shown — silence is a hard rule, not a placeholder.

"""

MARKER_LEGEND_BODY = """### Reply markers in examples
Same markers as main system prompt. Replace <MARKER> with your Italian; keep reply "" for pure body moves.

"""

REQUIRED_FORMAT_OLD = (
    '  "reply": "testo cortissimo, estremamente naturale e colloquiale, '
    'come una conversazione reale tra amici; evita convenevoli da IA",'
)
REQUIRED_FORMAT_NEW = '  "reply": "<YOUR_ITALIAN_REPLY>",'

WEEKLY_REFLECTION_OLD = (
    '{"reply": "", "emotion": "neutral", "speak_confidence": 0.0, '
    '"action": {"type": "tool", "name": "save_memory", '
    '"args": {"fact": "L\'utente apprezza promemoria per il cane e meteo mattutino, '
    'ma ignora suggerimenti sulle serie TV"}}}'
)
WEEKLY_REFLECTION_NEW = (
    '{"reply": "", "emotion": "neutral", "speak_confidence": 0.0, '
    '"action": {"type": "tool_call", "tools": [{"name": "save_memory", '
    '"params": {"value": "L\'utente apprezza promemoria per il cane e meteo mattutino, '
    'ma ignora suggerimenti sulle serie TV", "category": "PREFERENCE"}, '
    '"await_result": true}], "chain_status": "complete"}}'
)

SPEAK_LIKE_HUMAN_ADDITION = (
    "- Do not reuse fixed openers from examples (\"Ok\", \"Apro\", \"Aggiungo\", \"Certo\"); "
    "vary wording every turn.\n"
)


def apply_reply_map(text: str, mapping: dict[str, str]) -> str:
    for old, new in mapping.items():
        needle = f'"reply": "{old}"'
        replacement = f'"reply": "{new}"'
        count = text.count(needle)
        if count == 0:
            raise ValueError(f"Missing reply example: {old!r}")
        text = text.replace(needle, replacement)
    return text


def patch_llm(text: str) -> str:
    if REQUIRED_FORMAT_OLD not in text:
        raise ValueError("Required format block not found")
    text = text.replace(REQUIRED_FORMAT_OLD, REQUIRED_FORMAT_NEW, 1)

    if "## 7. Tool Call Examples (illustrative only)\n\nUser:" not in text:
        raise ValueError("§7 header not found")
    text = text.replace(
        "## 7. Tool Call Examples (illustrative only)\n\n",
        "## 7. Tool Call Examples (illustrative only)\n\n" + MARKER_LEGEND_LLM,
        1,
    )

    if SPEAK_LIKE_HUMAN_ADDITION.strip() in text:
        raise ValueError("Speak-like-human addition already present")
    text = text.replace(
        "### Speak like a human\n",
        "### Speak like a human\n" + SPEAK_LIKE_HUMAN_ADDITION,
        1,
    )

    text = apply_reply_map(text, LLM_REPLY_MAP)

    if WEEKLY_REFLECTION_OLD not in text:
        raise ValueError("Weekly reflection example not found")
    text = text.replace(WEEKLY_REFLECTION_OLD, WEEKLY_REFLECTION_NEW, 1)

    return text


def patch_body(text: str) -> str:
    if "## 6. Illustrative Examples\n\n### JSON examples\n\n" not in text:
        raise ValueError("Body §6 header not found")
    text = text.replace(
        "## 6. Illustrative Examples\n\n### JSON examples\n\n",
        "## 6. Illustrative Examples\n\n### JSON examples\n\n" + MARKER_LEGEND_BODY,
        1,
    )
    return apply_reply_map(text, BODY_REPLY_MAP)


def main() -> None:
    llm_text = LLM.read_text(encoding="utf-8")
    body_text = BODY.read_text(encoding="utf-8")

    llm_text = patch_llm(llm_text)
    body_text = patch_body(body_text)

    LLM.write_text(llm_text, encoding="utf-8")
    BODY.write_text(body_text, encoding="utf-8")
    print("Applied reply markers to", LLM.name, "and", BODY.name)


if __name__ == "__main__":
    main()
