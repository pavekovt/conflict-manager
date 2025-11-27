# Conflict Resolution & Retrospective Flows

This document describes the complete workflows for conflict resolution and retrospectives in the Morning Brief application.

## Table of Contents
- [Conflict Resolution Flow (Feelings-First)](#conflict-resolution-flow-feelings-first)
- [Retrospective Flow](#retrospective-flow)
- [State Machines](#state-machines)
- [Async Job Processing](#async-job-processing)

---

## Conflict Resolution Flow (Feelings-First)

The conflict resolution system uses a feelings-first approach where partners process their emotions with AI guidance before writing resolutions.

### High-Level Flow

```mermaid
flowchart TD
    Start([Conflict Initiated]) --> PF[PENDING_FEELINGS]

    PF --> U1F[User 1 Submits Feelings]
    PF --> U2F[User 2 Submits Feelings]

    U1F --> AI1[AI Processing<br/>Job queued]
    U2F --> AI2[AI Processing<br/>Job queued]

    AI1 --> G1[AI Provides<br/>Therapeutic Guidance]
    AI2 --> G2[AI Provides<br/>Therapeutic Guidance]

    G1 --> Ready{Both Users<br/>Ready?}
    G2 --> Ready

    Ready -->|User transitions| PR[PENDING_RESOLUTIONS]

    PR --> R1[User 1 Submits<br/>Resolution]
    PR --> R2[User 2 Submits<br/>Resolution]

    R1 --> BothRes{Both<br/>Submitted?}
    R2 --> BothRes

    BothRes -->|Yes| SumJob[Generate Summary Job<br/>PROCESSING_SUMMARY]

    SumJob --> SumAI[AI Analyzes Both<br/>Resolutions + Context]
    SumAI --> SG[SUMMARY_GENERATED]

    SG --> Review1[User 1 Reviews]
    SG --> Review2[User 2 Reviews]

    Review1 --> Decision{Decision}
    Review2 --> Decision

    Decision -->|Approve| BothApprove{Both<br/>Approved?}
    Decision -->|Request Changes| Refine[REFINEMENT]

    Refine --> RegenJob[Regenerate Summary<br/>Job queued]
    RegenJob --> SG

    BothApprove -->|Yes| Approved[APPROVED]
    BothApprove -->|No| SG

    Approved --> CreateDec[Create Decision<br/>in Backlog]
    CreateDec --> UpdateCtx[Update Partnership<br/>Context Job]
    UpdateCtx --> End([Complete])

    style PF fill:#e1f5ff
    style PR fill:#e1f5ff
    style SG fill:#fff4e1
    style Approved fill:#e1ffe1
    style AI1 fill:#ffe1f5
    style AI2 fill:#ffe1f5
    style SumJob fill:#ffe1f5
    style UpdateCtx fill:#ffe1f5
```

### Detailed State Transitions

```mermaid
stateDiagram-v2
    [*] --> PENDING_FEELINGS: Conflict Created

    PENDING_FEELINGS --> PROCESSING_FEELINGS: User submits feelings
    PROCESSING_FEELINGS --> PENDING_FEELINGS: AI guidance complete

    PENDING_FEELINGS --> PENDING_RESOLUTIONS: User transitions<br/>(feelings processed)

    PENDING_RESOLUTIONS --> PROCESSING_SUMMARY: Both resolutions submitted
    PROCESSING_SUMMARY --> SUMMARY_GENERATED: AI summary complete

    SUMMARY_GENERATED --> REFINEMENT: Partner requests changes
    REFINEMENT --> PROCESSING_SUMMARY: Re-generate requested

    SUMMARY_GENERATED --> APPROVED: Both partners approve
    APPROVED --> [*]: Decision created

    PENDING_FEELINGS --> ARCHIVED: Manual archive
    PENDING_RESOLUTIONS --> ARCHIVED: Manual archive
    SUMMARY_GENERATED --> ARCHIVED: Manual archive
    REFINEMENT --> ARCHIVED: Manual archive

    note right of PROCESSING_FEELINGS
        Async AI job:
        - Validate emotions
        - Provide guidance
        - Suggest "I" statements
    end note

    note right of PROCESSING_SUMMARY
        Async AI job:
        - Analyze resolutions
        - Use partnership context
        - Generate "We decided..."
    end note

    note right of APPROVED
        Sync operations:
        - Create decision
        - Queue context update job
    end note
```

### Feelings Processing (AI as Personal Therapist)

```mermaid
sequenceDiagram
    participant User
    participant API
    participant JobQueue
    participant Worker
    participant AI
    participant DB

    User->>API: POST /conflicts/{id}/feelings<br/>{feelingsText}
    API->>DB: Store feelings<br/>status: PROCESSING
    API->>DB: Create PROCESS_FEELINGS job
    API->>JobQueue: Queue job
    API-->>User: 200 OK<br/>{feeling object}

    Note over User: User can continue<br/>using app

    Worker->>JobQueue: Pick up job
    Worker->>DB: Get feeling + conflict
    Worker->>DB: Get previous feelings<br/>(for context)
    Worker->>DB: Get user profiles
    Worker->>DB: Get partnership context
    Worker->>AI: Process feelings with context<br/>- User profile<br/>- Partner profile<br/>- Previous feelings<br/>- Partnership history

    AI-->>Worker: Therapeutic response<br/>- Guidance<br/>- Suggested resolution<br/>- Emotional tone

    Worker->>DB: Update feeling<br/>status: COMPLETED<br/>+ AI response
    Worker->>JobQueue: Emit JobEvent.Completed

    Note over User: User receives SSE<br/>notification via EventFlow
```

### Summary Generation (AI as Couples Therapist)

```mermaid
sequenceDiagram
    participant U1 as User 1
    participant U2 as User 2
    participant API
    participant JobQueue
    participant Worker
    participant AI
    participant DB
    participant Context

    U1->>API: POST /conflicts/{id}/resolutions<br/>{resolutionText}
    API->>DB: Store resolution 1
    API-->>U1: 200 OK<br/>conflict (1/2 submitted)

    U2->>API: POST /conflicts/{id}/resolutions<br/>{resolutionText}
    API->>DB: Store resolution 2
    API->>DB: Check: both submitted?

    alt Both resolutions submitted
        API->>DB: Create GENERATE_SUMMARY job
        API->>DB: Update status:<br/>PROCESSING_SUMMARY
        API->>JobQueue: Queue summary job
        API-->>U2: 200 OK<br/>conflict (2/2 submitted)

        Worker->>JobQueue: Pick up job
        Worker->>DB: Get both resolutions
        Worker->>DB: Get user profiles
        Worker->>Context: Get partnership context
        Worker->>AI: Generate summary<br/>- Resolution 1 & 2<br/>- User profiles<br/>- Partnership history<br/>- Detect language

        AI-->>Worker: Comprehensive summary<br/>- "We decided..."<br/>- Patterns identified<br/>- Advice<br/>- Recurring issues<br/>- Theme tags

        Worker->>DB: Store AI summary
        Worker->>DB: Update status:<br/>SUMMARY_GENERATED
        Worker->>JobQueue: Emit JobEvent.Completed

        Note over U1,U2: Both users receive<br/>SSE notification
    end
```

### Approval & Decision Creation

```mermaid
sequenceDiagram
    participant U1 as User 1
    participant U2 as User 2
    participant API
    participant DB
    participant DecisionSvc
    participant JobQueue
    participant Worker
    participant Context

    U1->>API: POST /conflicts/{id}/approve
    API->>DB: Mark user 1 approved
    API-->>U1: 200 OK {success: true}

    U2->>API: POST /conflicts/{id}/approve
    API->>DB: Mark user 2 approved
    API->>DB: Check: both approved?

    alt Both approved
        API->>DB: Update status: APPROVED
        API->>DecisionSvc: Create decision
        DecisionSvc->>DB: Insert decision record<br/>- Conflict summary<br/>- Status: active<br/>- Created timestamp

        API->>DB: Create UPDATE_PARTNERSHIP_CONTEXT job
        API->>JobQueue: Queue context update
        API-->>U2: 200 OK {success: true}

        Note over U1,U2: Decision now in backlog<br/>for both partners

        Worker->>JobQueue: Pick up context job
        Worker->>DB: Get conflict summary
        Worker->>DB: Get resolutions
        Worker->>Context: Get existing context
        Worker->>AI: Update context<br/>- Conflict resolution<br/>- User profiles<br/>- Existing history

        AI-->>Worker: Updated context<br/>- New patterns<br/>- Growth areas<br/>- Communication trends

        Worker->>Context: Save updated context
        Worker->>Context: Increment conflict count
        Worker->>JobQueue: Emit JobEvent.Completed
    end
```

---

## Retrospective Flow

Retrospectives allow partners to review accumulated notes and create structured discussion points.

### High-Level Flow

```mermaid
flowchart TD
    Start([Create Retrospective]) --> IP[IN_PROGRESS]

    IP --> AddN1[User 1 Adds Notes]
    IP --> AddN2[User 2 Adds Notes]

    AddN1 --> Ready{Ready for<br/>Discussion?}
    AddN2 --> Ready

    Ready -->|Generate| GenJob[Generate Discussion<br/>Points Job<br/>PROCESSING_DISCUSSION_POINTS]

    GenJob --> AIGen[AI Analyzes Notes<br/>Groups by Theme]
    AIGen --> PA[PENDING_APPROVAL]

    PA --> A1[User 1 Approves<br/>with text]
    PA --> A2[User 2 Approves<br/>with text]

    A1 --> BothApp{Both<br/>Approved?}
    A2 --> BothApp

    BothApp -->|Yes| CanComplete[Can Complete]
    BothApp -->|No| PA

    CanComplete --> Complete[User calls<br/>/complete with<br/>final summary]
    Complete --> Comp[COMPLETED]

    Comp --> MarkNotes[Mark all notes<br/>as DISCUSSED]
    MarkNotes --> UpdateCtx[Update Partnership<br/>Context SYNC]
    UpdateCtx --> End([Done])

    Ready -->|Cancel| Cancel[CANCELLED]
    Cancel --> End

    style IP fill:#e1f5ff
    style PA fill:#fff4e1
    style Comp fill:#e1ffe1
    style GenJob fill:#ffe1f5
    style UpdateCtx fill:#e1ffe1
```

### Detailed State Transitions

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED: Retrospective scheduled
    [*] --> IN_PROGRESS: Manual trigger

    SCHEDULED --> IN_PROGRESS: Start time reached<br/>or manual start

    IN_PROGRESS --> PROCESSING_DISCUSSION_POINTS: Generate points called
    PROCESSING_DISCUSSION_POINTS --> PENDING_APPROVAL: AI generation complete

    PENDING_APPROVAL --> PENDING_APPROVAL: Partner approves<br/>(waiting for other)
    PENDING_APPROVAL --> COMPLETED: Both approved +<br/>complete called

    SCHEDULED --> CANCELLED: Manual cancel
    IN_PROGRESS --> CANCELLED: Manual cancel

    COMPLETED --> [*]: Context updated
    CANCELLED --> [*]

    note right of PROCESSING_DISCUSSION_POINTS
        Async AI job:
        - Group notes by theme
        - Suggest approaches
        - Prioritize items
    end note

    note right of PENDING_APPROVAL
        Requires:
        - Both partners approve
        - Both provide approval texts
        - Then complete endpoint called
    end note

    note right of COMPLETED
        Sync operations:
        - Mark notes discussed
        - Update partnership context
        - Store approval texts
    end note
```

### Discussion Point Generation

```mermaid
sequenceDiagram
    participant User
    participant API
    participant JobQueue
    participant Worker
    participant AI
    participant DB
    participant Context

    User->>API: POST /retrospectives
    API->>DB: Create retrospective<br/>status: IN_PROGRESS
    API-->>User: 200 OK {retro}

    User->>API: POST /retrospectives/{id}/notes<br/>{noteId}
    API->>DB: Link note to retro
    API-->>User: 200 OK

    Note over User: Partner also adds notes...

    User->>API: POST /retrospectives/{id}/generate-points
    API->>DB: Update status:<br/>PROCESSING_DISCUSSION_POINTS
    API->>DB: Create GENERATE_DISCUSSION_POINTS job
    API->>JobQueue: Queue job
    API-->>User: 200 OK

    Worker->>JobQueue: Pick up job
    Worker->>DB: Get retrospective
    Worker->>DB: Get all linked notes
    Worker->>Context: Process unprocessed journals<br/>(update context first)
    Worker->>AI: Generate discussion points<br/>- All notes<br/>- Partnership context

    AI-->>Worker: Discussion points<br/>- Grouped by theme<br/>- Suggested approaches<br/>- Related note IDs

    Worker->>DB: Store discussion points<br/>(as formatted text)
    Worker->>DB: Update status:<br/>IN_PROGRESS
    Worker->>JobQueue: Emit JobEvent.Completed

    Note over User: User receives SSE<br/>can now review points
```

### Approval & Completion

```mermaid
sequenceDiagram
    participant U1 as User 1
    participant U2 as User 2
    participant API
    participant DB
    participant RetroSvc
    participant Context

    U1->>API: PATCH /retrospectives/{id}/approve<br/>{approvalText: "Good points"}
    API->>DB: Store User 1 approval<br/>+ approval text
    API-->>U1: 200 OK {success: true}

    U2->>API: PATCH /retrospectives/{id}/approve<br/>{approvalText: "I agree"}
    API->>DB: Store User 2 approval<br/>+ approval text
    API-->>U2: 200 OK {success: true}

    Note over U1,U2: Both approved,<br/>ready to complete

    U1->>API: POST /retrospectives/{id}/complete<br/>{finalSummary: "Great talk"}
    API->>DB: Check both approved

    alt Both approved
        API->>DB: Update status: COMPLETED
        API->>DB: Store final summary
        API->>DB: Set completedAt timestamp

        API->>RetroSvc: Mark notes as discussed
        RetroSvc->>DB: Update all linked notes<br/>status: DISCUSSED

        API->>Context: Update partnership context<br/>(SYNCHRONOUS)
        Context->>DB: Get retro summary
        Context->>DB: Get all notes
        Context->>DB: Get approval texts
        Context->>AI: Update context<br/>- Retro summary<br/>- Note themes<br/>- Approval texts<br/>- Existing context

        AI-->>Context: Updated context<br/>- New insights<br/>- Progress noted<br/>- Areas of growth

        Context->>DB: Save updated context
        Context->>DB: Increment retro count

        API-->>U1: 200 OK {success: true}

        Note over U1,U2: Retro complete!<br/>Context updated
    else Not both approved
        API-->>U1: 400 Bad Request<br/>"Both must approve"
    end
```

---

## State Machines

### Conflict Status State Machine

| Current Status | Valid Transitions | Trigger |
|---------------|-------------------|---------|
| `PENDING_FEELINGS` | `PENDING_RESOLUTIONS`, `ARCHIVED` | User action, manual archive |
| `PENDING_RESOLUTIONS` | `PROCESSING_SUMMARY`, `ARCHIVED` | Both resolutions submitted, manual archive |
| `PROCESSING_SUMMARY` | `SUMMARY_GENERATED` | AI job completes |
| `SUMMARY_GENERATED` | `REFINEMENT`, `APPROVED`, `ARCHIVED` | Request refinement, both approve, manual archive |
| `REFINEMENT` | `PROCESSING_SUMMARY`, `ARCHIVED` | Regenerate requested, manual archive |
| `APPROVED` | *(terminal)* | Decision created |
| `ARCHIVED` | *(terminal)* | N/A |

### Retrospective Status State Machine

| Current Status | Valid Transitions | Trigger |
|---------------|-------------------|---------|
| `SCHEDULED` | `IN_PROGRESS`, `CANCELLED` | Time reached/manual start, cancel |
| `IN_PROGRESS` | `PROCESSING_DISCUSSION_POINTS`, `CANCELLED` | Generate points, cancel |
| `PROCESSING_DISCUSSION_POINTS` | `IN_PROGRESS` | AI job completes |
| `IN_PROGRESS` (after points) | `COMPLETED`, `CANCELLED` | Both approved + complete called, cancel |
| `COMPLETED` | *(terminal)* | N/A |
| `CANCELLED` | *(terminal)* | N/A |

**Note:** The retrospective flow has a unique characteristic where it returns to `IN_PROGRESS` after discussion points are generated, allowing partners to review before approval.

---

## Async Job Processing

The application uses background job processing for AI operations to avoid blocking HTTP requests.

### Job Processing Architecture

```mermaid
flowchart LR
    subgraph "API Layer"
        API[HTTP Request]
    end

    subgraph "Job System"
        Queue[Job Queue<br/>Kotlin Channel]
        DB[(Job Database)]

        W1[Worker 1]
        W2[Worker 2]
        W3[Worker 3]
    end

    subgraph "Processing"
        AI[AI Provider<br/>Claude/Mock]
        CTX[Partnership<br/>Context]
        Journal[Journal<br/>Processor]
    end

    subgraph "Real-time Updates"
        SSE[SSE Event Flow]
        Client[Client<br/>EventSource]
    end

    API -->|1. Create job| DB
    API -->|2. Queue job ID| Queue
    API -->|3. Return 200 OK| Return[Client]

    Queue -->|4. Pick job| W1
    Queue --> W2
    Queue --> W3

    W1 -->|5. Process journals first| Journal
    Journal -->|6. Update context| CTX
    W1 -->|7. Call AI| AI
    AI -->|8. Return result| W1
    W1 -->|9. Save result| DB
    W1 -->|10. Emit event| SSE

    SSE -->|11. Stream update| Client

    style API fill:#e1f5ff
    style Queue fill:#fff4e1
    style AI fill:#ffe1f5
    style SSE fill:#e1ffe1
```

### Job Types & Processing

```mermaid
graph TD
    subgraph "Job Types"
        J1[PROCESS_FEELINGS]
        J2[GENERATE_SUMMARY]
        J3[GENERATE_DISCUSSION_POINTS]
        J4[UPDATE_PARTNERSHIP_CONTEXT]
    end

    subgraph "Pre-Processing"
        JP[Journal Processor<br/>Batch process unprocessed journals]
    end

    subgraph "AI Providers"
        Mock[MockAIProvider<br/>Development]
        Claude[ClaudeAIProvider<br/>Production]
    end

    J1 --> JP
    J2 --> JP
    J3 --> JP
    J4 -.->|No journal processing| Skip[Skip]

    JP --> Mock
    JP --> Claude
    Skip --> Mock
    Skip --> Claude

    style J1 fill:#e1f5ff
    style J2 fill:#e1f5ff
    style J3 fill:#e1f5ff
    style J4 fill:#fff4e1
    style JP fill:#ffe1f5
```

### Job Lifecycle

```mermaid
sequenceDiagram
    participant API
    participant JobRepo
    participant Queue
    participant Worker
    participant JobRepo2 as JobRepo
    participant SSE

    API->>JobRepo: Create job<br/>status: PENDING
    JobRepo-->>API: Job created
    API->>Queue: Queue job ID
    API-->>Client: 200 OK

    Note over Queue,Worker: Worker picks up job

    Worker->>Queue: Receive job ID
    Worker->>JobRepo2: Get job details
    JobRepo2-->>Worker: Job data

    Worker->>JobRepo2: Mark as PROCESSING
    Worker->>SSE: Emit JobEvent.Started

    alt Success
        Worker->>Worker: Process job
        Worker->>JobRepo2: Mark as COMPLETED
        Worker->>SSE: Emit JobEvent.Completed
    else Failure
        Worker->>JobRepo2: Increment retry count

        alt Retry < 3
            Worker->>JobRepo2: Reset to PENDING
            Worker->>Queue: Re-queue job
            Worker->>SSE: Emit JobEvent.Retrying
        else Max retries
            Worker->>JobRepo2: Mark as FAILED<br/>Store error message
            Worker->>SSE: Emit JobEvent.Failed
        end
    end
```

### SSE Event Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant SSE as EventFlow<br/>(SharedFlow)
    participant Worker

    Client->>API: GET /api/events/stream?entityId=xxx
    API->>SSE: Subscribe to eventFlow

    Note over Client,SSE: Connection established<br/>Heartbeat every 8s

    Worker->>SSE: Emit JobEvent.Started
    SSE-->>Client: event: job_started<br/>data: {jobId, type, entityId}

    Worker->>Worker: Process...

    Worker->>SSE: Emit JobEvent.Completed
    SSE-->>Client: event: job_completed<br/>data: {jobId, type, entityId}

    Note over Client: Client updates UI<br/>Fetches latest data
```

---

## Key Design Decisions

### 1. **Feelings-First Approach**
- Partners process emotions before writing solutions
- AI acts as personal therapist for each individual
- Reduces reactive, emotional responses
- Promotes healthy communication patterns

### 2. **Async AI Processing**
- HTTP requests return immediately (no 30s+ waits)
- Background workers process in parallel
- SSE provides real-time updates
- Retry logic handles transient failures

### 3. **Partnership Context**
- Maintains relationship history across all interactions
- AI uses context for personalized responses
- Updated after conflicts (async) and retros (sync)
- Includes patterns, growth areas, communication trends

### 4. **Journal Integration**
- Private journals processed in batch before AI calls
- Extracts insights without revealing specifics
- Enriches partnership context automatically
- Preserves privacy while improving AI responses

### 5. **Two-Phase Approval**
- Retrospectives require both partners to approve discussion points
- Then requires explicit complete call with final summary
- Approval texts provide context for future AI interactions
- Ensures both partners are aligned before finalizing

---

## Testing the Flows

### Using the Test DSL

```kotlin
// Conflict Resolution Flow
testApi(baseUrl, client) {
    partnership {
        users {
            // Create conflict and submit feelings
            val conflictId = user1.conflict {
                create()
                withFeelings("I feel unheard when...")
                returningId()
            }

            user2.conflict {
                fetch(conflictId)
                withFeelings("I feel overwhelmed by...")
            }

            // Wait for transition to resolutions phase
            user1.waitForConflictStatus(conflictId, ConflictStatus.PENDING_RESOLUTIONS)

            // Submit resolutions
            user1.submitResolution(conflictId, "I will...")
            user2.submitResolution(conflictId, "We should...")

            // Wait for AI summary
            user1.waitForConflictStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)

            // Review and approve
            user1.approveSummary(conflictId)
            user2.approveSummary(conflictId)

            // Wait for approval
            user1.waitForConflictStatus(conflictId, ConflictStatus.APPROVED)

            // Verify decision created
            user1.decisions {
                isNotEmpty()
                hasCount(1)
            }
        }
    }
}

// Retrospective Flow
testApi(baseUrl, client) {
    partnership {
        users {
            // Create notes
            val note1 = user1.note("We need to discuss chores").note
            val note2 = user2.note("Want to plan date nights").note

            // Create retro and add notes
            val retroId = user1.retrospective {
                create()
                addNote(note1.id)
                returningId()
            }

            user2.retrospective {
                fetch(retroId)
                addNote(note2.id)
            }

            // Generate discussion points (async)
            user1.retrospective {
                fetch(retroId)
                generatePoints()
            }

            // Both approve
            user1.approveRetro(retroId, "These look good")
            user2.approveRetro(retroId, "I agree, let's discuss")

            // Complete
            user1.completeRetro(retroId, "Great conversation!")

            // Verify completion
            user1.retrospective {
                fetch(retroId)
                assertState {
                    isCompleted()
                    hasSummary("Great conversation!")
                }
            }
        }
    }
}
```

---

## Monitoring & Observability

### Job Status Tracking

```kotlin
// Check job status
val job = user.getJobStatus(jobId)
println("Job ${job.id}: ${job.status}")

// Subscribe to SSE for real-time updates
// GET /api/events/stream?entityId={conflictId}
// Receives:
// - job_started
// - job_completed
// - job_failed
// - job_retrying
```

### Logging Points

1. **Job Lifecycle**: JobProcessorService logs all job state changes
2. **AI Interactions**: All AI provider calls are logged with context
3. **State Transitions**: Conflict and retro status changes are logged
4. **Errors**: Failed jobs include full error messages and stack traces

---

## Future Enhancements

1. **Parallel Feelings Processing**: Allow partners to view each other's feelings before transitioning
2. **Discussion Point Refinement**: Allow partners to request changes to discussion points
3. **Progress Tracking**: Show completion percentage for multi-step flows
4. **Scheduled Retrospectives**: Automatic retro creation on schedule
5. **Conflict Templates**: Pre-defined conflict types with guided flows
6. **AI Coaching**: Proactive suggestions based on partnership patterns

---

## Related Documentation

- [API Documentation](../README.md) - Complete API endpoint reference
- [CLAUDE.md](../../CLAUDE.md) - Full system architecture and design decisions
- [Test DSL Guide](../src/test/kotlin/integration/dsl/README.md) - Writing integration tests
