package com.qlink.ai.repository.table

import com.qlink.ai.domain.AvailableModel
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object AvailableModels : Table("available_models") {
    val id = long("id").autoIncrement()
    val providerId = reference("provider_id", AiProviders.id, onDelete = ReferenceOption.CASCADE)
    val modelKey = varchar("model_key", 100)
    val displayName = varchar("display_name", 100)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("available_models_provider_id_idx", false, providerId)
        uniqueIndex("available_models_provider_model_unique", providerId, modelKey)
    }
}

fun ResultRow.toAvailableModelDomain(): AvailableModel =
    AvailableModel(
        id = this[AvailableModels.id],
        providerId = this[AvailableModels.providerId],
        modelKey = this[AvailableModels.modelKey],
        displayName = this[AvailableModels.displayName],
        createdAt = this[AvailableModels.createdAt].toKotlinInstant(),
        updatedAt = this[AvailableModels.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(availableModel: AvailableModel) {
    this[AvailableModels.providerId] = availableModel.providerId
    this[AvailableModels.modelKey] = availableModel.modelKey
    this[AvailableModels.displayName] = availableModel.displayName
}

fun UpdateBuilder<*>.refreshAvailableModelUpdatedAt() {
    this[AvailableModels.updatedAt] = Clock.System.now().toJavaInstant()
}
