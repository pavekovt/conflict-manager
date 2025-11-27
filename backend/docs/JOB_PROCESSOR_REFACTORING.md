# JobProcessorService Refactoring Summary

## Overview
Refactored the monolithic `JobProcessorService` into a modular, maintainable architecture with specialized handlers and helper classes.

## Changes Made

### 1. Created Helper Classes

**`UserProfileLoader.kt`** - Centralized user profile loading for AI context
- `loadProfile(userId)` - Load single user profile
- `loadProfiles(user1Id, user2Id)` - Load both partner profiles
- Eliminates repetitive user profile creation code

**`PartnershipContextLoader.kt`** - Centralized partnership context loading
- `loadContext(userId, processJournals)` - Load and update partnership context with journal processing
- `getPartnershipId(userId)` - Get partnership ID for a user
- Handles automatic journal context processing

### 2. Created Job Handlers

Each handler is responsible for a single job type with focused business logic:

**`ProcessFeelingsJobHandler.kt`** - PROCESS_FEELINGS jobs
- Processes user feelings submissions
- Calls AI provider for therapeutic guidance
- Updates conflict status when both partners submit feelings

**`GenerateSummaryJobHandler.kt`** - GENERATE_SUMMARY jobs
- Generates AI conflict summaries from both resolutions
- Handles partnership context loading with journals
- Updates conflict status to SUMMARY_GENERATED

**`GenerateDiscussionPointsJobHandler.kt`** - GENERATE_DISCUSSION_POINTS jobs
- Generates retrospective discussion points from notes
- Processes journals before AI call
- Formats discussion points as structured text

**`UpdatePartnershipContextJobHandler.kt`** - UPDATE_PARTNERSHIP_CONTEXT jobs
- Updates partnership context after conflict resolution
- Integrates conflict summaries into relationship history

### 3. Refactored JobProcessorService

**Before:**
- 460+ lines of code
- Mixed concerns (orchestration + business logic)
- Repetitive user profile creation (4+ times)
- Repetitive partnership context loading
- Hard to test and maintain

**After:**
- ~195 lines of code (58% reduction)
- Clean separation of concerns
- Delegates to specialized handlers
- Improved logging with structured messages
- Better error handling visibility

**Key Improvements:**
- Worker IDs now included in all logs (`Worker-0`, `Worker-1`, `Worker-2`)
- Better structured logging (using `{}` placeholders)
- Cleaner delegation pattern with `delegateToHandler()` method
- Improved failure handling with `handleJobFailure()` method
- More concise polling logic with informative messages

### 4. Updated Koin Configuration

Added dependency injection for:
- `UserProfileLoader`
- `PartnershipContextLoader`
- `ProcessFeelingsJobHandler`
- `GenerateSummaryJobHandler`
- `GenerateDiscussionPointsJobHandler`
- `UpdatePartnershipContextJobHandler`

Updated `JobProcessorService` to inject handlers instead of repositories.

### 5. Fixed Unit Tests

Updated `ConflictServiceTest` to reflect async job processing:
- Changed test name to `submitResolution should queue AI summary job when both resolutions submitted`
- Updated mocks to include `jobRepository` and `jobProcessorService`
- Changed expectations from synchronous AI calls to async job queueing
- Updated expected conflict status from `SUMMARY_GENERATED` to `PROCESSING_SUMMARY`

## Code Quality Improvements

1. **Eliminated Code Duplication**
   - User profile creation consolidated into `UserProfileLoader`
   - Partnership context loading consolidated into `PartnershipContextLoader`
   - Each pattern used 3-4 times, now reused from single location

2. **Single Responsibility Principle**
   - Each handler has one job type to process
   - Helpers have focused responsibilities
   - JobProcessorService only orchestrates

3. **Better Logging**
   - Structured logging with placeholders: `logger.info("Worker-{}: Processing {} job", workerId, jobType)`
   - Log prefixes for handlers: `[FEELINGS]`, `[SUMMARY]`, `[RETRO_POINTS]`, `[CONTEXT_UPDATE]`
   - Better visibility into job lifecycle

4. **Improved Testability**
   - Smaller, focused classes easier to unit test
   - Clear dependencies via constructor injection
   - Mockable interfaces for testing

5. **Easier Maintenance**
   - Add new job type = create new handler + register in Koin
   - Modify job logic = edit single handler file
   - Debug issues = follow logs with clear prefixes

## File Structure

```
backend/src/main/kotlin/
├── service/
│   ├── JobProcessorService.kt          (195 lines, was 460+)
│   └── job/                             (NEW)
│       ├── UserProfileLoader.kt         (37 lines)
│       ├── PartnershipContextLoader.kt  (41 lines)
│       ├── ProcessFeelingsJobHandler.kt (118 lines)
│       ├── GenerateSummaryJobHandler.kt (75 lines)
│       ├── GenerateDiscussionPointsJobHandler.kt (60 lines)
│       └── UpdatePartnershipContextJobHandler.kt (75 lines)
```

## Benefits

1. **Reduced Cognitive Load** - Each file has a single, clear purpose
2. **Faster Debugging** - Logs clearly identify which handler is running
3. **Easier Testing** - Smaller units with focused logic
4. **Better Code Reuse** - Helpers eliminate duplication
5. **Scalability** - Easy to add new job types without modifying existing code
6. **Maintainability** - Changes localized to specific handlers

## Migration Notes

- All existing functionality preserved
- No API changes
- Tests updated to reflect async processing model
- Backward compatible with existing database and code

## Testing

✅ All unit tests pass
✅ Build succeeds
✅ No compilation errors
✅ Dependency injection configured correctly
