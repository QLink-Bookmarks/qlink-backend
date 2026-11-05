package com.qlink.folder.domain.service

import com.qlink.folder.domain.Folder
import com.qlink.folder.domain.FolderDeleteOption
import com.qlink.folder.domain.FolderDeletionOptions
import com.qlink.folder.dto.FolderDeletionPlan
import com.qlink.link.domain.Link

object FolderDeletionDomainService {
    fun plan(
        source: Folder,
        links: List<Link>,
        callerId: Long,
        options: FolderDeletionOptions,
    ): FolderDeletionPlan {
        val (ownedLinks, remainingLinks) = links.partition { it.isOwnedBy(callerId) }
        val callerLinks = ownedLinks.map { it.id!! }
        val otherLinks = remainingLinks.map { it.id!! }
        return when (options.option) {
            FolderDeleteOption.NULL -> {
                FolderDeletionPlan(
                    deleteLinkIds = emptyList(),
                    moveLinkIds = emptyList(),
                    detachLinkIds = links.map { it.id!! },
                )
            }

            FolderDeleteOption.CASCADE -> {
                FolderDeletionPlan(
                    deleteLinkIds = if (source.isShared) links.map { it.id!! } else callerLinks,
                    moveLinkIds = emptyList(),
                    detachLinkIds = if (source.isShared) emptyList() else otherLinks,
                )
            }

            FolderDeleteOption.MOVE -> {
                FolderDeletionPlan(
                    deleteLinkIds = emptyList(),
                    moveLinkIds = callerLinks,
                    detachLinkIds = otherLinks,
                )
            }
        }
    }
}
