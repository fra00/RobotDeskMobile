package com.example.mydeskrobot.domain.translation

/**
 * Maps free-form user/LLM language names to BCP-47 tags for Android TTS.
 */
object TranslationLanguageMapper {

    data class ResolvedLanguage(
        val languageTag: String,
        val label: String,
    )

    fun resolve(raw: String?): ResolvedLanguage? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val normalized = trimmed.lowercase()
            .replace('_', '-')
            .replace(Regex("\\s+"), " ")

        known(normalized)?.let { return it }

        // Bare ISO 639-1 / regional tag
        if (normalized.matches(Regex("^[a-z]{2}(-[a-z]{2})?$"))) {
            val tag = when (normalized) {
                "en" -> "en-US"
                "it" -> "it-IT"
                "fr" -> "fr-FR"
                "de" -> "de-DE"
                "es" -> "es-ES"
                "pt" -> "pt-PT"
                "nl" -> "nl-NL"
                "pl" -> "pl-PL"
                "ru" -> "ru-RU"
                "ja" -> "ja-JP"
                "zh" -> "zh-CN"
                "ko" -> "ko-KR"
                "ar" -> "ar-SA"
                else -> if (normalized.contains('-')) {
                    val parts = normalized.split('-')
                    "${parts[0]}-${parts[1].uppercase()}"
                } else {
                    "$normalized-${normalized.uppercase()}"
                }
            }
            return ResolvedLanguage(languageTag = tag, label = trimmed)
        }

        // Ambiguous but non-empty: default English TTS (LLM still translates to named language)
        return ResolvedLanguage(languageTag = "en-US", label = trimmed)
    }

    private fun known(normalized: String): ResolvedLanguage? = when (normalized) {
        "inglese", "english", "en", "en-us", "en-gb", "en-uk" ->
            ResolvedLanguage(
                languageTag = if (normalized == "en-gb" || normalized == "en-uk") "en-GB" else "en-US",
                label = "English",
            )
        "italiano", "italian", "it", "it-it" ->
            ResolvedLanguage(languageTag = "it-IT", label = "Italian")
        "francese", "french", "fr", "fr-fr" ->
            ResolvedLanguage(languageTag = "fr-FR", label = "French")
        "tedesco", "german", "deutsch", "de", "de-de" ->
            ResolvedLanguage(languageTag = "de-DE", label = "German")
        "spagnolo", "spanish", "español", "espanol", "es", "es-es" ->
            ResolvedLanguage(languageTag = "es-ES", label = "Spanish")
        "portoghese", "portuguese", "pt", "pt-pt", "pt-br" ->
            ResolvedLanguage(
                languageTag = if (normalized == "pt-br") "pt-BR" else "pt-PT",
                label = "Portuguese",
            )
        "olandese", "dutch", "nederlands", "nl", "nl-nl" ->
            ResolvedLanguage(languageTag = "nl-NL", label = "Dutch")
        "polacco", "polish", "pl", "pl-pl" ->
            ResolvedLanguage(languageTag = "pl-PL", label = "Polish")
        "russo", "russian", "ru", "ru-ru" ->
            ResolvedLanguage(languageTag = "ru-RU", label = "Russian")
        "giapponese", "japanese", "ja", "ja-jp" ->
            ResolvedLanguage(languageTag = "ja-JP", label = "Japanese")
        "cinese", "chinese", "zh", "zh-cn", "zh-tw" ->
            ResolvedLanguage(
                languageTag = if (normalized == "zh-tw") "zh-TW" else "zh-CN",
                label = "Chinese",
            )
        "coreano", "korean", "ko", "ko-kr" ->
            ResolvedLanguage(languageTag = "ko-KR", label = "Korean")
        "arabo", "arabic", "ar", "ar-sa" ->
            ResolvedLanguage(languageTag = "ar-SA", label = "Arabic")
        else -> null
    }
}
