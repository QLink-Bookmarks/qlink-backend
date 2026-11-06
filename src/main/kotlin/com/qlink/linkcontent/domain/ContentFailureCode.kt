package com.qlink.linkcontent.domain

enum class ContentFailureCode(
    val isRetryable: Boolean = false,
    val isBlocking: Boolean = false,
) {
    FETCH_TIMEOUT(isRetryable = true),
    TOO_MANY_REDIRECTS,
    TARGET_DENIED,
    UPSTREAM_NOT_FOUND,
    UPSTREAM_SERVER_ERROR(isRetryable = true),
    UPSTREAM_RATE_LIMITED(isRetryable = true),
    UPSTREAM_OTHER_STATUS,
    NOT_HTML,
    CONTENT_TOO_LARGE,
    CONTENT_EMPTY,
    NOT_CONTENT,
    EXTRACTION_FAILED,
    LOGIN_REQUIRED(isBlocking = true),
    PAYWALL(isBlocking = true),
    BOT_WALL(isBlocking = true),
    ACCESS_DENIED(isBlocking = true),
    ROBOTS_NOINDEX(isBlocking = true),
    INVALID_EVENT,
    WORKER_GIVE_UP,
    UNKNOWN,
    ;

    val isRateLimited: Boolean
        get() = this == UPSTREAM_RATE_LIMITED

    companion object {
        fun from(raw: String?): ContentFailureCode = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}
