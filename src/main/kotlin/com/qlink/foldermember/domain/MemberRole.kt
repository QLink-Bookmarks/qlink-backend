package com.qlink.foldermember.domain

enum class MemberRole {
    OWNER,
    MEMBER,
    ;

    fun canWriteLink(): Boolean =
        when (this) {
            OWNER, MEMBER -> true
        }
}
