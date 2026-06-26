package com.example.mydeskrobot.reasoning.memory

class RecallPlanException(
    val failure: RecallPlanFailure,
) : Exception(failure.toString())

fun RecallPlanFailure.userMessage(): String = when (this) {
    RecallPlanFailure.NotConfigured ->
        "LLM non configurato: impossibile preparare il contesto memoria."
    is RecallPlanFailure.LlmError ->
        "Errore nel preparare il contesto memoria. Riprova."
    RecallPlanFailure.EmptyOutput ->
        "Impossibile preparare il contesto memoria (risposta vuota)."
    is RecallPlanFailure.ParseError ->
        "Impossibile preparare il contesto memoria (piano non valido)."
}
