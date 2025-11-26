package me.pavekovt.integration.dsl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import me.pavekovt.dto.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.entity.NoteStatus
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
    private val client get() = context.client
    private val baseUrl get() = context.baseUrl

    // ============= DSL Methods =============

    suspend fun conflict(block: suspend ConflictBuilder.() -> Unit): ConflictBuilder {
        val builder = ConflictBuilder(this)
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

    // ============= HTTP Operations =============

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

    suspend fun createConflict(): ConflictDTO =
        client.post("$baseUrl/api/conflicts") {
            authenticatedAs(this@TestUser)
        }.expect(HttpStatusCode.Created).body()

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

    suspend fun submitResolution(conflictId: String, resolutionText: String): ConflictDTO =
        client.post("$baseUrl/api/conflicts/$conflictId/resolutions") {
            authenticatedAs(this@TestUser)
            contentType(ContentType.Application.Json)
            setBody(SubmitResolutionRequest(resolutionText))
        }.expect(HttpStatusCode.OK).body()

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

    suspend fun withFeelings(text: String = "I feel frustrated about this"): ConflictBuilder {
        user.submitFeelings(conflict.id, text)
        return this
    }

    suspend fun withResolution(text: String = "We should talk more openly"): ConflictBuilder {
        user.submitResolution(conflict.id, text)
        return this
    }

    suspend fun assertState(block: suspend ConflictAssertion.() -> Unit) {
        ConflictAssertion(conflict).block()
    }

    suspend fun assertActions(block: suspend ConflictActionsAssertion.() -> Unit) {
        val actions = user.getConflictActions(conflict.id)
        ConflictActionsAssertion(actions).block()
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
        val client = context.client
        val baseUrl = context.baseUrl

        val inviteResponse = client.post("$baseUrl/api/partnerships/invite") {
            authenticatedAs(user1)
            contentType(ContentType.Application.Json)
            setBody(PartnerInviteRequest(user2Email))
        }
        val partnership = inviteResponse.body<PartnershipDTO>()

        client.post("$baseUrl/api/partnerships/${partnership.id}/accept") {
            authenticatedAs(user2)
        }

        partnershipDTO = partnership

        this.block()
    }
}

// ============= Assertions =============

class ConflictAssertion(private val conflict: ConflictDTO) {
    fun hasNextAction(expected: String) {
        assertNotNull(conflict.nextAction, "Next action should not be null")
        assertTrue(conflict.nextAction.contains(expected, ignoreCase = true),
            "Expected next action to contain '$expected' but was '${conflict.nextAction}'")
    }

    fun hasStatus(expected: me.pavekovt.entity.ConflictStatus) {
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
    assertEquals(status, this.status, "HTTP status mismatch")
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
