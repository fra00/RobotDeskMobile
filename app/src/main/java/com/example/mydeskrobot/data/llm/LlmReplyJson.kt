package com.example.mydeskrobot.data.llm

import com.squareup.moshi.Json

/** Schema JSON atteso dal modello (campi opzionali oltre a [reply]). */
data class LlmReplyJson(
    val reply: String? = null,
    val text: String? = null,
    val emotion: String? = null,
    @Json(name = "imageRequired")
    val imageRequired: Boolean? = null,
) {
    fun spokenText(): String = reply?.trim().orEmpty().ifBlank { text?.trim().orEmpty() }

    fun needsImage(): Boolean = imageRequired == true
}
