package com.qlink.support.fixture

import com.qlink.folder.domain.Folder
import com.qlink.support.fixture.RandomFixture

object FolderFixture {
    fun createValidUnsharedFolder(ownerId: Long): Folder =
        Folder(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            name = RandomFixture.randomSentenceWithMax(100),
            emoji = RandomFixture.randomEmoji(),
        )

    fun createFolderWith(
        ownerId: Long,
        name: String = RandomFixture.randomSentenceWithMax(100),
        emoji: String? = RandomFixture.randomEmoji(),
        sharedAt: kotlin.time.Instant? = null,
    ): Folder =
        Folder(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            name = name,
            emoji = emoji,
            sharedAt = sharedAt,
        )
}
