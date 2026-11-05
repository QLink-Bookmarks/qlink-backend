@file:Suppress("ktlint:standard:filename")

package com.qlink.linkcontent.dto

import com.qlink.linkcontent.domain.CollectAction
import com.qlink.linkcontent.domain.CollectOutcome
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent

data class CollectPreparation(
    val action: CollectAction,
    val next: LinkContent?,
)

data class CollectResult(
    val status: ContentStatus?,
    val outcome: CollectOutcome,
    val host: String? = null,
    val cooldownSeconds: Int? = null,
)
