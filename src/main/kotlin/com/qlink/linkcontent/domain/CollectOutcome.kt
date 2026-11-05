package com.qlink.linkcontent.domain

enum class CollectOutcome {
    COLLECTED,
    FAILED,
    BLOCKED,
    RETRY_SCHEDULED,
    RELEASED_THROTTLED,
    RELEASED_ERROR,
    GIVE_UP,
    SKIPPED,
    LINK_GONE,
    URL_CHANGED,
}
