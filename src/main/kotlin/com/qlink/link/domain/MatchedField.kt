package com.qlink.link.domain

/** 검색어가 일치한 필드. 앱 내부 판단용이며 화면에 표시하지 않는다. */
enum class MatchedField {
    TITLE,
    URL,
    TAGS,
    SUMMARY,
    MEMO,
    CONTENT,
}
