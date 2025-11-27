Concept: Couple's Conflict Resolution Manager with AI-Assisted Retrospectives

  Core Features (v1):
  - Private notes system (things that bother you, frustrations, concerns)
  - **Private journal system** (daily reflections, post-conflict processing, individual thoughts)
  - Feelings-first conflict resolution (process emotions before writing resolutions)
  - AI psychotherapist guidance for processing feelings
  - Conflict resolution tracking (both partners independently write resolutions)
  - AI-powered summarization of resolutions ("we decided that...")
  - Decision backlog (track all resolutions as reminders)
  - Retrospective system (scheduled reviews of notes + decisions)
  - Async AI processing with real-time SSE updates
  - Partnership context maintained across conflicts, retrospectives, and journals

  Key Goals:
  - Safe space for both partners to express concerns privately
  - Therapeutic AI guidance for processing emotions before resolution
  - Structured conflict resolution process with feelings-first approach
  - AI creates neutral summaries to reduce misunderstandings
  - Track decisions to prevent "we already talked about this" situations
  - Regular retrospectives to address small issues before they escalate
  - Mobile-first (capture thoughts immediately)
  - Easy enough for non-tech partner to actually use
  - Personalized AI responses using user profiles and relationship history

  Tech Stack (FINALIZED):
  - Backend: Kotlin + Ktor + Exposed ORM v1
  - Database: Supabase Postgres (via JDBC)
  - Frontend: Next.js PWA with TypeScript (planned)
  - Auth: Custom JWT authentication in Ktor
  - AI: Abstraction layer with Claude API (Anthropic SDK) + Mock provider
  - Async Processing: Kotlin Channels with background job processor
  - Real-time Updates: Server-Sent Events (SSE)
  - Deployment: Docker

  Priority Add-ons:
  - Push notifications for retro reminders
  - Decision search/filter (find past agreements)
  - Mood tracking alongside notes
  - Export retrospective summaries
  - Scheduled retrospectives (weekly/bi-weekly)
  - Multilingual support (AI responds in user's language)

  ## Architecture (Decided)

  Frontend (PWA) → Ktor API → Background Jobs (Channels) → Claude AI
                               ↓
                        Supabase Postgres
                               ↓
                        SSE for real-time updates

  Ktor Backend Responsibilities:
  - User authentication & authorization
  - User profile management (age, gender, description for AI context)
  - Partnership management with context tracking
  - Note CRUD operations (with privacy controls)
  - Feelings-first conflict workflow management
  - Async AI job processing (feelings, summaries, retrospectives)
  - AI summarization integration (Claude API as personal psychotherapist)
  - Partnership context updates (conflict resolutions, retrospectives)
  - Decision backlog management
  - Retrospective generation & scheduling
  - Server-Sent Events for real-time job status
  - Push notification service (optional)
  - All business logic & privacy enforcement

  Database Schema (Complete):

  **users**
  - id: UUID (PK)
  - email: VARCHAR (unique, not null)
  - password_hash: VARCHAR (not null)
  - name: VARCHAR (not null)
  - age: INTEGER (nullable) -- For AI personalization
  - gender: VARCHAR (nullable) -- For AI personalization
  - description: TEXT (nullable) -- Self-description for AI context
  - preferred_language: VARCHAR (nullable) -- e.g., "en", "es", "fr"
  - notification_token: VARCHAR (nullable)
  - created_at: TIMESTAMP (not null, default now())

  **partnerships**
  - id: UUID (PK)
  - user1_id: UUID (FK -> users.id, not null)
  - user2_id: UUID (FK -> users.id, not null)
  - status: ENUM (pending, accepted, ended) (not null, default 'pending')
  - initiated_by: UUID (FK -> users.id, not null)
  - created_at: TIMESTAMP (not null, default now())
  - accepted_at: TIMESTAMP (nullable)
  - ended_at: TIMESTAMP (nullable)

  **partnership_contexts**
  - id: UUID (PK)
  - partnership_id: UUID (FK -> partnerships.id, unique, not null)
  - compacted_summary: TEXT (not null) -- AI-maintained relationship history
  - conflict_count: INTEGER (not null, default 0)
  - retro_count: INTEGER (not null, default 0)
  - last_updated: TIMESTAMP (not null, default now())

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
  - status: ENUM (pending_feelings, pending_resolutions, summary_generated, refinement, approved, archived) (not null)
  - created_at: TIMESTAMP (not null, default now())

  **conflict_feelings** (NEW - Feelings-first approach)
  - id: UUID (PK)
  - conflict_id: UUID (FK -> conflicts.id, not null)
  - user_id: UUID (FK -> users.id, not null)
  - feelings_text: TEXT (not null)
  - detected_language: VARCHAR (nullable) -- Auto-detected from input
  - status: ENUM (processing, completed, failed) (not null, default 'processing')
  - ai_guidance: TEXT (nullable) -- Therapeutic guidance from AI
  - suggested_resolution: TEXT (nullable) -- AI-suggested resolution approach
  - emotional_tone: VARCHAR (nullable) -- e.g., "angry", "hurt", "frustrated"
  - submitted_at: TIMESTAMP (not null, default now())

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
  - patterns: TEXT (nullable) -- Patterns noticed from historical context
  - advice: TEXT (nullable) -- Actionable relationship advice
  - recurring_issues: JSON (nullable) -- Array of recurring themes
  - theme_tags: JSON (nullable) -- AI-suggested categories
  - provider: VARCHAR (not null) -- 'claude' or 'mock'
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
  - status: ENUM (scheduled, in_progress, processing_discussion_points, pending_approval, completed, cancelled) (not null)
  - ai_discussion_points: TEXT (nullable) -- JSON array of discussion points
  - final_summary: TEXT (nullable)
  - approved_by_user_id_1: UUID (nullable)
  - approved_by_user_id_2: UUID (nullable)
  - approval_text_1: TEXT (nullable) -- User 1's explanation for approval
  - approval_text_2: TEXT (nullable) -- User 2's explanation for approval
  - created_at: TIMESTAMP (not null, default now())

  **retrospective_users** (junction table)
  - retrospective_id: UUID (FK -> retrospectives.id, not null)
  - user_id: UUID (FK -> users.id, not null)
  - PRIMARY KEY (retrospective_id, user_id)

  **retrospective_notes** (junction table)
  - retrospective_id: UUID (FK -> retrospectives.id, not null)
  - note_id: UUID (FK -> notes.id, not null)
  - PRIMARY KEY (retrospective_id, note_id)

  **jobs** (Async processing)
  - id: UUID (PK)
  - job_type: ENUM (process_feelings, generate_summary, generate_discussion_points, update_partnership_context)
  - entity_id: UUID (not null) -- conflict_id, retro_id, etc.
  - payload: TEXT (nullable) -- JSON payload for job context
  - status: ENUM (pending, processing, completed, failed, retrying) (not null, default 'pending')
  - retry_count: INTEGER (not null, default 0)
  - max_retries: INTEGER (not null, default 3)
  - error_message: TEXT (nullable)
  - created_at: TIMESTAMP (not null, default now())
  - started_at: TIMESTAMP (nullable)
  - completed_at: TIMESTAMP (nullable)

  **journal_entries** (Private daily reflections)
  - id: UUID (PK)
  - user_id: UUID (FK -> users.id, not null)
  - partnership_id: UUID (FK -> partnerships.id, not null)
  - content: TEXT (not null) -- Freeform journal entry
  - status: ENUM (draft, completed, ai_processed, archived) (not null, default 'draft')
  - created_at: TIMESTAMP (not null, default now())
  - completed_at: TIMESTAMP (nullable)

  ## Data Flow

  **Note Creation:**
  1. User writes private note about concern/frustration
  2. Note stored with user_id (only visible to author until retro)
  3. User can mark note as "ready for discussion"

  **Conflict Resolution (Feelings-First Approach):**
  1. After verbal conflict, either partner initiates conflict
  2. **Each partner submits feelings independently (async AI processing)**
     - AI acts as personal psychotherapist
     - Provides empathetic guidance and validates emotions
     - Helps identify underlying needs
     - Suggests "I" statement approaches
     - Users can submit multiple feelings as they process emotions
  3. After processing feelings, partners write resolutions independently
  4. System waits for BOTH resolutions to be submitted
  5. AI analyzes both resolutions with full relationship context (async)
  6. AI generates comprehensive summary with patterns and advice
  7. Both partners review and approve/refine summary
  8. When both approve: Decision created + Partnership context updated (async)

  **Retrospective:**
  1. Scheduled (weekly/bi-weekly) or on-demand
  2. Pulls all pending notes from both partners
  3. Shows decision backlog for review
  4. AI generates discussion points from notes (async)
  5. Both partners review and approve discussion points
  6. Partners discuss together
  7. Mark notes as resolved/ongoing
  8. Complete retro with final summary (requires both approvals)
  9. Partnership context updated with insights (sync)

  **Journal Entries (Private Individual Reflections):**
  1. User creates journal entry (starts as DRAFT)
  2. User can edit while DRAFT
  3. User marks complete → status = COMPLETED
  4. Journal stays PRIVATE (partner cannot see)
  5. AI processes journals in BATCH when context update needed:
     - Before feelings processing
     - Before conflict summarization
     - Before retrospective discussion points
  6. AI extracts INSIGHTS without revealing private details
  7. Journals marked as AI_PROCESSED after context update

  **Partnership Context Management:**
  1. **Initial context** created when partnership accepted (user profiles)
  2. **Conflict resolution** updates context asynchronously after approval
  3. **Retrospective completion** updates context synchronously
  4. **Journal entries** update context in BATCH (before AI interactions)
  5. Context includes: user profiles, recurring themes, communication patterns, growth areas, individual emotional patterns

  ## Conflict Resolution State Machine

  **States:**
  1. PENDING_FEELINGS - Conflict initiated, partners processing feelings with AI
  2. PENDING_RESOLUTIONS - Feelings processed, waiting for both resolution submissions
  3. SUMMARY_GENERATED - AI created summary from both resolutions, awaiting approval
  4. REFINEMENT - One or both partners requested changes to summary
  5. APPROVED - Both partners approved summary, decision created and added to backlog
  6. ARCHIVED - Conflict closed without completion (manual action)

  **State Transitions:**
  - PENDING_FEELINGS → PENDING_RESOLUTIONS (when ready to write resolutions)
  - PENDING_RESOLUTIONS → SUMMARY_GENERATED (when both resolutions submitted, async)
  - SUMMARY_GENERATED → APPROVED (when both partners approve)
  - SUMMARY_GENERATED → REFINEMENT (when either partner requests changes)
  - REFINEMENT → SUMMARY_GENERATED (after AI re-generates summary, async)
  - Any state → ARCHIVED (manual close by either partner)

  **Business Rules:**
  - Users can submit multiple feelings per conflict for iterative processing
  - Cannot view partner's feelings or resolutions until both submitted
  - AI has access to all user's feelings in current conflict for context
  - Cannot generate AI summary until both resolutions exist
  - Both approvals required to create decision
  - Refinement can happen multiple times before approval

  ## Retrospective Workflow & State Machine

  **States:**
  1. SCHEDULED - Retrospective scheduled for future date
  2. IN_PROGRESS - Retrospective started, notes being added
  3. PROCESSING_DISCUSSION_POINTS - AI generating discussion points in background
  4. PENDING_APPROVAL - Discussion points generated, waiting for both partners to approve
  5. COMPLETED - Both partners approved and retrospective finalized with summary
  6. CANCELLED - Retrospective cancelled (manual action)

  **State Transitions:**
  - SCHEDULED → IN_PROGRESS (when started manually or automatically)
  - IN_PROGRESS → PROCESSING_DISCUSSION_POINTS (when generate-points called, async)
  - PROCESSING_DISCUSSION_POINTS → PENDING_APPROVAL (when AI completes generation)
  - PENDING_APPROVAL → COMPLETED (when both partners approve AND complete endpoint called)
  - Any state → CANCELLED (manual cancellation)

  **Business Rules:**
  - Both partners must add notes before generating discussion points
  - Discussion points are returned as structured JSON array (List<DiscussionPoint>)
  - Both partners must approve discussion points before completing retrospective
  - Each partner must provide approval text explaining their perspective/agreement
  - Approval texts help partners come to agreement and provide AI with context for future interactions
  - Completion requires both approvals AND calling complete endpoint with final summary
  - All notes in retrospective are marked as "discussed" upon completion
  - Partnership context is updated synchronously after completion with approval texts included

  ## API Endpoints (Complete)

  **Auth:**
  - POST /api/auth/register - create new user account
  - POST /api/auth/login - login and receive JWT tokens
  - GET /api/auth/me - get current user info

  **User Profile:**
  - GET /api/users/profile - get current user profile
  - PATCH /api/users/profile - update profile (name, age, gender, description, preferredLanguage)

  **Partnerships:**
  - POST /api/partnerships/invite - send partnership invitation
  - GET /api/partnerships/invitations - get sent/received invitations
  - POST /api/partnerships/:id/accept - accept invitation (creates initial context)
  - POST /api/partnerships/:id/reject - reject invitation
  - GET /api/partnerships/current - get active partnership
  - DELETE /api/partnerships/current - end current partnership

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
  - POST /api/conflicts/:id/feelings - submit feelings (async AI processing) **NEW**
  - GET /api/conflicts/:id/feelings - get all feelings for conflict **NEW**
  - POST /api/conflicts/:id/resolutions - submit my resolution (triggers summary if both submitted)
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
  - GET /api/retrospectives/:id - get retro details (includes approval texts)
  - GET /api/retrospectives/:id/notes - get notes included in this retro
  - POST /api/retrospectives/:id/notes - add note to retro
  - POST /api/retrospectives/:id/generate-points - generate discussion points (async)
  - PATCH /api/retrospectives/:id/approve - approve discussion points with explanation text (body: { approvalText: string })
  - POST /api/retrospectives/:id/complete - finalize retro with summary (requires both approvals)
  - PATCH /api/retrospectives/:id/cancel - cancel scheduled retro

  **Server-Sent Events (SSE):**
  - GET /api/events - subscribe to real-time job updates (SSE stream)
    - Events: JOB_STARTED, JOB_COMPLETED, JOB_FAILED, JOB_RETRYING

  ## Privacy & Security Considerations

  - Notes are PRIVATE until explicitly included in retro
  - Feelings are private until both partners submit them
  - Cannot read partner's feelings/resolutions outside of appropriate context
  - Both partners must submit resolutions before AI summary
  - Decision backlog visible to both (shared agreements)
  - Option to delete notes before they enter retro
  - Retro requires both partners to be "present" (active session)
  - Partnership context maintains relationship history securely

  ## AI Provider Abstraction Layer

  **Design:**
  Interface-based abstraction to support multiple AI providers with psychotherapist persona

  ```kotlin
  interface AIProvider {
      // Process feelings with therapeutic guidance
      suspend fun processFeelingsAndSuggestResolution(
          userFeelings: String,
          userProfile: UserProfile,
          partnerProfile: UserProfile,
          partnershipContext: String? = null,
          previousFeelings: List<String>? = null,
          detectedLanguage: String = "en"
      ): FeelingsProcessingResult

      // Generate conflict summary with relationship advice
      suspend fun summarizeConflict(
          resolution1: String,
          resolution2: String,
          user1Profile: UserProfile,
          user2Profile: UserProfile,
          partnershipContext: String? = null,
          detectedLanguage: String = "en"
      ): SummaryResult

      // Generate retrospective discussion points
      suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult

      // Update partnership context with conflict resolution
      suspend fun updatePartnershipContextWithConflict(
          existingContext: String?,
          conflictSummary: String,
          user1Profile: UserProfile,
          user2Profile: UserProfile
      ): String

      // Update partnership context with retrospective insights
      suspend fun updatePartnershipContextWithRetrospective(
          existingContext: String?,
          retroSummary: String,
          retroNotes: List<String>,
          approvalText1: String? = null,
          approvalText2: String? = null
      ): String

      // Detect language from user input
      fun detectLanguage(text: String): String
  }

  data class UserProfile(
      val name: String,
      val age: Int?,
      val gender: String?,
      val description: String?
  )

  data class FeelingsProcessingResult(
      val guidance: String,              // Therapeutic guidance
      val suggestedResolution: String,   // Communication strategy
      val emotionalTone: String,         // Emotion classification
      val provider: String
  )

  data class SummaryResult(
      val summary: String,               // "We decided that..." statement
      val patterns: String?,             // Patterns from history
      val advice: String?,               // Actionable advice
      val recurringIssues: List<String>, // Recurring themes
      val themeTags: List<String>,       // Categories
      val provider: String
  )

  data class RetroPointsResult(
      val discussionPoints: List<DiscussionPoint>,
      val provider: String
  )

  data class DiscussionPoint(
      val theme: String,
      val relatedNoteIds: List<String>,
      val suggestedApproach: String
  )
  ```

  **Implementations:**
  - MockAIProvider - Development provider with realistic responses
  - ClaudeAIProvider - Production provider using Anthropic Claude Sonnet 4.5 via SDK

  **Configuration:**
  - Provider selection via environment variable
  - API key from environment (AI_API_KEY)
  - Fallback to mock provider for development

  ## AI Therapeutic Approach

  **Feelings Processing (Personal Psychotherapist):**
  - AI acts as licensed couples therapist providing one-on-one support
  - Validates emotions without judgment
  - Helps identify underlying needs (safety, respect, connection, autonomy)
  - Normalizes relationship struggles
  - Guides toward "I" statements and constructive communication
  - References Gottman method and Emotionally Focused Therapy (EFT)
  - Uses client's name and partner's name for personalization
  - Responds in detected language for precision

  **Conflict Resolution Summary (Couples Therapist):**
  - Speaks to both partners as their therapist
  - Creates neutral "We decided..." statement
  - Identifies patterns from relationship history
  - Provides therapeutic homework and actionable steps
  - Detects recurring themes and tags them
  - Affirming and solutions-focused approach
  - Responds in detected language

  **Retrospective Discussion Points:**
  - Groups concerns by theme
  - Suggests therapeutic discussion approaches
  - Prioritizes what needs attention
  - Creates safe dialogue framework

  **Partnership Context Management:**
  - Maintains session notes as a therapist would
  - Tracks recurring conflict themes
  - Monitors communication patterns (improving or struggling)
  - Notes areas of growth
  - Keeps factual, chronological, therapeutically useful notes

  ## Language Detection & Multilingual Support

  **Automatic Language Detection:**
  - Heuristic pattern matching for: English, Spanish, French, German, Italian, Portuguese, Russian
  - Detects language from user input (Cyrillic, accented characters, etc.)
  - Falls back to English if uncertain

  **AI Response Localization:**
  - AI responds entirely in detected user language
  - Therapeutic guidance translated appropriately
  - Suggested resolutions in user's language
  - Maintains therapeutic tone across languages

  ## Async Job Processing Architecture

  **Job Types:**
  1. PROCESS_FEELINGS - Process user feelings and generate AI guidance
  2. GENERATE_SUMMARY - Generate conflict resolution summary from both resolutions
  3. GENERATE_DISCUSSION_POINTS - Generate retrospective discussion points
  4. UPDATE_PARTNERSHIP_CONTEXT - Update partnership context after conflict resolution

  **Flow:**
  1. API endpoint creates job and returns immediately (200 OK)
  2. Job queued in Kotlin Channel
  3. JobProcessorService processes jobs in background
     - **CRITICAL:** Before each AI call, JournalContextProcessor automatically processes unprocessed journals
     - This ensures partnership context is always up-to-date with latest journal insights
     - Journals are batch-processed and marked as AI_PROCESSED
  4. SSE publishes real-time status updates (STARTED, COMPLETED, FAILED, RETRYING)
  5. Clients subscribe to SSE stream for updates

  **Journal Integration in Background Jobs:**
  - `PROCESS_FEELINGS`: Processes journals before generating AI guidance
  - `GENERATE_SUMMARY`: Processes journals before creating conflict summary
  - `GENERATE_DISCUSSION_POINTS`: Processes journals before generating retro points
  - Journal processing is automatic and transparent to the user
  - Only COMPLETED journals are processed (batch optimization for tokens)

  **Retry Logic:**
  - Failed jobs automatically retry up to 3 times
  - Exponential backoff between retries
  - Error messages stored in job record

  ## Frontend Decision

  **DECIDED: Next.js**
  - Mature PWA ecosystem with proven patterns
  - Excellent TypeScript support
  - Large community and extensive documentation
  - Better for complex state management (conflict workflows)
  - Mobile-first design is CRITICAL (capture thoughts in the moment)
  - PWA features: Service worker, offline support, installable
  - EventSource API for SSE integration

---

## CURRENT PROGRESS (Updated 2025-11-25)

### ✅ Phase 1-3 COMPLETE: Backend Implementation (100%)

**Backend Foundation:**
1. ✅ Ktor project scaffolded with Gradle Kotlin DSL
2. ✅ Database configuration with Exposed v1 ORM
3. ✅ Complete database schema (10 tables + 2 junction tables)
4. ✅ Custom JWT authentication system
5. ✅ Authorization middleware with user context
6. ✅ All entity definitions with proper relationships
7. ✅ Repository pattern (10 repositories with interfaces)
8. ✅ Service layer with business logic (7 services)
9. ✅ Controller layer with REST endpoints (8 controllers)
10. ✅ Facade layer for business logic orchestration (6 facades)
11. ✅ Global error handling with StatusPages
12. ✅ Dependency injection with Koin
13. ✅ Privacy enforcement (users can only access their own data)
14. ✅ **Comprehensive unit tests for all services (55 tests total)**

**Feelings-First Conflict Resolution:**
15. ✅ ConflictFeelings entity with status tracking
16. ✅ Multiple feelings per user per conflict support
17. ✅ AI context-aware of all user's feelings in current conflict
18. ✅ Feelings submission API (async processing)
19. ✅ Feelings retrieval API

**Async AI Processing & Real-time Updates:**
20. ✅ Background job processing with Kotlin Channels
21. ✅ JobProcessorService with retry logic
22. ✅ Server-Sent Events (SSE) implementation
23. ✅ Real-time job status updates (STARTED, COMPLETED, FAILED, RETRYING)
24. ✅ Async processing for: feelings, summaries, discussion points, context updates

**User Profiles & Personalization:**
25. ✅ User profile fields (age, gender, description, preferredLanguage)
26. ✅ UserProfile data class for AI context
27. ✅ User profile API endpoints (GET/PATCH /api/users/profile)
28. ✅ Profile integration in all AI method signatures

**AI as Personal Psychotherapist:**
29. ✅ Complete AI provider rewrite with therapeutic persona
30. ✅ Gottman method and EFT references in prompts
31. ✅ Personalized responses using user names and profiles
32. ✅ Empathetic guidance with "I" statement coaching
33. ✅ All AI responses use user and partner names

**Language Detection & Multilingual Support:**
34. ✅ Heuristic language detection (7 languages)
35. ✅ detectedLanguage field in ConflictFeelings
36. ✅ AI responds in user's detected language
37. ✅ Language parameter in all AI method calls

**Partnership Context Management:**
38. ✅ Partnership context entity and repository
39. ✅ Initial context prepopulation on partnership acceptance
40. ✅ Async context updates after conflict approval (UPDATE_PARTNERSHIP_CONTEXT job)
41. ✅ Sync context updates after retrospective completion
42. ✅ Separate AI methods for conflict vs retrospective context updates
43. ✅ Context includes user profiles, themes, patterns, growth areas

**Journal Entries (Discussion Journal Feature - NEWLY ADDED):**
44. ✅ JournalEntries entity with state machine (DRAFT, COMPLETED, AI_PROCESSED, ARCHIVED)
45. ✅ Private journal entries (not visible to partner)
46. ✅ Full CRUD operations with ownership validation
47. ✅ JournalContextProcessor for batch processing unprocessed journals
48. ✅ AI provider method: updatePartnershipContextWithJournals()
49. ✅ Privacy-preserving AI context extraction (themes/patterns, not specifics)
50. ✅ **Automatic journal processing integrated into JobProcessorService:**
    - Before feelings processing (PROCESS_FEELINGS job)
    - Before conflict summary generation (GENERATE_SUMMARY job)
    - Before retro discussion points (GENERATE_DISCUSSION_POINTS job)
51. ✅ Journal API endpoints (7 endpoints: create, get, update, complete, archive, delete, list)
52. ✅ Dependency injection configured in Koin
53. ✅ Routes registered in Routing.kt

**AI Providers:**
54. ✅ AIProvider interface with complete method signatures
55. ✅ MockAIProvider - fully functional development provider
56. ✅ ClaudeAIProvider - production provider with Anthropic SDK (Claude Sonnet 4.5)
57. ✅ All providers support UserProfile context and language parameters

**Testing & Documentation:**
58. ✅ Comprehensive Postman collections:
   - Complete API with SSE documentation
   - Dual-user workflow collection with automated setup
   - Realistic conflict scenarios
   - SSE subscription examples
59. ✅ Unit tests for all services (55 tests passing)
60. ✅ Build successful with no compilation errors

### 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT (Future PWA)                       │
│  - EventSource for SSE                                       │
│  - Fetch API for REST                                        │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│                    KTOR API SERVER                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Controllers (REST endpoints)                         │   │
│  │  - AuthController, UserController, PartnershipController│
│  │  - ConflictController, NoteController, etc.          │   │
│  └──────────────┬──────────────────────────────────────┘   │
│                 │                                            │
│  ┌──────────────▼──────────────────────────────────────┐   │
│  │ Facades (Business Logic Orchestration)              │   │
│  │  - ConflictFacade, PartnershipFacade, etc.          │   │
│  └──────────────┬──────────────────────────────────────┘   │
│                 │                                            │
│  ┌──────────────▼──────────────────────────────────────┐   │
│  │ Services (Core Business Logic)                      │   │
│  │  - ConflictService, UserService, etc.               │   │
│  └──────────────┬──────────────────────────────────────┘   │
│                 │                                            │
│  ┌──────────────▼──────────────────────────────────────┐   │
│  │ Repositories (Data Access Layer)                    │   │
│  │  - ConflictRepository, UserRepository, etc.         │   │
│  └──────────────┬──────────────────────────────────────┘   │
└─────────────────┼────────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼─────┐  ┌───▼──────┐  ┌──▼───────────────────┐
│ Supabase│  │ Job      │  │ SSE Event Publisher  │
│ Postgres│  │ Processor│  │ (Real-time updates)  │
│         │  │ Service  │  └──────────────────────┘
└─────────┘  │ (Channel)│
             │    │     │
             └────┼─────┘
                  │
          ┌───────▼───────┐
          │   AI Provider │
          │  (Claude/Mock)│
          └───────────────┘
```

### 🎯 Current Status: Backend Complete, Ready for Frontend

**What Works:**
- ✅ Complete REST API with all endpoints
- ✅ Async AI processing with retry logic
- ✅ Real-time SSE updates
- ✅ User profiles with AI personalization
- ✅ Feelings-first conflict resolution workflow
- ✅ Partnership context management
- ✅ Multilingual AI responses
- ✅ Therapeutic AI persona with relationship expertise
- ✅ Privacy enforcement across all operations
- ✅ Comprehensive Postman testing collections

**Ready for Testing:**
- Import `postman/Morning-Brief-Dual-User-Complete.postman_collection.json`
- Run "0️⃣ Setup" folder to create users and partnership
- Follow "1️⃣ Conflict Resolution Flow" for complete workflow
- Monitor SSE for real-time job updates (use browser console or curl)

### 📋 Next Steps (Priority Order)

**Phase 4: Frontend Foundation (Week 6-7)**
1. Set up Next.js PWA project with TypeScript
2. Configure PWA manifest and service worker
3. Implement authentication UI (login/register)
4. Create protected routes and layouts
5. Set up EventSource for SSE integration
6. Build mobile-first responsive layouts

**Phase 5: Frontend Features (Week 8-9)**
7. User profile management UI
8. Partnership invitation/acceptance flow
9. Notes interface (create, list, mark ready)
10. Feelings submission interface with real-time AI guidance
11. Conflict resolution workflow UI
12. AI summary review and approval UI
13. Decision backlog view
14. Retrospective interface with discussion points

**Phase 6: Polish & Deploy (Week 10)**
15. Push notifications (Web Push API)
16. Mobile UI optimization and testing
17. Docker deployment setup
18. End-to-end testing
19. Performance optimization

**Future Enhancements (Post-MVP):**
- Export retrospective summaries to PDF
- Decision search with full-text search
- Analytics/insights (conflict frequency, common themes)
- Mood trends over time
- Voice notes instead of text
- Calendar integration for scheduled retros
- Additional language support

### 📝 Development Notes

**Exposed v1 API Specifics:**
- Use `kotlinx.datetime.LocalDateTime` not `java.time.LocalDateTime`
- No `.slice()` method - use `selectAll()` and map
- `insertReturning` returns ResultRow, access via `resultRow[Table.column]`
- Count: `query.count()` not `slice(column.count())`
- Transactions: Use `newSuspendedTransaction(Dispatchers.IO)` with `dbQuery` helper

**Async Processing Patterns:**
- Create job → Queue in Channel → Process in background → Publish SSE
- JobProcessorService automatically retries failed jobs
- SSE streams stay open for real-time updates
- Clients use EventSource API to subscribe

**AI Integration Patterns:**
- All AI methods require UserProfile objects for personalization
- Language detected from input and passed to AI
- Partnership context provided for relationship history awareness
- AI responds entirely in user's language
- Therapeutic persona maintained across all interactions

**Code Quality Practices:**
- No repetitive code (extension functions for common operations)
- Privacy-first architecture (explicit access checks)
- Clear separation of concerns (Repository → Service → Facade → Controller)
- Comprehensive error handling with custom exceptions
- Explicit relationships (junction tables) over implicit ones
- User profiles integrated throughout AI pipeline

**Testing with Postman:**
- Use dual-user collection for complete workflow testing
- SSE monitoring via browser console (Postman doesn't support SSE well)
- Auto-saves tokens and IDs for easy flow testing
- Realistic conflict scenarios included

### 🏗️ Project Structure

```
backend/src/main/kotlin/
├── entity/              # Database table definitions (11 files)
├── dto/                 # Data transfer objects
├── repository/          # Data access layer (9 repos)
├── service/             # Business logic (6 services)
├── facade/              # Orchestration layer (5 facades)
├── controller/          # REST endpoints (7 controllers)
├── ai/                  # AI provider abstraction (3 files)
├── configuration/       # Koin DI, Auth, StatusPages
├── utils/               # Extension functions, helpers
├── exception/           # Custom exception classes
└── Application.kt       # Main entry point

backend/src/test/kotlin/
└── service/             # Unit tests (55 tests, all passing)

postman/
├── Morning-Brief-API-Complete-With-SSE.postman_collection.json
└── Morning-Brief-Dual-User-Complete.postman_collection.json
```

### 📊 Key Metrics

- **Total Backend Files:** 65+ Kotlin files
- **Lines of Code:** ~9,000+ lines
- **Test Coverage:** 55 unit tests (all services covered)
- **API Endpoints:** 42+ REST endpoints (7 journal endpoints added)
- **Database Tables:** 12 tables (10 main + 2 junction)
- **AI Methods:** 6 comprehensive methods with full context (journal context update added)
- **Supported Languages:** 7 languages (auto-detected)
- **Job Types:** 4 async job types with automatic journal processing
- **Build Status:** ✅ Successful, no errors

### 🎉 Major Achievements

1. **Complete Backend API** - All CRUD operations, workflows, and business logic
2. **Feelings-First Approach** - Innovative conflict resolution with emotional processing
3. **AI as Therapist** - Personalized therapeutic guidance using Gottman method and EFT
4. **Async Processing** - Non-blocking AI operations with real-time SSE updates
5. **Partnership Context** - Relationship history maintained and utilized by AI
6. **Discussion Journal** - Private journaling with automatic AI context integration
7. **Multilingual Support** - Automatic language detection and localized responses
8. **Privacy-First Design** - Comprehensive access controls and data isolation
9. **Production-Ready** - Comprehensive error handling, retry logic, and testing

**The backend is feature-complete and ready for frontend development!** 🚀
- dont write .md files, except when user dileberately asks for it