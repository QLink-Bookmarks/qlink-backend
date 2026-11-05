package com.qlink.linkcontent.domain.service

import com.qlink.link.domain.Link
import com.qlink.linkcontent.domain.CollectAction
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.CollectPreparation
import kotlin.time.Instant

object LinkContentCollectDomainService {
    fun prepare(
        claimed: LinkContent,
        link: Link?,
        now: Instant,
    ): CollectPreparation =
        when {
            claimed.hasExceededClaims() -> CollectPreparation(action = CollectAction.GIVE_UP, next = claimed.giveUp())
            link == null -> CollectPreparation(action = CollectAction.LINK_GONE, next = null)
            !link.hasUrl(claimed.sourceUrl) -> CollectPreparation(action = CollectAction.URL_CHANGED, next = claimed.release(now))
            else -> CollectPreparation(action = CollectAction.COLLECT, next = claimed)
        }
}
