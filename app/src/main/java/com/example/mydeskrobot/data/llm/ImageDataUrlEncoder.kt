package com.example.mydeskrobot.data.llm

import android.util.Base64
import com.example.mydeskrobot.domain.vision.CapturedImage

object ImageDataUrlEncoder {
    fun toDataUrl(image: CapturedImage): String {
        val base64 = Base64.encodeToString(image.jpegBytes, Base64.NO_WRAP)
        return "data:${image.mimeType};base64,$base64"
    }
}
