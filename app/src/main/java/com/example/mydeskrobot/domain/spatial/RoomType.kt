package com.example.mydeskrobot.domain.spatial

enum class RoomType {
    BEDROOM,
    STUDY,
    KITCHEN,
    LIVING_ROOM,
    BATHROOM,
    HALLWAY,
    UNKNOWN,
    ;

    companion object {
        fun fromRaw(value: String?): RoomType {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.find { it.name.equals(value.trim(), ignoreCase = true) }
                ?: when (value.trim().lowercase()) {
                    "camera", "bedroom" -> BEDROOM
                    "studio", "study", "ufficio", "laboratorio" -> STUDY
                    "cucina", "kitchen" -> KITCHEN
                    "soggiorno", "salotto", "living_room" -> LIVING_ROOM
                    "bagno", "bathroom" -> BATHROOM
                    "corridoio", "ingresso", "hallway" -> HALLWAY
                    else -> UNKNOWN
                }
        }

        fun displayLabel(type: RoomType): String = when (type) {
            BEDROOM -> "camera"
            STUDY -> "studio"
            KITCHEN -> "cucina"
            LIVING_ROOM -> "soggiorno"
            BATHROOM -> "bagno"
            HALLWAY -> "corridoio"
            UNKNOWN -> "sconosciuta"
        }
    }
}
