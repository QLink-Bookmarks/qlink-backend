package com.qlink.ai.repository.table

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object AiProviders : Table("ai_providers") {
    val id = long("id").autoIncrement()
    val type = enumerationByName<AiProviderType>("type", 10)
    val baseUrl = varchar("base_url", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ai_providers_type_unique", type)
    }
}

fun ResultRow.toAiProviderDomain(): AiProvider =
    AiProvider(
        id = this[AiProviders.id],
        type = this[AiProviders.type],
        baseUrl = this[AiProviders.baseUrl],
        createdAt = this[AiProviders.createdAt].toKotlinInstant(),
        updatedAt = this[AiProviders.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(aiProvider: AiProvider) {
    this[AiProviders.type] = aiProvider.type
    this[AiProviders.baseUrl] = aiProvider.baseUrl
}

fun UpdateBuilder<*>.refreshAiProviderUpdatedAt() {
    this[AiProviders.updatedAt] = Clock.System.now().toJavaInstant()
}
