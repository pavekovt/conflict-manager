package me.pavekovt.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.pavekovt.db.dbQuery
import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.entity.Partnerships
import me.pavekovt.entity.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class PartnershipRepositoryImpl : PartnershipRepository {

    override suspend fun create(userId1: UUID, userId2: UUID, initiatedBy: UUID): UUID = dbQuery {
        // Check if partnership already exists (in any status)
        val existing = Partnerships.selectAll()
            .where {
                ((Partnerships.userId1 eq userId1) and (Partnerships.userId2 eq userId2)) or
                ((Partnerships.userId1 eq userId2) and (Partnerships.userId2 eq userId1))
            }
            .singleOrNull()

        if (existing != null) {
            throw IllegalStateException("Partnership request already exists")
        }

        Partnerships.insertReturning {
            it[Partnerships.userId1] = userId1
            it[Partnerships.userId2] = userId2
            it[status] = "pending"
            it[Partnerships.initiatedBy] = initiatedBy
        }.single()[Partnerships.id].value
    }

    override suspend fun findById(partnershipId: UUID): PartnershipDTO? = dbQuery {
        val partnership = Partnerships.selectAll()
            .where { Partnerships.id eq partnershipId }
            .singleOrNull() ?: return@dbQuery null

        val user1Id = partnership[Partnerships.userId1].value
        val user2Id = partnership[Partnerships.userId2].value

        // Get first user
        val user1Row = Users.selectAll()
            .where { Users.id eq user1Id }
            .singleOrNull() ?: return@dbQuery null

        PartnershipDTO(
            id = partnershipId.toString(),
            partnerId = user2Id.toString(),
            partnerName = user1Row[Users.name],
            partnerEmail = user1Row[Users.email],
            status = partnership[Partnerships.status],
            initiatedByMe = partnership[Partnerships.initiatedBy].value == user1Id,
            createdAt = partnership[Partnerships.createdAt].toString(),
            acceptedAt = partnership[Partnerships.acceptedAt]?.toString()
        )
    }

    override suspend fun findActivePartnership(userId: UUID): PartnershipDTO? = dbQuery {
        val partnershipRow = Partnerships.selectAll()
            .where {
                ((Partnerships.userId1 eq userId) or (Partnerships.userId2 eq userId)) and
                (Partnerships.status eq "active")
            }
            .singleOrNull() ?: return@dbQuery null

        val partnershipId = partnershipRow[Partnerships.id].value
        val user1Id = partnershipRow[Partnerships.userId1].value
        val user2Id = partnershipRow[Partnerships.userId2].value
        val partnerId = if (user1Id == userId) user2Id else user1Id

        val partnerRow = Users.selectAll()
            .where { Users.id eq partnerId }
            .singleOrNull() ?: return@dbQuery null

        PartnershipDTO(
            id = partnershipId.toString(),
            partnerId = partnerId.toString(),
            partnerName = partnerRow[Users.name],
            partnerEmail = partnerRow[Users.email],
            status = partnershipRow[Partnerships.status],
            initiatedByMe = partnershipRow[Partnerships.initiatedBy].value == userId,
            createdAt = partnershipRow[Partnerships.createdAt].toString(),
            acceptedAt = partnershipRow[Partnerships.acceptedAt]?.toString()
        )
    }

    override suspend fun findPendingInvitationsSent(userId: UUID): List<PartnershipDTO> = dbQuery {
        Partnerships.selectAll()
            .where {
                (Partnerships.initiatedBy eq userId) and (Partnerships.status eq "pending")
            }
            .map { row ->
                val partnershipId = row[Partnerships.id].value
                val user1Id = row[Partnerships.userId1].value
                val user2Id = row[Partnerships.userId2].value
                val partnerId = if (user1Id == userId) user2Id else user1Id

                val partnerRow = Users.selectAll()
                    .where { Users.id eq partnerId }
                    .single()

                PartnershipDTO(
                    id = partnershipId.toString(),
                    partnerId = partnerId.toString(),
                    partnerName = partnerRow[Users.name],
                    partnerEmail = partnerRow[Users.email],
                    status = row[Partnerships.status],
                    initiatedByMe = true,
                    createdAt = row[Partnerships.createdAt].toString()
                )
            }
    }

    override suspend fun findPendingInvitationsReceived(userId: UUID): List<PartnershipDTO> = dbQuery {
        Partnerships.selectAll()
            .where {
                ((Partnerships.userId1 eq userId) or (Partnerships.userId2 eq userId)) and
                (Partnerships.initiatedBy neq userId) and
                (Partnerships.status eq "pending")
            }
            .map { row ->
                val partnershipId = row[Partnerships.id].value
                val user1Id = row[Partnerships.userId1].value
                val user2Id = row[Partnerships.userId2].value
                val partnerId = if (user1Id == userId) user2Id else user1Id

                val partnerRow = Users.selectAll()
                    .where { Users.id eq partnerId }
                    .single()

                PartnershipDTO(
                    id = partnershipId.toString(),
                    partnerId = partnerId.toString(),
                    partnerName = partnerRow[Users.name],
                    partnerEmail = partnerRow[Users.email],
                    status = row[Partnerships.status],
                    initiatedByMe = false,
                    createdAt = row[Partnerships.createdAt].toString()
                )
            }
    }

    override suspend fun accept(partnershipId: UUID, userId: UUID): Boolean = dbQuery {
        val partnership = Partnerships.selectAll()
            .where { Partnerships.id eq partnershipId }
            .singleOrNull() ?: return@dbQuery false

        // Verify user is part of this partnership and didn't initiate it
        val user1Id = partnership[Partnerships.userId1].value
        val user2Id = partnership[Partnerships.userId2].value
        val initiatedBy = partnership[Partnerships.initiatedBy].value

        if ((userId != user1Id && userId != user2Id) || userId == initiatedBy) {
            return@dbQuery false
        }

        if (partnership[Partnerships.status] != "pending") {
            return@dbQuery false
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        Partnerships.update({ Partnerships.id eq partnershipId }) {
            it[status] = "active"
            it[acceptedAt] = now
        } > 0
    }

    override suspend fun reject(partnershipId: UUID, userId: UUID): Boolean = dbQuery {
        val partnership = Partnerships.selectAll()
            .where { Partnerships.id eq partnershipId }
            .singleOrNull() ?: return@dbQuery false

        // Verify user is part of this partnership and didn't initiate it
        val user1Id = partnership[Partnerships.userId1].value
        val user2Id = partnership[Partnerships.userId2].value
        val initiatedBy = partnership[Partnerships.initiatedBy].value

        if ((userId != user1Id && userId != user2Id) || userId == initiatedBy) {
            return@dbQuery false
        }

        if (partnership[Partnerships.status] != "pending") {
            return@dbQuery false
        }

        // Delete the partnership request
        Partnerships.deleteWhere { Partnerships.id eq partnershipId } > 0
    }

    override suspend fun end(userId: UUID): Boolean = dbQuery {
        val partnership = Partnerships.selectAll()
            .where {
                ((Partnerships.userId1 eq userId) or (Partnerships.userId2 eq userId)) and
                (Partnerships.status eq "active")
            }
            .singleOrNull() ?: return@dbQuery false

        val partnershipId = partnership[Partnerships.id].value
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)

        Partnerships.update({ Partnerships.id eq partnershipId }) {
            it[status] = "ended"
            it[endedAt] = now
        } > 0
    }

    override suspend fun getPartnerId(userId: UUID): UUID? = dbQuery {
        val partnership = Partnerships.selectAll()
            .where {
                ((Partnerships.userId1 eq userId) or (Partnerships.userId2 eq userId)) and
                (Partnerships.status eq "active")
            }
            .singleOrNull() ?: return@dbQuery null

        val user1Id = partnership[Partnerships.userId1].value
        val user2Id = partnership[Partnerships.userId2].value

        if (user1Id == userId) user2Id else user1Id
    }

    override suspend fun arePartners(userId1: UUID, userId2: UUID): Boolean = dbQuery {
        Partnerships.selectAll()
            .where {
                (((Partnerships.userId1 eq userId1) and (Partnerships.userId2 eq userId2)) or
                ((Partnerships.userId1 eq userId2) and (Partnerships.userId2 eq userId1))) and
                (Partnerships.status eq "active")
            }
            .count() > 0
    }

    private fun ResultRow.toPartnershipDTO(forUserId: UUID): PartnershipDTO {
        val user1Id = this[Partnerships.userId1].value
        val user2Id = this[Partnerships.userId2].value
        val partnerId = if (user1Id == forUserId) user2Id else user1Id

        return PartnershipDTO(
            id = this[Partnerships.id].value.toString(),
            partnerId = partnerId.toString(),
            partnerName = if (user1Id == forUserId) this[Users.name] else this[Users.name],
            partnerEmail = if (user1Id == forUserId) this[Users.email] else this[Users.email],
            status = this[Partnerships.status],
            initiatedByMe = this[Partnerships.initiatedBy].value == forUserId,
            createdAt = this[Partnerships.createdAt].toString(),
            acceptedAt = this[Partnerships.acceptedAt]?.toString()
        )
    }
}
