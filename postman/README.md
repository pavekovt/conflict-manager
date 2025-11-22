# Postman Collection - Morning Brief API

## Files

- `Morning-Brief-API.postman_collection.json` - Complete API collection with all endpoints
- `Morning-Brief.postman_environment.json` - Environment variables for local development

## Setup Instructions

1. **Import Collection**
   - Open Postman
   - Click "Import" button
   - Select `Morning-Brief-API.postman_collection.json`

2. **Import Environment**
   - Click "Import" button again
   - Select `Morning-Brief.postman_environment.json`
   - Select "Morning Brief - Local" from the environment dropdown

3. **Authentication Flow**
   - Run "Auth > Register" to create a new user
   - Tokens are automatically saved to collection variables
   - All subsequent requests will use the saved token automatically
   - Use "Auth > Login" to login with existing credentials
   - Use "Auth > Refresh Token" when access token expires

## Collection Structure

```
Morning Brief API/
├── Auth/
│   ├── Register (saves tokens)
│   ├── Login (saves tokens)
│   ├── Refresh Token (updates tokens)
│   └── Logout
├── Partnerships/
│   ├── Send Partner Invitation (saves partnershipId)
│   ├── Get Invitations
│   ├── Accept Invitation
│   ├── Reject Invitation
│   ├── Get Current Partnership
│   └── End Partnership
├── Notes/
│   ├── Create Note (saves noteId)
│   ├── Get My Notes
│   ├── Get Notes by Status
│   ├── Get Note by ID
│   ├── Update Note
│   └── Delete Note
├── Conflicts/
│   ├── Initiate Conflict (saves conflictId)
│   ├── Get My Conflicts
│   ├── Get Conflict by ID
│   ├── Submit Resolution
│   ├── Get AI Summary
│   ├── Approve Summary
│   ├── Request Refinement
│   └── Archive Conflict
├── Decisions/
│   ├── Get All Decisions
│   ├── Get Decisions by Status
│   ├── Get Decision by ID
│   ├── Mark Decision as Reviewed
│   └── Archive Decision
├── Retrospectives/
│   ├── Trigger Manual Retro (saves retroId)
│   ├── Get All Retrospectives
│   ├── Get Retro by ID
│   ├── Get Retro Notes
│   ├── Add Note to Retro
│   ├── Complete Retro
│   └── Cancel Retro
└── Notifications/
    ├── Subscribe to Notifications
    └── Unsubscribe from Notifications
```

## Environment Variables

### Base Configuration
- `baseUrl` - API base URL (default: http://localhost:8080)

### User 1 (Primary Test User)
- `user1Email` - alice@example.com
- `user1Name` - Alice
- `user1Password` - password123

### User 2 (Partner Test User)
- `user2Email` - bob@example.com
- `user2Name` - Bob
- `user2Password` - password456

## Collection Variables (Auto-managed)

These are automatically set by test scripts:
- `accessToken` - JWT access token (auto-saved after login/register)
- `refreshToken` - JWT refresh token (auto-saved after login/register)
- `lastPartnershipId` - Last created partnership ID
- `lastNoteId` - Last created note ID
- `lastConflictId` - Last created conflict ID
- `lastDecisionId` - Last created decision ID
- `lastRetroId` - Last created retrospective ID

## Authentication

The collection uses **Bearer Token** authentication:
- Auth requests (Register, Login, Refresh) have `auth: noauth`
- All other requests automatically include `Authorization: Bearer {{accessToken}}`
- Pre-request script attaches token to all authenticated requests
- Post-response scripts save tokens from auth endpoints

## Partnership Setup Workflow

**IMPORTANT:** Before using any conflict, decision, or retrospective features, users must establish a partnership:

1. **Register User 1 (Alice)**
   - Run "Auth > Register" (uses `{{user1Email}}`)
   - Token automatically saved

2. **Register User 2 (Bob)**
   - Manually change body to use `{{user2Email}}`, `{{user2Name}}`, `{{user2Password}}`
   - Run "Auth > Register"
   - Token automatically saved (overwrites User 1's token)

3. **Send Partnership Invitation (as Bob)**
   - Run "Partnerships > Send Partner Invitation"
   - Uses `{{user2Email}}` to invite Alice
   - Partnership ID saved to `{{lastPartnershipId}}`

4. **Switch to Alice**
   - Login as Alice: "Auth > Login" with `{{user1Email}}`

5. **Accept Invitation (as Alice)**
   - Run "Partnerships > Get Invitations" to see pending invitation
   - Run "Partnerships > Accept Invitation" with saved `{{lastPartnershipId}}`
   - Partnership is now active!

6. **Verify Partnership**
   - Run "Partnerships > Get Current Partnership" to confirm

## Testing Multi-User Scenarios

Once partnership is established, test conflict resolution:

1. **Create Conflict as User 1**
   - Ensure you're logged in as User 1
   - Run "Conflicts > Initiate Conflict"
   - Conflict ID saved to `{{lastConflictId}}`

2. **Submit Resolution as User 1**
   - Run "Conflicts > Submit Resolution"

3. **Switch to User 2**
   - Run "Auth > Login" with `{{user2Email}}` and `{{user2Password}}`

4. **Submit Resolution as User 2**
   - Run "Conflicts > Submit Resolution" (same conflictId)
   - AI summary will be generated

5. **Approve Summary (Both Users)**
   - Run "Conflicts > Approve Summary" as User 2
   - Switch back to User 1, login, and approve
   - Decision created in backlog

## Tips

- Run requests in order within each folder for best results
- Check the "Tests" tab to see automatic variable saving
- Monitor collection variables to track entity IDs
- Use Console (View > Show Postman Console) to debug scripts
- Status codes and response schemas are validated in test scripts

## Creating Additional Environments

To create a staging/production environment:
1. Duplicate `Morning-Brief.postman_environment.json`
2. Rename to `Morning-Brief-Production.postman_environment.json`
3. Update `baseUrl` to production URL
4. Import into Postman
