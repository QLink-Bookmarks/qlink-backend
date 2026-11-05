package com.qlink.linkcontent.domain

enum class ContentPriority(
    val value: Int,
) {
    INTERACTIVE(0),
    BULK(1),
    ;

    companion object {
        fun from(value: Int): ContentPriority = entries.first { it.value == value }
    }
}
