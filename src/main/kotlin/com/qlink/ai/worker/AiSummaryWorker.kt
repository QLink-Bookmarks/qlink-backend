package com.qlink.ai.worker

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryPrompt
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AiSummaryWorker(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val aiClientRouter: AiClientRouter,
    private val channel: Channel<AiSummaryCommand>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            for (command in channel) {
                runCatching { handle(command) }
                    .onFailure {
                        logger.warn(
                            "Failed to process AI summary. ownerId={}, linkId={}, provider={}",
                            command.ownerId,
                            command.linkId,
                            command.provider,
                            it,
                        )
                    }
            }
        }
    }

    private suspend fun handle(command: AiSummaryCommand) {
        val link =
            tx.readOnly {
                linkRepository.findById(command.linkId)
            } ?: return

        if (link.ownerId != command.ownerId) {
            logger.warn("Skip AI summary for owner mismatch. ownerId={}, linkId={}", command.ownerId, command.linkId)
            return
        }

        val summary =
            aiClientRouter.summarize(
                provider = command.provider,
                prompt =
                    AiSummaryPrompt(
                        url = link.url,
                        title = link.title,
                        memo = link.memo,
                        tags = link.tags,
                    ),
            )

        tx.required {
            val latest = linkRepository.findById(command.linkId) ?: return@required
            if (latest.ownerId != command.ownerId) {
                return@required
            }

            linkRepository.update(
                latest.update(
                    folderId = latest.folderId,
                    url = latest.url,
                    title = latest.title,
                    summary = summary,
                    memo = latest.memo,
                    tags = latest.tags,
                    thumbnailUrl = latest.thumbnailUrl,
                    sourceType = latest.sourceType,
                ),
            )
        }
    }
}
