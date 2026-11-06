package com.qlink.linkcontent.domain.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requirePresent
import com.qlink.link.domain.Link
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import kotlin.time.Instant

object LinkContentPlanDomainService {
    fun forUrlChange(
        current: LinkContent?,
        savedLink: Link,
        now: Instant,
    ): LinkContent? =
        when {
            current == null -> {
                LinkContent.create(
                    linkId = savedLink.id.requirePresent(ErrorCode.LINK_CONTENT_SAVED_LINK_ID_REQUIRED),
                    sourceUrl = savedLink.url,
                    priority = ContentPriority.INTERACTIVE,
                    now = now,
                )
            }

            savedLink.hasUrl(current.sourceUrl) -> {
                null
            }

            else -> {
                current.resetForUrl(sourceUrl = savedLink.url, now = now)
            }
        }
}
