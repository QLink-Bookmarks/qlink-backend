package com.qlink.linkcontent.domain.service

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
                    linkId = checkNotNull(savedLink.id) { "저장된 링크에는 id가 있어야 한다" },
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
