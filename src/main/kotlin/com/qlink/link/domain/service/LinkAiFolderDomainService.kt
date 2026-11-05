package com.qlink.link.domain.service

import com.qlink.folder.domain.Folder
import com.qlink.foldermember.domain.FolderMember
import com.qlink.link.domain.Link

object LinkAiFolderDomainService {
    fun resolve(
        link: Link,
        startedFolderId: Long?,
        candidateFolderId: Long?,
        candidate: Folder?,
        member: FolderMember?,
    ): Long? {
        if (link.hasFolderChangedSince(startedFolderId)) {
            return link.folderId
        }
        if (candidateFolderId == null) return null
        if (candidate == null) return link.folderId
        val memberCanWrite =
            member
                ?.takeIf { it.folderId == candidate.id && it.userId == link.ownerId }
                ?.canWriteLink() == true
        return if (candidate.isLinkWritableBy(userId = link.ownerId, memberCanWrite = memberCanWrite)) {
            candidate.id
        } else {
            link.folderId
        }
    }
}
