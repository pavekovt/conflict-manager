package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.SubmitResolutionRequest
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.entity.Conflicts
import me.pavekovt.integration.dsl.testApi
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecisionsApiTest : IntegrationTestBase() {

    @Test
    fun `GET decisions should return decision backlog`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    user1.decisions {
                        isNotEmpty()
                    }.first().let { decision ->
                        assertEquals("active", decision.status)
                        assertEquals(null, decision.reviewedAt)
                    }
                }
            }
        }
    }
//
    @Test
    fun `GET decisions with status filter should return only matching decisions`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    user1.firstDecision {
                        review()
                        assertState {
                            hasStatus("reviewed")
                        }
                    }
                }
            }
        }
    }
//
    @Test
    fun `GET decision by id should return the decision`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    val decision = user1.firstDecision {}
                    user1.decision(decision.id) {
                        assertState {
                            hasId(decision.id)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `GET decision by id should return 404 for non-existent decision`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()

            // When
            val response = user.getDecisionRaw("00000000-0000-0000-0000-000000000000")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `PATCH review should mark decision as reviewed`(): Unit = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    user1.firstDecision {
                        review()
                        assertState {
                            hasStatus("reviewed")
                            hasReviewedAt()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `PATCH archive should archive decision`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    user1.firstDecision {
                        archive()
                        assertState {
                            hasStatus("archived")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `decisions should be visible to both users in the conflict`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                withConflictResolved {
                    Pair(user1.decisions {}, user2.decisions {}).let {
                        assertTrue(it.first.isNotEmpty())
                        assertTrue(it.second.isNotEmpty())

                        assertTrue {
                            it.first.first().id == it.second.first().id
                        }
                    }
                }
            }
        }
    }
}
