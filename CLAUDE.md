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

  Tech Stack (DECIDED):
  - Backend: Kotlin + Ktor + Exposed
  - Database: Supabase (Postgres only, for persistence)
  - Frontend: PWA (SvelteKit or Next.js - TBD)
  - AI: Claude API (or OpenAI) for summarization
  - Deployment: Docker (homelab or cloud for reliability)

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

  Supabase Usage:
  - Data persistence only (Postgres via JDBC)
  - Users table (id, name, notification_token)
  - Notes (id, user_id, content, created_at, status, visibility)
  - Conflicts (id, created_at, status, triggered_by)
  - Resolutions (id, conflict_id, user_id, resolution_text, submitted_at)
  - AISummaries (id, conflict_id, summary_text, created_at)
  - Decisions (id, summary, created_at, category)
  - Retrospectives (id, scheduled_date, notes_included, summary, created_at)

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

  ## API Endpoints (planned)

  **Notes:**
  - POST /api/notes - create private note
  - GET /api/notes - get my notes (filtered by status)
  - PATCH /api/notes/:id - update note (mark ready for discussion)
  - DELETE /api/notes/:id - delete my note

  **Conflicts:**
  - POST /api/conflicts - initiate conflict resolution
  - POST /api/conflicts/:id/resolution - submit my resolution understanding
  - GET /api/conflicts/:id - get conflict status
  - GET /api/conflicts/:id/summary - get AI summary (after both submit)
  - PATCH /api/conflicts/:id/approve - approve final summary

  **Decisions:**
  - GET /api/decisions - get decision backlog
  - GET /api/decisions/:id - get specific decision
  - PATCH /api/decisions/:id - mark as reviewed/completed

  **Retrospectives:**
  - POST /api/retrospectives - trigger retro (manual or scheduled)
  - GET /api/retrospectives/:id - get retro details
  - GET /api/retrospectives/:id/notes - get notes for this retro
  - POST /api/retrospectives/:id/complete - finalize retro with summary

  **AI Integration:**
  - POST /api/ai/summarize-conflict - summarize two resolutions
  - POST /api/ai/generate-retro - generate retro discussion points

  ## Privacy & Security Considerations

  - Notes are PRIVATE until explicitly included in retro
  - Cannot read partner's notes outside of retro context
  - Both partners must submit resolutions before AI summary
  - Decision backlog visible to both (shared agreements)
  - Option to delete notes before they enter retro
  - Retro requires both partners to be "present" (active session)

  ## AI Summarization Strategy

  **Conflict Resolution Summary:**
  - Input: Two independent resolution texts
  - Prompt: "Analyze these two perspectives and create a neutral summary of the agreement"
  - Output: "We decided that..." statement
  - Goal: Find common ground, highlight any discrepancies

  **Retrospective Discussion Points:**
  - Input: Multiple notes from both partners
  - Prompt: "Generate discussion points from these concerns, group by theme"
  - Output: Organized discussion agenda
  - Goal: Structure the conversation productively

  ## Frontend Decision

  Recommendation: SvelteKit (clean code, fast dev) OR Next.js (mature ecosystem)
  - Both support PWA out of box
  - SvelteKit = cleaner code, easier maintenance
  - Next.js = larger ecosystem, more examples
  - Mobile-first is CRITICAL (capture thoughts in the moment)

  ## Next Steps

  1. Pick frontend framework (SvelteKit vs Next.js)
  2. Scaffold Ktor project structure
  3. Set up Supabase connection with Exposed
  4. Design database schema (Users, Notes, Conflicts, Resolutions, Decisions, Retrospectives)
  5. Implement authentication & authorization
  6. Build Notes CRUD with privacy controls
  7. Implement Conflict Resolution workflow
  8. Integrate Claude API for summarization
  9. Build Retrospective system
  10. Build PWA frontend with mobile-first UX
  11. Add push notifications for scheduled retros
- I will code everything myself for practise