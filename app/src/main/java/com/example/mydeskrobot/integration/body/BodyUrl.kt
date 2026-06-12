package com.example.mydeskrobot.integration.body

object BodyUrl {
    fun normalize(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    fun join(baseUrl: String, path: String): String {
        val base = normalize(baseUrl)
        val cleanPath = path.trim().removePrefix("/")
        return "$base/$cleanPath"
    }
}
