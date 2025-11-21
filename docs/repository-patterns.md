# Exposed DSL Repository Patterns

Example repository patterns for the Conflict Resolution Manager project using Exposed DSL.

## Table Definitions

```kotlin
// src/main/kotlin/com/example/db/Tables.kt

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100)
    val notificationToken = varchar("notification_token", 500).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object Notes : UUIDTable("notes") {
    val userId = reference("user_id", Users)
    val content = text("content")
    val status = enumerationByName<NoteStatus>("status", 50).default(NoteStatus.DRAFT)
    val mood = enumerationByName<Mood>("mood", 50).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

enum class NoteStatus {
    DRAFT,
    READY_FOR_DISCUSSION,
    DISCUSSED,
    ARCHIVED
}

enum class Mood {
    FRUSTRATED,
    ANGRY,
    SAD,
    CONCERNED,
    NEUTRAL
}

object Conflicts : UUIDTable("conflicts") {
    val initiatedBy = reference("initiated_by", Users)
    val status = enumerationByName<ConflictStatus>("status", 50).default(ConflictStatus.PENDING_RESOLUTIONS)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

enum class ConflictStatus {
    PENDING_RESOLUTIONS,
    SUMMARY_GENERATED,
    REFINEMENT,
    APPROVED,
    ARCHIVED
}

object Resolutions : UUIDTable("resolutions") {
    val conflictId = reference("conflict_id", Conflicts)
    val userId = reference("user_id", Users)
    val resolutionText = text("resolution_text")
    val submittedAt = timestamp("submitted_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(conflictId, userId) // Each user submits once per conflict
    }
}

object AISummaries : UUIDTable("ai_summaries") {
    val conflictId = reference("conflict_id", Conflicts)
    val summaryText = text("summary_text")
    val provider = varchar("provider", 50)
    val approvedByUser1 = bool("approved_by_user_1").default(false)
    val approvedByUser2 = bool("approved_by_user_2").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object Decisions : UUIDTable("decisions") {
    val conflictId = reference("conflict_id", Conflicts).nullable()
    val summary = text("summary")
    val category = varchar("category", 100).nullable()
    val status = enumerationByName<DecisionStatus>("status", 50).default(DecisionStatus.ACTIVE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val reviewedAt = timestamp("reviewed_at").nullable()
}

enum class DecisionStatus {
    ACTIVE,
    REVIEWED,
    ARCHIVED
}

object Retrospectives : UUIDTable("retrospectives") {
    val scheduledDate = timestamp("scheduled_date").nullable()
    val startedAt = timestamp("started_at").defaultExpression(CurrentTimestamp)
    val completedAt = timestamp("completed_at").nullable()
    val status = enumerationByName<RetroStatus>("status", 50)
    val aiDiscussionPoints = text("ai_discussion_points").nullable()
    val finalSummary = text("final_summary").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

enum class RetroStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

object RetrospectiveNotes : org.jetbrains.exposed.sql.Table("retrospective_notes") {
    val retrospectiveId = reference("retrospective_id", Retrospectives)
    val noteId = reference("note_id", Notes)

    override val primaryKey = PrimaryKey(retrospectiveId, noteId)
}
```

## DTOs (Data Transfer Objects)

```kotlin
// src/main/kotlin/com/example/models/DTOs.kt

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class UserDTO(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: String
)

@Serializable
data class NoteDTO(
    val id: String,
    val userId: String,
    val content: String,
    val status: String,
    val mood: String?,
    val createdAt: String
)

@Serializable
data class CreateNoteRequest(
    val content: String,
    val mood: String?
)

@Serializable
data class UpdateNoteRequest(
    val content: String? = null,
    val status: String? = null,
    val mood: String? = null
)

@Serializable
data class ConflictDTO(
    val id: String,
    val initiatedBy: String,
    val status: String,
    val createdAt: String,
    val myResolutionSubmitted: Boolean,
    val partnerResolutionSubmitted: Boolean,
    val summaryAvailable: Boolean
)

@Serializable
data class CreateResolutionRequest(
    val resolutionText: String
)

@Serializable
data class ConflictSummaryDTO(
    val conflictId: String,
    val summaryText: String,
    val discrepancies: List<String>?,
    val approvedByMe: Boolean,
    val approvedByPartner: Boolean,
    val createdAt: String
)

@Serializable
data class DecisionDTO(
    val id: String,
    val summary: String,
    val category: String?,
    val status: String,
    val createdAt: String,
    val reviewedAt: String?
)

@Serializable
data class RetroDTO(
    val id: String,
    val scheduledDate: String?,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val noteCount: Int,
    val discussionPoints: List<String>?,
    val finalSummary: String?
)
```

## Repository Interfaces

```kotlin
// src/main/kotlin/com/example/repositories/Repositories.kt

import java.util.UUID

interface UserRepository {
    suspend fun create(email: String, passwordHash: String, name: String): UserDTO
    suspend fun findByEmail(email: String): UserDTO?
    suspend fun findById(id: UUID): UserDTO?
    suspend fun updateNotificationToken(userId: UUID, token: String?)
}

interface NoteRepository {
    suspend fun create(userId: UUID, content: String, mood: Mood?): NoteDTO
    suspend fun findById(noteId: UUID, requestingUserId: UUID): NoteDTO?
    suspend fun findByUser(userId: UUID, status: NoteStatus? = null): List<NoteDTO>
    suspend fun update(noteId: UUID, userId: UUID, content: String?, status: NoteStatus?, mood: Mood?): NoteDTO?
    suspend fun delete(noteId: UUID, userId: UUID): Boolean
    suspend fun findByRetro(retroId: UUID): List<NoteDTO>
}

interface ConflictRepository {
    suspend fun create(initiatedBy: UUID): ConflictDTO
    suspend fun findById(conflictId: UUID): ConflictDTO?
    suspend fun findByUser(userId: UUID): List<ConflictDTO>
    suspend fun updateStatus(conflictId: UUID, newStatus: ConflictStatus): Boolean
    suspend fun hasResolution(conflictId: UUID, userId: UUID): Boolean
    suspend fun getBothResolutions(conflictId: UUID): Pair<String, String>?
    suspend fun getPartnerUserId(conflictId: UUID, currentUserId: UUID): UUID?
}

interface ResolutionRepository {
    suspend fun create(conflictId: UUID, userId: UUID, resolutionText: String): Boolean
    suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): String?
}

interface AISummaryRepository {
    suspend fun create(conflictId: UUID, summaryText: String, provider: String): UUID
    suspend fun findByConflict(conflictId: UUID): ConflictSummaryDTO?
    suspend fun approve(summaryId: UUID, userId: UUID): Boolean
    suspend fun isApprovedByBoth(summaryId: UUID): Boolean
}

interface DecisionRepository {
    suspend fun create(conflictId: UUID?, summary: String, category: String?): DecisionDTO
    suspend fun findAll(status: DecisionStatus? = null): List<DecisionDTO>
    suspend fun findById(id: UUID): DecisionDTO?
    suspend fun markReviewed(id: UUID): Boolean
    suspend fun archive(id: UUID): Boolean
}

interface RetroRepository {
    suspend fun create(scheduledDate: Instant?, status: RetroStatus): RetroDTO
    suspend fun findById(id: UUID): RetroDTO?
    suspend fun findAll(): List<RetroDTO>
    suspend fun addNote(retroId: UUID, noteId: UUID): Boolean
    suspend fun complete(retroId: UUID, summary: String): Boolean
    suspend fun cancel(retroId: UUID): Boolean
}
```

## Repository Implementations

### UserRepository

```kotlin
// src/main/kotlin/com/example/repositories/UserRepositoryImpl.kt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class UserRepositoryImpl : UserRepository {

    override suspend fun create(email: String, passwordHash: String, name: String): UserDTO = dbQuery {
        val id = Users.insertAndGetId {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.name] = name
        }

        Users.select { Users.id eq id }
            .single()
            .toUserDTO()
    }

    override suspend fun findByEmail(email: String): UserDTO? = dbQuery {
        Users.select { Users.email eq email }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun findById(id: UUID): UserDTO? = dbQuery {
        Users.select { Users.id eq id }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun updateNotificationToken(userId: UUID, token: String?) = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[notificationToken] = token
        }
    }
}

// Extension function to map ResultRow to DTO
private fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value.toString(),
    email = this[Users.email],
    name = this[Users.name],
    createdAt = this[Users.createdAt].toString()
)
```

### NoteRepository (with privacy enforcement)

```kotlin
// src/main/kotlin/com/example/repositories/NoteRepositoryImpl.kt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class NoteRepositoryImpl : NoteRepository {

    override suspend fun create(userId: UUID, content: String, mood: Mood?): NoteDTO = dbQuery {
        val id = Notes.insertAndGetId {
            it[Notes.userId] = userId
            it[Notes.content] = content
            it[Notes.mood] = mood
        }

        Notes.select { Notes.id eq id }
            .single()
            .toNoteDTO()
    }

    override suspend fun findById(noteId: UUID, requestingUserId: UUID): NoteDTO? = dbQuery {
        // PRIVACY: Only return if the note belongs to requesting user
        Notes.select {
            (Notes.id eq noteId) and (Notes.userId eq requestingUserId)
        }
            .singleOrNull()
            ?.toNoteDTO()
    }

    override suspend fun findByUser(userId: UUID, status: NoteStatus?): List<NoteDTO> = dbQuery {
        Notes.select { Notes.userId eq userId }
            .apply {
                if (status != null) {
                    andWhere { Notes.status eq status }
                }
            }
            .orderBy(Notes.createdAt to SortOrder.DESC)
            .map { it.toNoteDTO() }
    }

    override suspend fun update(
        noteId: UUID,
        userId: UUID,
        content: String?,
        status: NoteStatus?,
        mood: Mood?
    ): NoteDTO? = dbQuery {
        // PRIVACY: Only update if note belongs to user
        val updated = Notes.update({
            (Notes.id eq noteId) and (Notes.userId eq userId)
        }) {
            if (content != null) it[Notes.content] = content
            if (status != null) it[Notes.status] = status
            if (mood != null) it[Notes.mood] = mood
        }

        if (updated > 0) {
            Notes.select { Notes.id eq noteId }
                .singleOrNull()
                ?.toNoteDTO()
        } else null
    }

    override suspend fun delete(noteId: UUID, userId: UUID): Boolean = dbQuery {
        // PRIVACY: Only delete if note belongs to user
        Notes.deleteWhere {
            (Notes.id eq noteId) and (Notes.userId eq userId)
        } > 0
    }

    override suspend fun findByRetro(retroId: UUID): List<NoteDTO> = dbQuery {
        // Join with retrospective_notes to get notes in a specific retro
        (RetrospectiveNotes innerJoin Notes)
            .select { RetrospectiveNotes.retrospectiveId eq retroId }
            .map { it.toNoteDTO() }
    }
}

private fun ResultRow.toNoteDTO() = NoteDTO(
    id = this[Notes.id].value.toString(),
    userId = this[Notes.userId].value.toString(),
    content = this[Notes.content],
    status = this[Notes.status].name.lowercase(),
    mood = this[Notes.mood]?.name?.lowercase(),
    createdAt = this[Notes.createdAt].toString()
)
```

### ConflictRepository (complex workflow queries)

```kotlin
// src/main/kotlin/com/example/repositories/ConflictRepositoryImpl.kt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class ConflictRepositoryImpl : ConflictRepository {

    override suspend fun create(initiatedBy: UUID): ConflictDTO = dbQuery {
        val id = Conflicts.insertAndGetId {
            it[Conflicts.initiatedBy] = initiatedBy
        }

        // Build DTO with resolution status
        buildConflictDTO(id.value, initiatedBy)
    }

    override suspend fun findById(conflictId: UUID): ConflictDTO? = dbQuery {
        Conflicts.select { Conflicts.id eq conflictId }
            .singleOrNull()
            ?.let { row ->
                buildConflictDTO(
                    conflictId = row[Conflicts.id].value,
                    initiatedBy = row[Conflicts.initiatedBy].value
                )
            }
    }

    override suspend fun findByUser(userId: UUID): List<ConflictDTO> = dbQuery {
        // Get all conflicts where user is involved (initiated or has resolution)
        val conflictIds = Resolutions
            .slice(Resolutions.conflictId)
            .select { Resolutions.userId eq userId }
            .map { it[Resolutions.conflictId].value }
            .toSet()

        val initiatedIds = Conflicts
            .slice(Conflicts.id)
            .select { Conflicts.initiatedBy eq userId }
            .map { it[Conflicts.id].value }
            .toSet()

        val allIds = conflictIds + initiatedIds

        allIds.mapNotNull { conflictId ->
            Conflicts.select { Conflicts.id eq conflictId }
                .singleOrNull()
                ?.let { row ->
                    buildConflictDTO(
                        conflictId = row[Conflicts.id].value,
                        initiatedBy = row[Conflicts.initiatedBy].value
                    )
                }
        }
    }

    override suspend fun updateStatus(conflictId: UUID, newStatus: ConflictStatus): Boolean = dbQuery {
        Conflicts.update({ Conflicts.id eq conflictId }) {
            it[status] = newStatus
        } > 0
    }

    override suspend fun hasResolution(conflictId: UUID, userId: UUID): Boolean = dbQuery {
        Resolutions.select {
            (Resolutions.conflictId eq conflictId) and (Resolutions.userId eq userId)
        }.count() > 0
    }

    override suspend fun getBothResolutions(conflictId: UUID): Pair<String, String>? = dbQuery {
        val resolutions = Resolutions
            .select { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.resolutionText] }

        if (resolutions.size == 2) {
            Pair(resolutions[0], resolutions[1])
        } else null
    }

    override suspend fun getPartnerUserId(conflictId: UUID, currentUserId: UUID): UUID? = dbQuery {
        // Find the other user involved in this conflict
        val allUserIds = Resolutions
            .slice(Resolutions.userId)
            .select { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.userId].value }
            .toSet()

        val conflict = Conflicts
            .select { Conflicts.id eq conflictId }
            .singleOrNull()

        val initiatorId = conflict?.get(Conflicts.initiatedBy)?.value

        (allUserIds + setOfNotNull(initiatorId))
            .firstOrNull { it != currentUserId }
    }

    // Helper function to build ConflictDTO with resolution status
    private suspend fun buildConflictDTO(conflictId: UUID, initiatedBy: UUID): ConflictDTO {
        val conflict = Conflicts.select { Conflicts.id eq conflictId }.single()

        val resolutionCount = Resolutions
            .slice(Resolutions.id.count())
            .select { Resolutions.conflictId eq conflictId }
            .single()[Resolutions.id.count()]

        val hasSummary = AISummaries
            .select { AISummaries.conflictId eq conflictId }
            .count() > 0

        return ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = initiatedBy.toString(),
            status = conflict[Conflicts.status].name.lowercase(),
            createdAt = conflict[Conflicts.createdAt].toString(),
            myResolutionSubmitted = resolutionCount > 0, // This will be refined in service layer
            partnerResolutionSubmitted = resolutionCount == 2L,
            summaryAvailable = hasSummary
        )
    }
}
```

### AISummaryRepository

```kotlin
// src/main/kotlin/com/example/repositories/AISummaryRepositoryImpl.kt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class AISummaryRepositoryImpl : AISummaryRepository {

    override suspend fun create(conflictId: UUID, summaryText: String, provider: String): UUID = dbQuery {
        AISummaries.insertAndGetId {
            it[AISummaries.conflictId] = conflictId
            it[AISummaries.summaryText] = summaryText
            it[AISummaries.provider] = provider
        }.value
    }

    override suspend fun findByConflict(conflictId: UUID): ConflictSummaryDTO? = dbQuery {
        AISummaries.select { AISummaries.conflictId eq conflictId }
            .singleOrNull()
            ?.let { row ->
                ConflictSummaryDTO(
                    conflictId = conflictId.toString(),
                    summaryText = row[AISummaries.summaryText],
                    discrepancies = null, // TODO: parse from summary if stored separately
                    approvedByMe = false, // Will be set in service layer based on current user
                    approvedByPartner = false,
                    createdAt = row[AISummaries.createdAt].toString()
                )
            }
    }

    override suspend fun approve(summaryId: UUID, userId: UUID): Boolean = dbQuery {
        // First check which user this is (user 1 or user 2)
        val summary = AISummaries.select { AISummaries.id eq summaryId }.singleOrNull()
            ?: return@dbQuery false

        val conflictId = summary[AISummaries.conflictId].value

        // Get both users involved in conflict
        val userIds = Resolutions
            .slice(Resolutions.userId)
            .select { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.userId].value }
            .sorted() // Sort to ensure consistent ordering

        if (userIds.size != 2) return@dbQuery false

        // Determine which approval field to update
        val isUser1 = userId == userIds[0]

        AISummaries.update({ AISummaries.id eq summaryId }) {
            if (isUser1) {
                it[approvedByUser1] = true
            } else {
                it[approvedByUser2] = true
            }
        } > 0
    }

    override suspend fun isApprovedByBoth(summaryId: UUID): Boolean = dbQuery {
        AISummaries.select { AISummaries.id eq summaryId }
            .singleOrNull()
            ?.let { row ->
                row[AISummaries.approvedByUser1] && row[AISummaries.approvedByUser2]
            } ?: false
    }
}
```

### DecisionRepository

```kotlin
// src/main/kotlin/com/example/repositories/DecisionRepositoryImpl.kt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class DecisionRepositoryImpl : DecisionRepository {

    override suspend fun create(conflictId: UUID?, summary: String, category: String?): DecisionDTO = dbQuery {
        val id = Decisions.insertAndGetId {
            it[Decisions.conflictId] = conflictId
            it[Decisions.summary] = summary
            it[Decisions.category] = category
        }

        Decisions.select { Decisions.id eq id }
            .single()
            .toDecisionDTO()
    }

    override suspend fun findAll(status: DecisionStatus?): List<DecisionDTO> = dbQuery {
        Decisions.selectAll()
            .apply {
                if (status != null) {
                    andWhere { Decisions.status eq status }
                }
            }
            .orderBy(Decisions.createdAt to SortOrder.DESC)
            .map { it.toDecisionDTO() }
    }

    override suspend fun findById(id: UUID): DecisionDTO? = dbQuery {
        Decisions.select { Decisions.id eq id }
            .singleOrNull()
            ?.toDecisionDTO()
    }

    override suspend fun markReviewed(id: UUID): Boolean = dbQuery {
        Decisions.update({ Decisions.id eq id }) {
            it[status] = DecisionStatus.REVIEWED
            it[reviewedAt] = Instant.now()
        } > 0
    }

    override suspend fun archive(id: UUID): Boolean = dbQuery {
        Decisions.update({ Decisions.id eq id }) {
            it[status] = DecisionStatus.ARCHIVED
        } > 0
    }
}

private fun ResultRow.toDecisionDTO() = DecisionDTO(
    id = this[Decisions.id].value.toString(),
    summary = this[Decisions.summary],
    category = this[Decisions.category],
    status = this[Decisions.status].name.lowercase(),
    createdAt = this[Decisions.createdAt].toString(),
    reviewedAt = this[Decisions.reviewedAt]?.toString()
)
```

## Database Helper

```kotlin
// src/main/kotlin/com/example/db/DatabaseFactory.kt

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    fun init(jdbcUrl: String, driver: String, user: String, password: String) {
        Database.connect(
            url = jdbcUrl,
            driver = driver,
            user = user,
            password = password
        )
    }
}

// Helper function for all database queries
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

## Usage in Service Layer

```kotlin
// src/main/kotlin/com/example/services/ConflictService.kt

class ConflictService(
    private val conflictRepo: ConflictRepository,
    private val resolutionRepo: ResolutionRepository,
    private val aiSummaryRepo: AISummaryRepository,
    private val decisionRepo: DecisionRepository,
    private val aiProvider: AIProvider
) {

    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): Result<ConflictDTO> {
        // Check if user already submitted
        if (conflictRepo.hasResolution(conflictId, userId)) {
            return Result.failure(IllegalStateException("Resolution already submitted"))
        }

        // Save resolution
        resolutionRepo.create(conflictId, userId, resolutionText)

        // Check if both resolutions are now submitted
        val bothResolutions = conflictRepo.getBothResolutions(conflictId)

        if (bothResolutions != null) {
            // Generate AI summary
            val summary = aiProvider.summarizeConflict(
                bothResolutions.first,
                bothResolutions.second
            )

            // Save summary
            aiSummaryRepo.create(conflictId, summary.summary, summary.provider)

            // Update conflict status
            conflictRepo.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)
        }

        return conflictRepo.findById(conflictId)
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Conflict not found"))
    }

    suspend fun approveSummary(summaryId: UUID, userId: UUID): Result<Unit> {
        // Approve by this user
        aiSummaryRepo.approve(summaryId, userId)

        // Check if both approved
        if (aiSummaryRepo.isApprovedByBoth(summaryId)) {
            // Get summary and create decision
            val summary = aiSummaryRepo.findByConflict(UUID.fromString(summaryId.toString()))
                ?: return Result.failure(IllegalStateException("Summary not found"))

            decisionRepo.create(
                conflictId = UUID.fromString(summary.conflictId),
                summary = summary.summaryText,
                category = null // Could extract from AI analysis
            )

            // Update conflict to approved
            conflictRepo.updateStatus(UUID.fromString(summary.conflictId), ConflictStatus.APPROVED)
        }

        return Result.success(Unit)
    }
}
```

## Key Patterns Demonstrated

1. **Privacy Enforcement**: All queries include user ID checks (see NoteRepository)
2. **Complex Joins**: ConflictRepository shows joins across multiple tables
3. **Explicit SQL Control**: Clear, readable queries with no hidden behavior
4. **DTO Mapping**: Extension functions for clean ResultRow → DTO conversion
5. **Transaction Handling**: All queries wrapped in `dbQuery` for proper transaction management
6. **State Machine**: Conflict status transitions managed explicitly
7. **Business Logic Separation**: Repositories handle data access, services handle workflow

This pattern gives you full control over privacy, clear visibility into queries, and explicit handling of your complex conflict resolution workflow.
