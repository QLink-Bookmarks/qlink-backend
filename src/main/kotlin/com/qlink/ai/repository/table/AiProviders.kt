package com.qlink.ai.repository.table

import com.qlink.ai.domain.AiProvider
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
    val name = varchar("name", 50)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ai_providers_name_unique", name)
    }
}

fun ResultRow.toAiProviderDomain(): AiProvider =
    AiProvider(
        id = this[AiProviders.id],
        name = this[AiProviders.name],
        createdAt = this[AiProviders.createdAt].toKotlinInstant(),
        updatedAt = this[AiProviders.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(aiProvider: AiProvider) {
    this[AiProviders.name] = aiProvider.name
}

fun UpdateBuilder<*>.refreshAiProviderUpdatedAt() {
    this[AiProviders.updatedAt] = Clock.System.now().toJavaInstant()
}
