package com.qlink.ai.service

import com.qlink.ai.worker.AiSummaryCommand
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository

class UpdateLinkAiSummaryService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val dispatcher: AiSummaryDispatcher,
) {
    suspend fun updateLinkAiSummary(
        loginId: Long,
        request: AiSummaryRequest,
    ): AiSummaryResponse {
        tx.readOnly {
            val link = linkRepository.findById(request.linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)

            link.validateOwner(loginId)
        }

        dispatcher.dispatch(
            AiSummaryCommand(
                ownerId = loginId,
                linkId = request.linkId,
                provider = request.provider,
            ),
        )

        return AiSummaryResponse(
            linkId = request.linkId,
            status = AiSummaryStatus.QUEUED,
        )
    }
}
