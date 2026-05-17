package support.fixture

import com.qlink.user.domain.User

object UserFixture {

    fun createRandomValidUser(): User {
        return User(
            id = RandomFixture.randomId(),
            displayName = RandomFixture.randomSentenceWithMax(50),
            avatarUrl = RandomFixture.randomUrl(),
            avatarEmoji = RandomFixture.randomEmoji(),
        )
    }

}