package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Stores compacted relationship context summaries for partnerships.
 * Updated after each conflict resolution and retrospective completion.
 * Used by AI to provide context-aware advice for new conflicts.
 */
object PartnershipContext : UUIDTable("partnership_context") {
    val partnershipId = reference("partnership_id", Partnerships)
    val compactedSummary = text("compacted_summary") // AI-generated summary of relationship patterns, themes, and dynamics
    val lastUpdatedAt = datetime("last_updated_at").defaultExpression(CurrentDateTime)
    val conflictCount = integer("conflict_count").default(0) // Track number of conflicts included in context
    val retroCount = integer("retro_count").default(0) // Track number of retros included in context
}
