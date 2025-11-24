Concept: Couple's Conflict Resolution Manager with AI-Assisted Retrospectives

  Core Features (v1):
  - Private notes system (things that bother you, frustrations, concerns)
  - Conflict resolution tracking (both partners independently write resolutions)
  - AI-powered summarization of resolutions ("we decided that...")
  - Decision backlog (track all resolutions as reminders)
  - Retrospective system (scheduled reviews of notes + decisions)

  Key Goals:
  - Safe space for both partners to express concerns privately
  - Structured conflict resolution process
  - AI creates neutral summaries to reduce misunderstandings
  - Track decisions to prevent "we already talked about this" situations
  - Regular retrospectives to address small issues before they escalate
  - Mobile-first (capture thoughts immediately)
  - Easy enough for non-tech partner to actually use

  Tech Stack (FINALIZED):
  - Backend: Kotlin + Ktor + Exposed ORM
  - Database: Supabase Postgres (via JDBC)
  - Frontend: Next.js PWA with TypeScript
  - Auth: Custom JWT authentication in Ktor
  - AI: Abstraction layer supporting Claude API & OpenAI (interchangeable)
  - Deployment: Docker

  Priority Add-ons:
  - Push notifications for retro reminders
  - Decision search/filter (find past agreements)
  - Mood tracking alongside notes
  - Export retrospective summaries
  - Scheduled retrospectives (weekly/bi-weekly)

  ## Architecture (Decided)

  Frontend (PWA) → Ktor API → Supabase Postgres + Claude API

  Ktor Backend Responsibilities:
  - User authentication & authorization
  - Note CRUD operations (with privacy controls)
  - Conflict resolution workflow management
  - AI summarization integration (Claude API)
  - Decision backlog management
  - Retrospective generation & scheduling
  - Push notification service (optional)
  - All business logic & privacy enforcement

  Database Schema (Detailed):

  **users**
  - id: UUID (PK)
  - email: VARCHAR (unique, not null)
  - password_hash: VARCHAR (not null)
  - name: VARCHAR (not null)
  - notification_token: VARCHAR (nullable)
  - created_at: TIMESTAMP (not null, default now())

  **notes**
  - id: UUID (PK)
  - user_id: UUID (FK -> users.id, not null)
  - content: TEXT (not null)
  - status: ENUM (draft, ready_for_discussion, discussed, archived) (not null, default 'draft')
  - mood: ENUM (frustrated, angry, sad, concerned, neutral) (nullable)
  - created_at: TIMESTAMP (not null, default now())

  **conflicts**
  - id: UUID (PK)
  - initiated_by: UUID (FK -> users.id, not null)
  - status: ENUM (pending_resolutions, summary_generated, refinement, approved, archived) (not null, default 'pending_resolutions')
  - created_at: TIMESTAMP (not null, default now())

  **resolutions**
  - id: UUID (PK)
  - conflict_id: UUID (FK -> conflicts.id, not null)
  - user_id: UUID (FK -> users.id, not null)
  - resolution_text: TEXT (not null)
  - submitted_at: TIMESTAMP (not null, default now())
  - UNIQUE(conflict_id, user_id) -- each user submits once per conflict

  **ai_summaries**
  - id: UUID (PK)
  - conflict_id: UUID (FK -> conflicts.id, not null)
  - summary_text: TEXT (not null)
  - provider: VARCHAR (not null) -- 'claude' or 'openai'
  - approved_by_user_1: BOOLEAN (not null, default false)
  - approved_by_user_2: BOOLEAN (not null, default false)
  - created_at: TIMESTAMP (not null, default now())

  **decisions**
  - id: UUID (PK)
  - conflict_id: UUID (FK -> conflicts.id, nullable)
  - summary: TEXT (not null)
  - category: VARCHAR (nullable) -- for filtering/searching
  - status: ENUM (active, reviewed, archived) (not null, default 'active')
  - created_at: TIMESTAMP (not null, default now())
  - reviewed_at: TIMESTAMP (nullable)

  **retrospectives**
  - id: UUID (PK)
  - scheduled_date: TIMESTAMP (nullable) -- null if manually triggered
  - started_at: TIMESTAMP (not null, default now())
  - completed_at: TIMESTAMP (nullable)
  - status: ENUM (scheduled, in_progress, completed, cancelled) (not null)
  - ai_discussion_points: TEXT (nullable)
  - final_summary: TEXT (nullable)
  - created_at: TIMESTAMP (not null, default now())

  **retrospective_notes** (junction table)
  - retrospective_id: UUID (FK -> retrospectives.id, not null)
  - note_id: UUID (FK -> notes.id, not null)
  - PRIMARY KEY (retrospective_id, note_id)

  ## Data Flow

  **Note Creation:**
  1. User writes private note about concern/frustration
  2. Note stored with user_id (only visible to author until retro)
  3. User can mark note as "ready for discussion"

  **Conflict Resolution:**
  1. After verbal conflict, either partner initiates resolution process
  2. Both partners independently write their understanding of resolution
  3. System waits for BOTH to submit
  4. AI (Claude) analyzes both resolutions and creates neutral summary
  5. Summary presented: "Based on your inputs, we decided that..."
  6. Both partners approve/refine summary
  7. Final decision saved to backlog

  **Retrospective:**
  1. Scheduled (weekly/bi-weekly) or on-demand
  2. Pulls all pending notes from both partners
  3. Shows decision backlog for review
  4. AI generates discussion points from notes
  5. Partners discuss together
  6. Mark notes as resolved/ongoing
  7. Generate retro summary for future reference

  ## Conflict Resolution State Machine

  **States:**
  1. PENDING_RESOLUTIONS - Conflict initiated, waiting for both partners to submit resolutions
  2. SUMMARY_GENERATED - AI created summary from both resolutions, awaiting approval
  3. REFINEMENT - One or both partners requested changes to summary
  4. APPROVED - Both partners approved summary, decision created and added to backlog
  5. ARCHIVED - Conflict closed without completion (manual action)

  **State Transitions:**
  - PENDING_RESOLUTIONS → SUMMARY_GENERATED (when both resolutions submitted)
  - SUMMARY_GENERATED → APPROVED (when both partners approve)
  - SUMMARY_GENERATED → REFINEMENT (when either partner requests changes)
  - REFINEMENT → SUMMARY_GENERATED (after AI re-generates summary)
  - Any state → ARCHIVED (manual close by either partner)

  **Business Rules:**
  - Cannot view partner's resolution until both submitted
  - Cannot generate AI summary until both resolutions exist
  - Both approvals required to create decision
  - Refinement can happen multiple times before approval

  ## API Endpoints (Complete)

  **Auth:**
  - POST /api/auth/register - create new user account
  - POST /api/auth/login - login and receive JWT tokens
  - POST /api/auth/refresh - refresh access token using refresh token
  - POST /api/auth/logout - invalidate refresh token (optional)

  **Notes:**
  - POST /api/notes - create private note
  - GET /api/notes?status={status} - get my notes (filtered by status)
  - GET /api/notes/:id - get specific note (only mine)
  - PATCH /api/notes/:id - update note (mark ready for discussion)
  - DELETE /api/notes/:id - delete my note

  **Conflicts:**
  - POST /api/conflicts - initiate conflict resolution
  - GET /api/conflicts - list my conflicts (with status)
  - GET /api/conflicts/:id - get conflict details
  - POST /api/conflicts/:id/resolutions - submit my resolution
  - GET /api/conflicts/:id/summary - get AI summary (after both submit)
  - PATCH /api/conflicts/:id/approve - approve final summary
  - PATCH /api/conflicts/:id/request-refinement - request changes to summary
  - PATCH /api/conflicts/:id/archive - close conflict without completion

  **Decisions:**
  - GET /api/decisions?status={status} - get decision backlog (filtered)
  - GET /api/decisions/:id - get specific decision
  - PATCH /api/decisions/:id/review - mark as reviewed
  - PATCH /api/decisions/:id/archive - archive decision

  **Retrospectives:**
  - POST /api/retrospectives - trigger manual retro
  - GET /api/retrospectives - list retrospectives
  - GET /api/retrospectives/:id - get retro details
  - GET /api/retrospectives/:id/notes - get notes included in this retro
  - POST /api/retrospectives/:id/add-note - add note to retro
  - POST /api/retrospectives/:id/complete - finalize retro with summary
  - PATCH /api/retrospectives/:id/cancel - cancel scheduled retro

  **Notifications:**
  - POST /api/notifications/subscribe - register device for push notifications
  - DELETE /api/notifications/unsubscribe - remove device from notifications

  ## Privacy & Security Considerations

  - Notes are PRIVATE until explicitly included in retro
  - Cannot read partner's notes outside of retro context
  - Both partners must submit resolutions before AI summary
  - Decision backlog visible to both (shared agreements)
  - Option to delete notes before they enter retro
  - Retro requires both partners to be "present" (active session)

  ## AI Provider Abstraction Layer

  **Design:**
  Interface-based abstraction to support multiple AI providers (Claude API, OpenAI, etc.)

  ```kotlin
  interface AIProvider {
      suspend fun summarizeConflict(resolution1: String, resolution2: String): SummaryResult
      suspend fun generateRetroPoints(notes: List<Note>): RetroPointsResult
  }

  data class SummaryResult(
      val summary: String,
      val provider: String,
      val discrepancies: List<String>? = null
  )

  data class RetroPointsResult(
      val discussionPoints: List<DiscussionPoint>,
      val provider: String
  )

  data class DiscussionPoint(
      val theme: String,
      val relatedNotes: List<UUID>,
      val suggestedApproach: String
  )
  ```

  **Implementations:**
  - ClaudeProvider(apiKey: String) - Uses Anthropic Claude API
  - OpenAIProvider(apiKey: String) - Uses OpenAI GPT-4 API

  **Factory:**
  ```kotlin
  object AIProviderFactory {
      fun create(config: AIConfig): AIProvider {
          return when (config.provider) {
              "claude" -> ClaudeProvider(config.apiKey)
              "openai" -> OpenAIProvider(config.apiKey)
              else -> throw IllegalArgumentException("Unknown provider: ${config.provider}")
          }
      }
  }
  ```

  **Configuration (application.conf):**
  ```hocon
  ai {
      provider = "claude"  # or "openai"
      apiKey = ${AI_API_KEY}  # from environment variable
  }
  ```

  ## AI Summarization Strategy

  **Conflict Resolution Summary:**
  - Input: Two independent resolution texts
  - Prompt: "Analyze these two perspectives and create a neutral summary of the agreement. Format the output as 'We decided that...' and highlight any discrepancies between the two accounts."
  - Output: "We decided that..." statement + list of discrepancies (if any)
  - Goal: Find common ground, highlight any differences in understanding

  **Retrospective Discussion Points:**
  - Input: Multiple notes from both partners (content + mood if available)
  - Prompt: "Generate discussion points from these concerns. Group by theme and suggest constructive approaches for each theme."
  - Output: Organized discussion agenda with themes and suggested approaches
  - Goal: Structure the conversation productively and prevent overwhelming discussion

  ## Frontend Decision

  **DECIDED: Next.js**
  - Mature PWA ecosystem with proven patterns
  - Excellent TypeScript support
  - Large community and extensive documentation
  - Better for complex state management (conflict workflows)
  - Mobile-first design is CRITICAL (capture thoughts in the moment)
  - PWA features: Service worker, offline support, installable

  ## Development Roadmap

  **Phase 1: Backend Foundation (Week 1-2)**
  1. Scaffold Ktor project structure
     - Set up Gradle build with Kotlin DSL
     - Configure routing, content negotiation, CORS
     - Add logging, error handling middleware
  2. Set up Supabase Postgres connection
     - Configure Exposed ORM
     - Create database migrations
     - Test connection and basic queries
  3. Implement database schema
     - Create all tables with proper constraints
     - Set up foreign keys and indexes
     - Create seed data for testing
  4. Build JWT authentication
     - Password hashing (BCrypt)
     - JWT token generation (access + refresh)
     - Token validation middleware
     - Register, login, refresh endpoints
  5. Create authorization middleware
     - User context injection
     - Privacy enforcement helpers

  **Phase 2: Core Features (Week 3-4)**
  6. Notes CRUD API
     - Create, read, update, delete endpoints
     - Privacy enforcement (user can only see own notes)
     - Status filtering and mood tracking
  7. Conflict resolution workflow API
     - Conflict creation
     - Resolution submission
     - State machine implementation
     - Privacy: hide partner's resolution until both submitted
  8. AI provider abstraction layer
     - AIProvider interface
     - ClaudeProvider implementation
     - OpenAIProvider implementation
     - Factory and configuration
  9. AI summarization integration
     - Conflict summary generation
     - Discrepancy detection
     - Approval workflow
     - Refinement loop
  10. Decision backlog API
      - Create decisions from approved conflicts
      - List, filter, search decisions
      - Review and archive operations

  **Phase 3: Retrospectives (Week 5)**
  11. Retrospective CRUD API
      - Manual retro triggering
      - Note inclusion in retro
      - AI discussion point generation
      - Retro completion with summary
  12. Scheduled retro logic
      - Background job scheduler (Quartz or similar)
      - Weekly/bi-weekly retro creation
      - Notification triggering
  13. Retro workflow
      - Both partners must be active
      - Note visibility during retro
      - Mark notes as discussed/resolved

  **Phase 4: Frontend Foundation (Week 6-7)**
  14. Next.js PWA setup
      - Create Next.js project with TypeScript
      - Configure PWA (next-pwa plugin)
      - Set up service worker
      - App manifest for installability
  15. Authentication UI
      - Register page
      - Login page
      - JWT token management (localStorage + refresh)
      - Auto-refresh token logic
  16. Protected routes and layouts
      - Auth context provider
      - Protected route wrapper
      - Navigation/header component
      - Mobile-first responsive layout

  **Phase 5: Frontend Features (Week 8-9)**
  17. Notes interface
      - Create note form (with mood selector)
      - My notes list (filtered by status)
      - Edit/delete note
      - Mark ready for discussion
  18. Conflict resolution UI
      - Initiate conflict button
      - Submit resolution form
      - View conflict status
      - Approve/request refinement for summary
      - Real-time updates when partner submits
  19. Decision backlog view
      - List all decisions
      - Search and filter by category/status
      - Mark as reviewed
      - Archive decisions
  20. Retrospective interface
      - View scheduled retros
      - Trigger manual retro
      - View notes in retro
      - See AI discussion points
      - Complete retro with summary
      - Retro history view

  **Phase 6: Polish & Deploy (Week 10)**
  21. Push notifications
      - Web Push API setup
      - Notification subscription endpoint
      - Send notifications for:
        - Scheduled retro reminders
        - Partner submitted resolution
        - Conflict summary ready
  22. Mobile UI optimization
      - Touch-friendly controls
      - Swipe gestures (optional)
      - Optimize for small screens
      - Test on actual mobile devices
  23. Docker deployment
      - Dockerfile for Ktor backend
      - Dockerfile for Next.js frontend
      - Docker Compose for local development
      - Environment variable configuration
  24. Testing & bug fixes
      - Manual testing of all workflows
      - Edge case handling
      - Error message improvements
      - Performance testing

  **Future Enhancements (Post-MVP):**
  - Export retrospective summaries to PDF
  - Decision search with full-text search
  - Analytics/insights (conflict frequency, common themes)
  - Mood trends over time
  - Voice notes instead of text
  - Integration with calendar for scheduled retros

  ---

  **Notes:**
  - I will code everything myself for practice
  - Estimated timeline: ~10 weeks for MVP
  - Can be adjusted based on available time
  - We are using v1 exposed

---

## CURRENT PROGRESS (Updated 2025-11-22)

### ✅ Completed

**Backend Foundation (Phase 1-2 - COMPLETE):**
1. ✅ Ktor project scaffolded with Gradle Kotlin DSL
2. ✅ Database configuration with Exposed v1 ORM
3. ✅ Complete database schema implemented (all 7 tables + junction tables)
4. ✅ Custom JWT authentication system
5. ✅ Authorization middleware with user context
6. ✅ All entity definitions (Users, Notes, Conflicts, Resolutions, AISummaries, Decisions, Retrospectives)
7. ✅ Repository pattern implemented (7 repositories with interfaces)
8. ✅ Service layer with business logic (5 services)
9. ✅ Controller layer with REST endpoints (5 controllers)
10. ✅ Global error handling with StatusPages
11. ✅ Dependency injection with Koin
12. ✅ AI provider abstraction layer (MockAIProvider for development)
13. ✅ Privacy enforcement (users can only access their own data)
14. ✅ Conflict resolution state machine
15. ✅ Retrospective system with explicit user tracking (RetrospectiveUsers junction table)
16. ✅ **Comprehensive unit tests for all services (55 tests total)**
   - AuthServiceTest (8 tests)
   - NoteServiceTest (14 tests)
   - DecisionServiceTest (10 tests)
   - RetrospectiveServiceTest (10 tests)
   - ConflictServiceTest (13 tests)

**Key Technical Decisions Made:**
- Using Exposed v1 DSL (not DAO) for explicit queries and privacy control
- Using `kotlinx.datetime.LocalDateTime` (not java.time) for Exposed v1 compatibility
- JWT authentication stored in memory (no database session table for MVP)
- MockAIProvider for development (easy to swap with real Claude/OpenAI later)
- Extension functions (getCurrentUser/getCurrentUserId) to eliminate JWT extraction repetition
- Explicit RetrospectiveUsers junction table instead of deriving from notes

**Files Generated:** 40+ backend files including:
- 7 entity files (database tables)
- 7 repository interfaces + implementations
- 5 service files with business logic
- 5 controller files with REST endpoints
- 5 comprehensive unit test files
- DTO classes for request/response
- Exception classes
- Configuration files (Database, Koin, StatusPages, Auth)
- Database helper (dbQuery wrapper for v1 transactions)

### 🚧 Known Issues to Fix

1. **Koin configuration compilation errors** - Need to fix AuthenticationProperties instantiation
2. **Missing SortOrder imports** - Some repository files still need import fixes
3. **Test database configuration** - Tests currently mock repositories, need integration tests with H2
4. **Application.yaml configuration** - Need to properly configure JWT secrets, database URL, etc.

### 📋 Next Steps (Priority Order)

**Immediate (Next Session):**
1. Fix remaining Koin configuration errors
2. Set up proper application.yaml configuration (JWT secret, database connection)
3. Test full application startup (ensure Ktor server starts)
4. Add basic integration tests (end-to-end API tests)
5. Implement real ClaudeProvider or OpenAIProvider (replace MockAIProvider)

**Short-term (Phase 3):**
6. Add repository unit tests
7. Add controller integration tests
8. Set up database migrations (Exposed migration module already added)
9. Add API documentation (OpenAPI/Swagger)
10. Test all REST endpoints manually with Postman/Insomnia

**Medium-term (Phase 4-5):**
11. Set up Next.js PWA frontend project
12. Implement authentication UI (login/register)
13. Build notes interface
14. Build conflict resolution UI
15. Build decision backlog UI
16. Build retrospective interface

**Polish (Phase 6):**
17. Add push notifications
18. Docker deployment setup
19. Mobile UI optimization
20. End-to-end testing

### 🎯 Current Focus

**Backend is ~80% complete!** All core features implemented with comprehensive testing.
Main remaining work:
- Configuration fixes (Koin, application.yaml)
- Real AI provider implementation
- Integration tests
- Then move to frontend development

### 📝 Development Notes

**Exposed v1 API Specifics:**
- Use `kotlinx.datetime.LocalDateTime` not `java.time.LocalDateTime`
- No `.slice()` method - just use `selectAll()` and map
- `insertReturning` returns ResultRow, access via `resultRow[Table.column]`
- Count: `query.count()` not `slice(column.count())`
- Transactions: Use `newSuspendedTransaction(Dispatchers.IO)` for suspend functions `dbQuery` util is provided

**Testing Patterns:**
- MockK for mocking repositories/dependencies
- `coEvery`/`coVerify` for suspend functions
- `runBlocking` in test functions
- Proper setup/teardown with `@BeforeTest` and `@AfterTest`

**Code Quality Practices Established:**
- No repetitive code (extension functions for common operations)
- Privacy-first architecture (explicit access checks)
- Clear separation of concerns (Repository → Service → Controller)
- Comprehensive error handling with custom exceptions
- Explicit relationships (junction tables) over implicit ones
- user response.body<T>() for json exptracting from response in tests
- Never commit anything