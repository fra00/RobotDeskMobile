package com.example.mydeskrobot.integration.llm

import retrofit2.HttpException

object LlmHttpErrors {

    fun formatForLog(throwable: Throwable): String {
        if (throwable is HttpException) {
            val body = runCatching {
                throwable.response()?.errorBody()?.string()?.trim().orEmpty()
            }.getOrElse { "failed to read body: ${it.message}" }
            val path = throwable.response()?.raw()?.request?.url?.encodedPath.orEmpty()
            return buildString {
                append("HTTP ")
                append(throwable.code())
                if (path.isNotBlank()) {
                    append(' ')
                    append(path)
                }
                if (body.isNotBlank()) {
                    append(" — ")
                    append(body.take(MAX_LOG_CHARS))
                }
            }
        }
        return throwable.message?.trim().orEmpty().ifBlank { throwable.toString() }
    }

    private const val MAX_LOG_CHARS = 2_000
}
