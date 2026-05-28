package com.example.mydeskrobot.domain.vision

data class CapturedImage(
    val jpegBytes: ByteArray,
    val mimeType: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CapturedImage
        return jpegBytes.contentEquals(other.jpegBytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
