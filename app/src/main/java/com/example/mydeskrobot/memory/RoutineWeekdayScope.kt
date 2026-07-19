package com.example.mydeskrobot.memory

/**
 * Detects distinct weekday scopes in Italian ROUTINE memories (e.g. lun-gio vs venerdì).
 * Prevents schedule fragments from being treated as semantic duplicates.
 */
object RoutineWeekdayScope {

    private val DAY_TO_INDEX = mapOf(
        "lunedi" to 1,
        "lun" to 1,
        "martedi" to 2,
        "mar" to 2,
        "mercoledi" to 3,
        "mer" to 3,
        "giovedi" to 4,
        "gio" to 4,
        "venerdi" to 5,
        "ven" to 5,
        "sabato" to 6,
        "sab" to 6,
        "domenica" to 7,
        "dom" to 7,
    )

    private val RANGE_PATTERN = Regex(
        """(?:dal|da)\s+([a-zàèéìòù]+)\s+(?:al|a)\s+([a-zàéìòù]+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns 1=Monday … 7=Sunday mentioned in [value], or empty if no weekday signal.
     */
    fun weekdayIndices(value: String): Set<Int> {
        val normalized = value.lowercase()
            .replace(Regex("""[^a-zàèéìòù0-9\s-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return emptySet()

        val fromRange = mutableSetOf<Int>()
        RANGE_PATTERN.findAll(normalized).forEach { match ->
            val start = dayIndex(match.groupValues[1]) ?: return@forEach
            val end = dayIndex(match.groupValues[2]) ?: return@forEach
            fromRange += expandRange(start, end)
        }
        if (fromRange.isNotEmpty()) return fromRange

        val tokens = normalized.split(Regex("""[\s-]+""")).filter { it.isNotBlank() }
        val fromTokens = tokens.mapNotNull { dayIndex(it) }.toSet()
        return fromTokens
    }

    /**
     * True when both strings name weekdays but refer to non-overlapping days
     * (e.g. lun-gio vs venerdì).
     */
    fun hasDistinctWeekdayScope(a: String, b: String): Boolean {
        val left = weekdayIndices(a)
        val right = weekdayIndices(b)
        if (left.isEmpty() || right.isEmpty()) return false
        return left.intersect(right).isEmpty()
    }

    private fun dayIndex(token: String): Int? {
        val key = stripAccents(token.trim().lowercase())
        return DAY_TO_INDEX[key]
    }

    private fun stripAccents(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun expandRange(start: Int, end: Int): Set<Int> {
        if (start == end) return setOf(start)
        return if (start < end) {
            (start..end).toSet()
        } else {
            (start..7).toSet() + (1..end).toSet()
        }
    }
}
