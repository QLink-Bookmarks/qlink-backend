package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class DeleteLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
) {
    suspend fun deleteLink(
        loginId: Long,
        linkId: Long,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val link = linkRepository.findById(linkId) ?: return@required

            link.validateOwner(loginId)
            linkRepository.deleteById(linkId)
        }
    }
}
