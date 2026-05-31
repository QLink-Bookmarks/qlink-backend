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
    val model = varchar("model", 100)
    val priority = integer("priority")
    val rpdLimit = integer("rpd_limit").nullable()
    val tpdLimit = integer("tpd_limit").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("available_models_provider_id_idx", false, providerId)
        uniqueIndex("available_models_provider_model_unique", providerId, model)
    }
}

fun ResultRow.toAvailableModelDomain(): AvailableModel =
    AvailableModel(
        id = this[AvailableModels.id],
        providerId = this[AvailableModels.providerId],
        model = this[AvailableModels.model],
        priority = this[AvailableModels.priority],
        rpdLimit = this[AvailableModels.rpdLimit],
        tpdLimit = this[AvailableModels.tpdLimit],
        createdAt = this[AvailableModels.createdAt].toKotlinInstant(),
        updatedAt = this[AvailableModels.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(availableModel: AvailableModel) {
    this[AvailableModels.providerId] = availableModel.providerId
    this[AvailableModels.model] = availableModel.model
    this[AvailableModels.priority] = availableModel.priority
    this[AvailableModels.rpdLimit] = availableModel.rpdLimit
    this[AvailableModels.tpdLimit] = availableModel.tpdLimit
}

fun UpdateBuilder<*>.refreshAvailableModelUpdatedAt() {
    this[AvailableModels.updatedAt] = Clock.System.now().toJavaInstant()
}
