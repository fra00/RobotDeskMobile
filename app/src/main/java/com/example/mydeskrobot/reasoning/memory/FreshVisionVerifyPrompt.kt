package com.example.mydeskrobot.reasoning.memory

/**
 * Prompt block injected when the LLM turn includes a fresh photo for verification.
 * Memorized spatial landmarks must not override what is visible in the current frame.
 */
object FreshVisionVerifyPrompt {

    val SECTION: String = """
VERIFICA VISIVA ISTANTANEA (questo turno — priorità assoluta):
- Descrivi SOLO ciò che è visibile nella foto corrente di questo turno.
- Landmark, luoghi noti e descrizioni stanza in DOVE SONO / MEMORIA sono INVALIDI per "cosa vedo ora".
- Se la foto mostra un soffitto bianco, dì soffitto bianco — non scrivania/finestra/televisione dalla memoria.
- L'identità stanza ("dove siamo") resta valida solo se l'utente chiede posizione senza verifica visiva; in questo turno con foto, rispondi alla scena attuale.
""".trim()
}
