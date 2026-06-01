package com.qlink.support.fixture

import com.qlink.user.domain.User

object UserFixture {
    fun createRandomValidUser(): User =
        User(
            id = RandomFixture.randomId(),
            username = "user-${RandomFixture.randomId()}",
            nickname = RandomFixture.randomSentenceWithMax(50),
            avatarUrl = RandomFixture.randomUrl(),
            avatarEmoji = RandomFixture.randomEmoji(),
        )
}
