package com.qlink.support.fixture

import com.qlink.folder.domain.Folder
import kotlin.time.Instant

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
        sharedAt: Instant? = null,
        createdAt: Instant? = null,
    ): Folder =
        Folder(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            name = name,
            emoji = emoji,
            sharedAt = sharedAt,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    fun createSharedFolderOf(
        ownerId: Long,
        name: String = RandomFixture.randomSentenceWithMax(100),
    ): Folder = createFolderWith(ownerId = ownerId, name = name, sharedAt = RandomFixture.randomPastInstant())

    fun createImportedFolderOf(
        ownerId: Long,
        name: String = "${RandomFixture.randomSentenceWithMax(80)} ${RandomFixture.randomId()}",
        createdAt: Instant = RandomFixture.randomPastInstant(),
    ): Folder = Folder.createImported(ownerId = ownerId, name = name, createdAt = createdAt)
}
