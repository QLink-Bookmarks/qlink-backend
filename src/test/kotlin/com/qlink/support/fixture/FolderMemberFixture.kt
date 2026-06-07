package com.qlink.support.fixture

import com.qlink.foldermember.domain.FolderMember
import kotlin.time.Clock

object FolderMemberFixture {
    fun createOwnerMember(
        folderId: Long,
        userId: Long,
        userName: String = RandomFixture.randomSentenceWithMax(50),
    ): FolderMember =
        FolderMember.owner(
            folderId = folderId,
            userId = userId,
            userName = userName,
            joinedAt = Clock.System.now(),
        )

    fun createMember(
        folderId: Long,
        userId: Long,
        userName: String = RandomFixture.randomSentenceWithMax(50),
    ): FolderMember =
        FolderMember.member(
            folderId = folderId,
            userId = userId,
            userName = userName,
            joinedAt = Clock.System.now(),
        )
}
