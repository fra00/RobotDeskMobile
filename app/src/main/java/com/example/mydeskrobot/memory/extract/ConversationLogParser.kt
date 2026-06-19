package com.example.mydeskrobot.memory.extract

/**
 * Parses the in-app conversation log into structured entries for background extractors.
 */
object ConversationLogParser {

    private val systemLineRegex = Regex(
        pattern = """^Sistema\s*\(([^)]+)\)\s*:\s*(.*)$""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parseAllEntries(conversationLog: String): List<ChatLogEntry> {
        if (conversationLog.isBlank()) return emptyList()
        val lines = conversationLog
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val entries = mutableListOf<ChatLogEntry>()
        var id = 1L
        lines.forEach { line ->
            when {
                line.startsWith("Tu:", ignoreCase = true) -> {
                    entries.add(
                        ChatLogEntry(
                            id = id++,
                            role = "user",
                            text = line.removePrefix("Tu:").trim(),
                        ),
                    )
                }
                line.startsWith("Robot:", ignoreCase = true) -> {
                    entries.add(
                        ChatLogEntry(
                            id = id++,
                            role = "assistant",
                            text = line.removePrefix("Robot:").trim(),
                        ),
                    )
                }
                else -> {
                    val systemMatch = systemLineRegex.matchEntire(line)
                    if (systemMatch != null) {
                        val channel = systemMatch.groupValues[1].trim()
                        val body = systemMatch.groupValues[2].trim()
                        entries.add(
                            ChatLogEntry(
                                id = id++,
                                role = "system",
                                text = "$channel: $body",
                            ),
                        )
                    }
                }
            }
        }
        return entries
    }

    fun parseUserAssistantEntries(conversationLog: String): List<ChatLogEntry> =
        parseAllEntries(conversationLog).filter { it.role == "user" || it.role == "assistant" }
}
