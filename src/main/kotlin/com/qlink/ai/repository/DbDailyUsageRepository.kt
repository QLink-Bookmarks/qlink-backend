package com.qlink.ai.repository

import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.repository.table.DailyUsages
import com.qlink.ai.repository.table.fromDomain
import com.qlink.ai.repository.table.refreshDailyUsageUpdatedAt
import com.qlink.ai.repository.table.toDailyUsageDomain
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.time.LocalDate

class DbDailyUsageRepository : DailyUsageRepository {
    override suspend fun insert(dailyUsage: DailyUsage): DailyUsage =
        DailyUsages
            .insertReturning { it.fromDomain(dailyUsage) }
            .single()
            .toDailyUsageDomain()

    override suspend fun findByUserIdAndProviderIdAndUsageDate(
        userProviderId: Long,
        modelId: Long,
        usageDate: LocalDate,
    ): DailyUsage? =
        DailyUsages
            .selectAll()
            .where {
                (DailyUsages.userProviderId eq userProviderId) and
                    (DailyUsages.modelId eq modelId) and
                    (DailyUsages.usageDate eq usageDate)
            }.singleOrNull()
            ?.toDailyUsageDomain()

    override suspend fun update(dailyUsage: DailyUsage): DailyUsage =
        DailyUsages
            .updateReturning(where = { DailyUsages.id eq dailyUsage.id!! }) {
                it.fromDomain(dailyUsage)
                it.refreshDailyUsageUpdatedAt()
            }.single()
            .toDailyUsageDomain()
}
