package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class SetLinkFavoriteService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
) {
    suspend fun setFavorite(
        loginId: Long,
        linkId: Long,
        isFavorite: Boolean?,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val link = linkRepository.findById(linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
            link.validateOwner(loginId)

            if (isFavorite != null) {
                linkRepository.update(link.changeFavorite(isFavorite))
            }
        }
    }
}
