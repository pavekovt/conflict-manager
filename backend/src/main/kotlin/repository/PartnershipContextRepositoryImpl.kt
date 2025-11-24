package me.pavekovt.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.pavekovt.db.dbQuery
import me.pavekovt.entity.PartnershipContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class PartnershipContextRepositoryImpl : PartnershipContextRepository {

    override suspend fun getContext(partnershipId: UUID): PartnershipContextData? = dbQuery {
        PartnershipContext.selectAll()
            .where { PartnershipContext.partnershipId eq partnershipId }
            .singleOrNull()
            ?.let { row ->
                PartnershipContextData(
                    id = row[PartnershipContext.id].value,
                    partnershipId = row[PartnershipContext.partnershipId].value,
                    compactedSummary = row[PartnershipContext.compactedSummary],
                    conflictCount = row[PartnershipContext.conflictCount],
                    retroCount = row[PartnershipContext.retroCount]
                )
            }
    }

    override suspend fun upsertContext(
        partnershipId: UUID,
        compactedSummary: String,
        incrementConflictCount: Boolean,
        incrementRetroCount: Boolean
    ): UUID = dbQuery {
        val existing = PartnershipContext.selectAll()
            .where { PartnershipContext.partnershipId eq partnershipId }
            .singleOrNull()

        if (existing != null) {
            // Update existing context
            val currentConflictCount = existing[PartnershipContext.conflictCount]
            val currentRetroCount = existing[PartnershipContext.retroCount]

            PartnershipContext.update({ PartnershipContext.partnershipId eq partnershipId }) {
                it[PartnershipContext.compactedSummary] = compactedSummary
                it[lastUpdatedAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                if (incrementConflictCount) {
                    it[conflictCount] = currentConflictCount + 1
                }
                if (incrementRetroCount) {
                    it[retroCount] = currentRetroCount + 1
                }
            }

            existing[PartnershipContext.id].value
        } else {
            // Create new context
            PartnershipContext.insertReturning {
                it[PartnershipContext.partnershipId] = partnershipId
                it[PartnershipContext.compactedSummary] = compactedSummary
                it[conflictCount] = if (incrementConflictCount) 1 else 0
                it[retroCount] = if (incrementRetroCount) 1 else 0
            }.single()[PartnershipContext.id].value
        }
    }

    override suspend fun exists(partnershipId: UUID): Boolean = dbQuery {
        PartnershipContext.selectAll()
            .where { PartnershipContext.partnershipId eq partnershipId }
            .count() > 0
    }
}
