package me.pavekovt.integration.dsl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.pavekovt.db.dbQuery
import me.pavekovt.dto.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.entity.Conflicts
import me.pavekovt.entity.NoteStatus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Kotlin DSL for integration tests with idiomatic builders and assertions
 */

class TestContext(
    val baseUrl: String,
    val client: HttpClient
) {
    val users = mutableMapOf<String, TestUser>()
    val partnerships = mutableMapOf<String, TestPartnership>()

    suspend fun user(
        email: String = "${UUID.randomUUID()}@test.com",
        name: String = "User ${UUID.randomUUID().toString().take(8)}",
        block: suspend TestUser.() -> Unit = {}
    ): TestUser {
        val user = registerUser(email, name)
        users[email] = user
        user.block()
        return user
    }

    suspend fun partnership(
        name: String = "partnership-${partnerships.size}",
        block: suspend TestPartnership.() -> Unit
    ): TestPartnership {
        val partnership = TestPartnership(this, name)
        partnerships[name] = partnership
        partnership.block()
        return partnership
    }

    private suspend fun registerUser(email: String, name: String): TestUser {
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(name, email, "password123"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = response.body<AuthResponse>()
        return TestUser(this, email, name, auth.token, auth.user!!.id)
    }
}

// ============= Test User =============

class TestUser(
    private val context: TestContext,
    val email: String,
    val name: String,
    val token: String,
    val id: String
) {
    internal val client get() = context.client
    internal val baseUrl get() = context.baseUrl

    // ============= DSL Methods =============

    suspend fun conflict(block: suspend ConflictBuilder.() -> Unit): ConflictBuilder {
        val builder = ConflictBuilder(this)
        builder.block()
        return builder
    }

    suspend fun firstDecision(block: suspend DecisionBuilder.() -> Unit): DecisionDTO {
        val builder = DecisionBuilder(this, getDecisions().first().id)
        builder.block()
        return builder.decision
    }

    suspend fun decision(id: String, block: suspend DecisionBuilder.() -> Unit): DecisionBuilder {
        val builder = DecisionBuilder(this, id)
        builder.block()
        return builder
    }

    suspend fun decisions(block: suspend DecisionListAssertion.() -> Unit): List<DecisionDTO> {
        val decisions = getDecisions()
        DecisionListAssertion(decisions).block()
        return decisions
    }

    suspend fun note(text: String = "Note ${UUID.randomUUID().toString().take(8)}", block: suspend NoteBuilder.() -> Unit = {}): NoteBuilder {
        val builder = NoteBuilder(this, text)
        builder.block()
        return builder
    }

    suspend fun retrospective(block: suspend RetrospectiveBuilder.() -> Unit): RetrospectiveBuilder {
        val builder = RetrospectiveBuilder(this)
        builder.block()
        return builder
    }

    suspend fun dashboard(block: suspend DashboardAssertion.() -> Unit): DashboardDTO {
        val dashboard = getDashboard()
        DashboardAssertion(dashboard).block()
        return dashboard
    }

    suspend fun journal(
        content: String = "Journal entry ${UUID.randomUUID().toString().take(8)}",
        block: suspend JournalBuilder.() -> Unit = {}
    ): JournalEntryDTO {
        val builder = JournalBuilder(this, content)
        builder.block()
        return builder.entry
    }

    suspend fun partnershipHealth(block: suspend PartnershipHealthAssertion.() -> Unit): PartnershipHealthDTO {
        val health = getPartnershipHealth()
        PartnershipHealthAssertion(health).block()
        return health
    }

    // ============= HTTP Operations - Conflicts =============

    suspend fun createConflict(): ConflictDTO =
        client.post("$baseUrl/api/conflicts") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.Created).body()

    suspend fun createConflictReadyForResolutions(): ConflictDTO {
        val conflict = createConflict()
        // Bypass feelings phase for test convenience
        dbQuery {
            Conflicts.update({ Conflicts.id eq UUID.fromString(conflict.id) }) {
                it[status] = ConflictStatus.PENDING_RESOLUTIONS
            }
        }
        return getConflict(conflict.id)
    }

    suspend fun getConflict(id: String): ConflictDTO =
        client.get("$baseUrl/api/conflicts/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getConflicts(): List<ConflictDTO> =
        client.get("$baseUrl/api/conflicts") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getConflictActions(id: String): ConflictActionsDTO =
        client.get("$baseUrl/api/conflicts/$id/actions") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun submitFeelings(conflictId: String, feelingsText: String): ConflictFeelingsDTO =
        client.post("$baseUrl/api/conflicts/$conflictId/feelings") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(SubmitFeelingsRequest(feelingsText))
        }.expect(HttpStatusCode.OK).body()

    suspend fun submitResolution(conflictId: String, resolutionText: String = "My resolution"): ConflictDTO =
        client.post("$baseUrl/api/conflicts/$conflictId/resolutions") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(SubmitResolutionRequest(resolutionText))
        }.expect(HttpStatusCode.OK).body()

    suspend fun submitResolutionRaw(conflictId: String, resolutionText: String = "My resolution"): HttpResponse =
        client.post("$baseUrl/api/conflicts/$conflictId/resolutions") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(SubmitResolutionRequest(resolutionText))
        }

    suspend fun getConflictSummary(id: String): AISummaryDTO =
        client.get("$baseUrl/api/conflicts/$id/summary") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getConflictSummaryRaw(id: String): HttpResponse =
        client.get("$baseUrl/api/conflicts/$id/summary") {
            authenticatedAs(this@TestUser)
        }

    suspend fun approveSummary(id: String): Boolean =
        client.post("$baseUrl/api/conflicts/$id/approve") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun requestRefinement(id: String): Boolean =
        client.patch("$baseUrl/api/conflicts/$id/request-refinement") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun archiveConflict(id: String): Boolean =
        client.patch("$baseUrl/api/conflicts/$id/archive") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun waitForConflictStatus(
        conflictId: String,
        targetStatus: ConflictStatus,
        maxAttempts: Int = 60,
        delayMs: Long = 500
    ): ConflictDTO {
        repeat(maxAttempts) { attempt ->
            val conflict = getConflict(conflictId)
            if (conflict.status == targetStatus) {
                return conflict
            }
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
            }
        }
        val currentConflict = getConflict(conflictId)
        throw AssertionError("Conflict did not reach status $targetStatus after ${maxAttempts * delayMs}ms. Current status: ${currentConflict.status}")
    }

    // ============= HTTP Operations - Decisions =============

    suspend fun getDecisions(): List<DecisionDTO> =
        client.get("$baseUrl/api/decisions") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getDecisionsRaw(): HttpResponse =
        client.get("$baseUrl/api/decisions") {
            authenticatedAs(this@TestUser)
        }

    suspend fun getDecision(id: String): DecisionDTO =
        client.get("$baseUrl/api/decisions/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getDecisionRaw(id: String): HttpResponse =
        client.get("$baseUrl/api/decisions/$id") {
            authenticatedAs(this@TestUser)
        }

    suspend fun reviewDecision(id: String): DecisionDTO =
        client.patch("$baseUrl/api/decisions/$id/review") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun reviewDecisionRaw(id: String): HttpResponse =
        client.patch("$baseUrl/api/decisions/$id/review") {
            authenticatedAs(this@TestUser)
        }

    suspend fun archiveDecision(id: String): DecisionDTO =
        client.patch("$baseUrl/api/decisions/$id/archive") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun archiveDecisionRaw(id: String): HttpResponse =
        client.patch("$baseUrl/api/decisions/$id/archive") {
            authenticatedAs(this@TestUser)
        }

    // ============= HTTP Operations - Notes =============

    suspend fun createNote(text: String = "Note text"): NoteDTO =
        client.post("$baseUrl/api/notes") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CreateNoteRequest(text))
        }.expect(HttpStatusCode.OK).body()

    suspend fun createNoteRaw(text: String = "Note text"): HttpResponse =
        client.post("$baseUrl/api/notes") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CreateNoteRequest(text))
        }

    suspend fun getNote(id: String): NoteDTO =
        client.get("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getNoteRaw(id: String): HttpResponse =
        client.get("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
        }

    suspend fun updateNoteStatus(id: String, status: NoteStatus): NoteDTO =
        client.patch("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(UpdateNoteRequest(status = status.name.lowercase()))
        }.expect(HttpStatusCode.OK).body()

    suspend fun updateNoteStatusRaw(id: String, status: NoteStatus): HttpResponse =
        client.patch("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(UpdateNoteRequest(status = status.name.lowercase()))
        }

    suspend fun deleteNote(id: String): Boolean =
        client.delete("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun deleteNoteRaw(id: String): HttpResponse =
        client.delete("$baseUrl/api/notes/$id") {
            authenticatedAs(this@TestUser)
        }

    // ============= HTTP Operations - Retrospectives =============

    suspend fun createRetrospective(scheduledDate: String? = null, users: List<String>? = null): RetrospectiveDTO =
        client.post("$baseUrl/api/retrospectives") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CreateRetrospectiveRequest(scheduledDate, users))
        }.expect(HttpStatusCode.OK).body()

    suspend fun createRetrospectiveRaw(scheduledDate: String? = null, users: List<String>? = null): HttpResponse =
        client.post("$baseUrl/api/retrospectives") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CreateRetrospectiveRequest(scheduledDate, users))
        }

    suspend fun getRetrospective(id: String): RetrospectiveDTO =
        client.get("$baseUrl/api/retrospectives/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getRetrospectiveRaw(id: String): HttpResponse =
        client.get("$baseUrl/api/retrospectives/$id") {
            authenticatedAs(this@TestUser)
        }

    suspend fun getRetrospectives(): List<RetrospectiveDTO> =
        client.get("$baseUrl/api/retrospectives") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getRetrospectivesRaw(): HttpResponse =
        client.get("$baseUrl/api/retrospectives") {
            authenticatedAs(this@TestUser)
        }

    suspend fun addNoteToRetro(retroId: String, noteId: String): Boolean =
        client.post("$baseUrl/api/retrospectives/$retroId/notes") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(AddNoteToRetroRequest(noteId))
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun addNoteToRetroRaw(retroId: String, noteId: String): HttpResponse =
        client.post("$baseUrl/api/retrospectives/$retroId/notes") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(AddNoteToRetroRequest(noteId))
        }

    suspend fun getRetroNotes(id: String): RetrospectiveWithNotesDTO =
        client.get("$baseUrl/api/retrospectives/$id/notes") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getRetroNotesRaw(id: String): HttpResponse =
        client.get("$baseUrl/api/retrospectives/$id/notes") {
            authenticatedAs(this@TestUser)
        }

    suspend fun approveRetro(id: String, approvalText: String = "I think it's very good action points"): Boolean =
        client.patch("$baseUrl/api/retrospectives/$id/approve") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(ApproveRetrospectiveRequest(approvalText))
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun completeRetro(id: String, finalSummary: String = "We discussed important topics"): Boolean =
        client.post("$baseUrl/api/retrospectives/$id/complete") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CompleteRetrospectiveRequest(finalSummary))
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun completeRetroRaw(id: String, finalSummary: String = "We discussed important topics"): HttpResponse =
        client.post("$baseUrl/api/retrospectives/$id/complete") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CompleteRetrospectiveRequest(finalSummary))
        }

    suspend fun cancelRetro(id: String): Boolean =
        client.patch("$baseUrl/api/retrospectives/$id/cancel") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun cancelRetroRaw(id: String): HttpResponse =
        client.patch("$baseUrl/api/retrospectives/$id/cancel") {
            authenticatedAs(this@TestUser)
        }

    // ============= HTTP Operations - Partnerships =============

    suspend fun sendInvite(email: String): PartnershipDTO =
        client.post("$baseUrl/api/partnerships/invite") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(PartnerInviteRequest(email))
        }.expect(HttpStatusCode.Created).body()

    suspend fun sendInviteRaw(email: String): HttpResponse =
        client.post("$baseUrl/api/partnerships/invite") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(PartnerInviteRequest(email))
        }

    suspend fun acceptInvite(id: String): PartnershipDTO =
        client.post("$baseUrl/api/partnerships/$id/accept") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun acceptInviteRaw(id: String): HttpResponse =
        client.post("$baseUrl/api/partnerships/$id/accept") {
            authenticatedAs(this@TestUser)
        }

    suspend fun rejectInvite(id: String) {
        client.post("$baseUrl/api/partnerships/$id/reject") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK)
    }

    suspend fun rejectInviteRaw(id: String): HttpResponse =
        client.post("$baseUrl/api/partnerships/$id/reject") {
            authenticatedAs(this@TestUser)
        }

    suspend fun getInvitations(): PartnershipInvitationsDTO =
        client.get("$baseUrl/api/partnerships/invitations") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()

    suspend fun getInvitationsRaw(): HttpResponse =
        client.get("$baseUrl/api/partnerships/invitations") {
            authenticatedAs(this@TestUser)
        }

    suspend fun getCurrentPartnership(): PartnershipDTO? {
        val response = client.get("$baseUrl/api/partnerships/current") {
            authenticatedAs(this@TestUser)
        }
        return if (response.status == HttpStatusCode.OK) {
            response.body<PartnershipDTO>()
        } else null
    }

    suspend fun getCurrentPartnershipRaw(): HttpResponse =
        client.get("$baseUrl/api/partnerships/current") {
            authenticatedAs(this@TestUser)
        }

    suspend fun endPartnership() {
        client.delete("$baseUrl/api/partnerships/current") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK)
    }

    suspend fun endPartnershipRaw(): HttpResponse =
        client.delete("$baseUrl/api/partnerships/current") {
            authenticatedAs(this@TestUser)
        }

    // ============= HTTP Operations - Other =============

    suspend fun getDashboard(): DashboardDTO =
        client.get("$baseUrl/api/dashboard") {
            authenticatedAs(this@TestUser)
        }.body()

    suspend fun getPartnershipHealth(): PartnershipHealthDTO =
        client.get("$baseUrl/api/partnerships/current/health") {
            authenticatedAs(this@TestUser)
        }.body()

    suspend fun getJobStatus(jobId: String): JobStatusDTO =
        client.get("$baseUrl/api/jobs/$jobId") {
            authenticatedAs(this@TestUser)
        }.body()

    suspend fun createJournal(content: String): JournalEntryDTO =
        client.post("$baseUrl/api/journal") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(CreateJournalEntryRequest(content))
        }.expect(HttpStatusCode.OK).body()

    suspend fun completeJournal(id: String): Boolean =
        client.patch("$baseUrl/api/journal/$id/complete") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body<Map<String, Boolean>>()["success"]!!

    suspend fun getJournal(id: String): JournalEntryDTO =
        client.get("$baseUrl/api/journal/$id") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.OK).body()
}

// ============= Builders =============

class ConflictBuilder(private val user: TestUser) {
    lateinit var conflict: ConflictDTO

    suspend fun fetch(id: String): ConflictBuilder {
        conflict = user.getConflict(id)
        return this
    }

    suspend fun create(): ConflictBuilder {
        conflict = user.createConflict()
        return this
    }

    suspend fun createReadyForResolutions(): ConflictBuilder {
        conflict = user.createConflictReadyForResolutions()
        return this
    }

    suspend fun withFeelings(text: String = "I feel frustrated about this"): ConflictBuilder {
        user.submitFeelings(conflict.id, text)
        conflict = user.getConflict(conflict.id)
        return this
    }

    suspend fun withResolution(text: String = "We should talk more openly"): ConflictBuilder {
        conflict = user.submitResolution(conflict.id, text)
        return this
    }

    suspend fun waitForStatus(status: ConflictStatus, maxAttempts: Int = 60, delayMs: Long = 500): ConflictBuilder {
        conflict = user.waitForConflictStatus(conflict.id, status, maxAttempts, delayMs)
        return this
    }

    suspend fun assertState(block: suspend ConflictAssertion.() -> Unit) {
        ConflictAssertion(conflict).block()
    }

    suspend fun assertActions(block: suspend ConflictActionsAssertion.() -> Unit) {
        val actions = user.getConflictActions(conflict.id)
        ConflictActionsAssertion(actions).block()
    }

    suspend fun returningId(): String {
        return conflict.id
    }

    suspend fun summary(block: suspend SummaryBuilder.() -> Unit): SummaryBuilder {
        val builder = SummaryBuilder(user, conflict)
        builder.block()
        return builder
    }
}

class SummaryBuilder(private val user: TestUser, private val conflict: ConflictDTO) {
    lateinit var summary: AISummaryDTO

    init {
        runBlocking {
            summary = user.getConflictSummary(conflict.id)
        }
    }

    suspend fun assertState(block: suspend SummaryAssertion.() -> Unit) {
        SummaryAssertion(summary).block()
    }
}

class DecisionBuilder(private val user: TestUser, private val id: String) {
    lateinit var decision: DecisionDTO

    init {
        kotlinx.coroutines.runBlocking {
            decision = user.getDecision(id)
        }
    }

    suspend fun review(): DecisionBuilder {
        decision = user.reviewDecision(id)
        return this
    }

    suspend fun archive(): DecisionBuilder {
        decision = user.archiveDecision(id)
        return this
    }

    suspend fun assertState(block: suspend DecisionAssertion.() -> Unit) {
        decision = user.getDecision(decision.id)
        DecisionAssertion(decision).block()
    }
}

class NoteBuilder(private val user: TestUser, private val text: String) {
    lateinit var note: NoteDTO

    init {
        kotlinx.coroutines.runBlocking {
            note = user.createNote(text)
        }
    }

    suspend fun markReadyForDiscussion(): NoteBuilder {
        note = user.updateNoteStatus(note.id, NoteStatus.READY_FOR_DISCUSSION)
        return this
    }

    suspend fun markDiscussed(): NoteBuilder {
        note = user.updateNoteStatus(note.id, NoteStatus.DISCUSSED)
        return this
    }

    suspend fun archive(): NoteBuilder {
        note = user.updateNoteStatus(note.id, NoteStatus.ARCHIVED)
        return this
    }

    suspend fun delete(): NoteBuilder {
        user.deleteNote(note.id)
        return this
    }

    suspend fun assertState(block: suspend NoteAssertion.() -> Unit) {
        NoteAssertion(note).block()
    }
}

class RetrospectiveBuilder(private val user: TestUser) {
    lateinit var retrospective: RetrospectiveDTO

    suspend fun create(scheduledDate: String? = null, users: List<String>? = null): RetrospectiveBuilder {
        retrospective = user.createRetrospective(scheduledDate, users)
        return this
    }

    suspend fun fetch(id: String): RetrospectiveBuilder {
        retrospective = user.getRetrospective(id)
        return this
    }

    suspend fun addNote(noteId: String): RetrospectiveBuilder {
        user.addNoteToRetro(retrospective.id, noteId)
        retrospective = user.getRetrospective(retrospective.id)
        return this
    }

    suspend fun complete(finalSummary: String = "We discussed important topics"): RetrospectiveBuilder {
        user.completeRetro(retrospective.id, finalSummary)
        retrospective = user.getRetrospective(retrospective.id)
        return this
    }

    suspend fun cancel(): RetrospectiveBuilder {
        user.cancelRetro(retrospective.id)
        retrospective = user.getRetrospective(retrospective.id)
        return this
    }

    suspend fun approve(): RetrospectiveBuilder {
        user.approveRetro(retrospective.id)
        retrospective = user.getRetrospective(retrospective.id)
        return this
    }

    suspend fun assertState(block: suspend RetrospectiveAssertion.() -> Unit) {
        RetrospectiveAssertion(retrospective).block()
    }

    suspend fun returningId(): String {
        return retrospective.id
    }
}

class JournalBuilder(private val user: TestUser, private val content: String) {
    lateinit var entry: JournalEntryDTO

    init {
        kotlinx.coroutines.runBlocking {
            entry = user.createJournal(content)
        }
    }

    suspend fun complete(): JournalBuilder {
        user.completeJournal(entry.id)
        entry = user.getJournal(entry.id)
        return this
    }

    suspend fun assertPrivacy(block: suspend JournalAssertion.() -> Unit) {
        JournalAssertion(entry).block()
    }
}

class TestPartnership(
    private val context: TestContext,
    val name: String
) {
    lateinit var user1: TestUser
    lateinit var user2: TestUser
    lateinit var partnershipDTO: PartnershipDTO

    suspend fun users(
        user1Email: String = "${UUID.randomUUID()}@test.com",
        user2Email: String = "${UUID.randomUUID()}@test.com",
        user1Name: String = "Partner 1",
        user2Name: String = "Partner 2",
        block: suspend TestPartnership.() -> Unit = {}
    ) {
        user1 = context.user(user1Email, user1Name) {}
        user2 = context.user(user2Email, user2Name) {}

        // Send and accept invite
        val partnership = user1.sendInvite(user2Email)
        user2.acceptInvite(partnership.id)

        partnershipDTO = partnership

        this.block()
    }

    suspend fun withConflictResolved(
        block: suspend TestPartnership.(conflictId: String) -> Unit = {}
    ) {
        users()

        val conflictId = user1.conflict {
            create()
            withFeelings()
        }.returningId()

        user2.conflict {
            fetch(conflictId)
            withFeelings()
        }

        user2.waitForConflictStatus(conflictId, ConflictStatus.PENDING_RESOLUTIONS)

        user1.submitResolution(conflictId)
        user2.submitResolution(conflictId)

        user2.waitForConflictStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)

        user1.approveSummary(conflictId)
        user2.approveSummary(conflictId)

        user2.waitForConflictStatus(conflictId, ConflictStatus.APPROVED)

        block(conflictId)
    }
}

// ============= Assertions =============

class ConflictAssertion(private val conflict: ConflictDTO) {
    fun hasNextAction(expected: String) {
        assertNotNull(conflict.nextAction, "Next action should not be null")
        assertTrue(conflict.nextAction.contains(expected, ignoreCase = true),
            "Expected next action to contain '$expected' but was '${conflict.nextAction}'")
    }

    fun hasStatus(expected: ConflictStatus) {
        assertEquals(expected, conflict.status, "Conflict status mismatch")
    }

    fun myResolutionSubmitted(expected: Boolean = true) {
        assertEquals(expected, conflict.myResolutionSubmitted, "My resolution status mismatch")
    }

    fun partnerResolutionSubmitted(expected: Boolean = true) {
        assertEquals(expected, conflict.partnerResolutionSubmitted, "Partner resolution status mismatch")
    }

    fun summaryAvailable(expected: Boolean = true) {
        assertEquals(expected, conflict.summaryAvailable, "Summary availability status mismatch")
    }

    fun waitingFor(expected: String) {
        assertNotNull(conflict.waitingFor, "Waiting for should not be null")
        assertTrue(conflict.waitingFor.contains(expected, ignoreCase = true),
            "Expected waiting for to contain '$expected' but was '${conflict.waitingFor}'")
    }

    fun allowsAction(action: ConflictAction) {
        assertTrue(conflict.allowedActions.contains(action),
            "Expected conflict to allow action $action but allowed actions are ${conflict.allowedActions}")
    }

    fun doesNotAllowAction(action: ConflictAction) {
        assertTrue(!conflict.allowedActions.contains(action),
            "Expected conflict to not allow action $action but it's in ${conflict.allowedActions}")
    }
}

class ConflictActionsAssertion(private val actions: ConflictActionsDTO) {
    fun canDo(action: ConflictAction, reason: String? = null) {
        val availability = actions.availableActions.find { it.action == action }
        assertNotNull(availability, "Action $action not found in available actions")
        assertTrue(availability.enabled, "Action $action is not enabled: ${availability.reason}")
        if (reason != null) {
            assertEquals(reason, availability.reason)
        }
    }

    fun cannotDo(action: ConflictAction, expectedReason: String? = null) {
        val availability = actions.availableActions.find { it.action == action }
        if (availability != null) {
            assertTrue(!availability.enabled, "Action $action should not be enabled")
            if (expectedReason != null) {
                assertTrue(availability.reason?.contains(expectedReason, ignoreCase = true) == true,
                    "Expected reason to contain '$expectedReason' but was '${availability.reason}'")
            }
        }
    }

    fun hasNextSteps(vararg steps: String) {
        steps.forEach { step ->
            assertTrue(actions.nextSteps.any { it.contains(step, ignoreCase = true) },
                "Expected next steps to contain '$step' but steps were: ${actions.nextSteps}")
        }
    }

    fun progressForMe(block: UserProgressAssertion.() -> Unit) {
        UserProgressAssertion(actions.progress.myProgress).block()
    }

    fun progressForPartner(block: UserProgressAssertion.() -> Unit) {
        UserProgressAssertion(actions.progress.partnerProgress).block()
    }
}

class UserProgressAssertion(private val progress: UserProgress) {
    fun hasFeelingsSubmitted(count: Int) {
        assertEquals(count, progress.feelingsSubmitted, "Feelings submitted count mismatch")
    }

    fun hasResolutionSubmitted(submitted: Boolean) {
        assertEquals(submitted, progress.resolutionSubmitted, "Resolution submitted mismatch")
    }
}

class DecisionAssertion(private val decision: DecisionDTO) {
    fun hasStatus(expected: String) {
        assertEquals(expected, decision.status, "Decision status mismatch")
    }

    fun hasReviewedAt() {
        assertNotNull(decision.reviewedAt, "Decision is not reviewed")
    }

    fun hasId(id: String) {
        assertEquals(id, decision.id, "Id mismatch")
    }

    fun hasSummary(expectedSubstring: String) {
        assertTrue(decision.summary.contains(expectedSubstring, ignoreCase = true),
            "Expected summary to contain '$expectedSubstring' but was '${decision.summary}'")
    }

    fun isReviewed() {
        assertEquals("reviewed", decision.status, "Expected decision to be reviewed")
        assertNotNull(decision.reviewedAt, "Expected reviewedAt to be set")
    }

    fun isActive() {
        assertEquals("active", decision.status, "Expected decision to be active")
    }

    fun isArchived() {
        assertEquals("archived", decision.status, "Expected decision to be archived")
    }
}

class DecisionListAssertion(private val decisions: List<DecisionDTO>) {
    fun hasCount(expected: Int) {
        assertEquals(expected, decisions.size, "Expected $expected decisions but found ${decisions.size}")
    }

    fun isEmpty() {
        assertTrue(decisions.isEmpty(), "Expected decisions to be empty but found ${decisions.size}")
    }

    fun isNotEmpty() {
        assertTrue(decisions.isNotEmpty(), "Expected decisions to not be empty")
    }

    fun allHaveStatus(expected: String) {
        assertTrue(decisions.all { it.status == expected },
            "Expected all decisions to have status '$expected' but found: ${decisions.map { it.status }}")
    }
}

class NoteAssertion(private val note: NoteDTO) {
    fun hasStatus(expected: NoteStatus) {
        assertEquals(expected.name.lowercase(), note.status, "Note status mismatch")
    }

    fun hasContent(expectedSubstring: String) {
        assertTrue(note.content.contains(expectedSubstring, ignoreCase = true),
            "Expected content to contain '$expectedSubstring' but was '${note.content}'")
    }
}

class RetrospectiveAssertion(private val retro: RetrospectiveDTO) {
    fun hasStatus(expected: String) {
        assertEquals(expected, retro.status, "Retrospective status mismatch")
    }

    fun isCompleted() {
        assertEquals("completed", retro.status, "Expected retrospective to be completed")
        assertNotNull(retro.completedAt, "Expected completedAt to be set")
    }

    fun hasSummary(expected: String) {
        assertEquals(retro.finalSummary, expected, "Final summary mismatch")
    }

    fun isCancelled() {
        assertEquals("cancelled", retro.status, "Expected retrospective to be cancelled")
    }
}

class DashboardAssertion(private val dashboard: DashboardDTO) {
    fun hasPendingActions(count: Int) {
        assertEquals(count, dashboard.pendingActions.size,
            "Expected $count pending actions but found ${dashboard.pendingActions.size}")
    }

    fun hasPendingAction(type: String, actionText: String) {
        val found = dashboard.pendingActions.any {
            it.type == type && it.action.contains(actionText, ignoreCase = true)
        }
        assertTrue(found, "Expected pending action of type '$type' with text '$actionText' not found")
    }

    fun summaryShows(block: DashboardSummaryAssertion.() -> Unit) {
        DashboardSummaryAssertion(dashboard.summary).block()
    }
}

class DashboardSummaryAssertion(private val summary: DashboardSummary) {
    fun activeConflicts(count: Int) {
        assertEquals(count, summary.activeConflicts, "Active conflicts count mismatch")
    }

    fun needingMyAction(count: Int) {
        assertEquals(count, summary.conflictsNeedingMyAction,
            "Conflicts needing my action count mismatch")
    }

    fun awaitingPartner(count: Int) {
        assertEquals(count, summary.conflictsAwaitingPartner,
            "Conflicts awaiting partner count mismatch")
    }
}

class SummaryAssertion(private val summary: AISummaryDTO) {
    fun hasText(expectedText: String) {
        assertTrue(summary.summaryText.contains(expectedText), "Summary doesn't contain: $expectedText")
    }
}

class PartnershipHealthAssertion(private val health: PartnershipHealthDTO) {
    fun isActive() {
        assertTrue(health.isActive, "Partnership should be active")
    }

    fun isNotActive() {
        assertTrue(!health.isActive, "Partnership should not be active")
    }

    fun needsAttention(vararg items: String) {
        items.forEach { item ->
            assertTrue(health.needsAttention.any { it.contains(item, ignoreCase = true) },
                "Expected needs attention to contain '$item' but was: ${health.needsAttention}")
        }
    }

    fun suggests(vararg suggestions: String) {
        suggestions.forEach { suggestion ->
            assertTrue(health.suggestions.any { it.contains(suggestion, ignoreCase = true) },
                "Expected suggestions to contain '$suggestion' but was: ${health.suggestions}")
        }
    }

    fun stats(block: PartnershipStatsAssertion.() -> Unit) {
        PartnershipStatsAssertion(health.stats).block()
    }
}

class PartnershipStatsAssertion(private val stats: PartnershipStats) {
    fun resolvedConflicts(count: Int) {
        assertEquals(count, stats.totalConflictsResolved, "Resolved conflicts count mismatch")
    }

    fun activeConflicts(count: Int) {
        assertEquals(count, stats.activeConflicts, "Active conflicts count mismatch")
    }
}

class JournalAssertion(private val journal: JournalEntryDTO) {
    fun isPrivate() {
        assertEquals(PrivacyLevel.PRIVATE, journal.privacy, "Journal should be private")
    }

    fun hasStatus(status: String) {
        assertEquals(status, journal.status, "Journal status mismatch")
    }
}

// ============= Extensions =============

fun HttpRequestBuilder.authenticatedAs(user: TestUser) {
    header(HttpHeaders.Authorization, "Bearer ${user.token}")
}

suspend fun HttpResponse.expect(status: HttpStatusCode): HttpResponse {
    assertEquals(status, this.status, "HTTP status mismatch, error ${this.bodyAsText()}")
    return this
}

// ============= DSL Entry Point =============

suspend fun testApi(
    baseUrl: String,
    client: HttpClient,
    block: suspend TestContext.() -> Unit
) {
    val context = TestContext(baseUrl, client)
    context.block()
}
