# Conflict Resolution Manager

A couple's relationship management app with AI-assisted conflict resolution and retrospectives.

## Quick Start

### 1. Start Database

```bash
# Start PostgreSQL and pgAdmin
docker-compose up -d

# Verify containers are running
docker-compose ps
```

### 2. Set Up Environment

```bash
# Copy example env file
cp .env.example .env

# Edit .env and add your AI API key
# AI_API_KEY=your-actual-api-key
```

### 3. Run Backend (Coming Soon)

```bash
cd backend
./gradlew run
```

Backend will be available at `http://localhost:8080`

### 4. Run Frontend (Coming Soon)

```bash
cd frontend
npm install
npm run dev
```

Frontend will be available at `http://localhost:3000`

## Tech Stack

- **Backend**: Kotlin + Ktor + Exposed ORM
- **Database**: PostgreSQL 16 (via Docker)
- **Frontend**: Next.js PWA with TypeScript
- **Auth**: Custom JWT authentication
- **AI**: Abstraction layer (Claude API / OpenAI)
- **Deployment**: Docker

## Database Access

**PostgreSQL:**
- Host: `localhost`
- Port: `5432`
- Database: `conflict_manager`
- User: `dev_user`
- Password: `dev_password`

**pgAdmin UI:**
- URL: `http://localhost:5050`
- Email: `admin@example.com`
- Password: `admin`

**JDBC Connection URL:**
```
jdbc:postgresql://localhost:5432/conflict_manager
```

## Project Structure

```
.
├── backend/              # Ktor backend (Kotlin)
├── frontend/             # Next.js frontend (TypeScript)
├── docs/                 # Documentation
│   ├── database-setup.md
│   └── repository-patterns.md
├── docker-compose.yml    # PostgreSQL + pgAdmin
├── CLAUDE.md            # Project design & roadmap
└── README.md
```

## Documentation

- [Database Setup Guide](docs/database-setup.md) - Complete database configuration
- [Repository Patterns](docs/repository-patterns.md) - Exposed DSL examples
- [Project Design](CLAUDE.md) - Full architecture & roadmap

## Features

### Core (v1)
- ✅ Private notes system
- ✅ Conflict resolution workflow
- ✅ AI-powered summarization
- ✅ Decision backlog
- ✅ Retrospective system

### Future Enhancements
- Export retrospective summaries to PDF
- Full-text search for decisions
- Analytics & insights
- Mood trends over time
- Voice notes
- Calendar integration

## Development Roadmap

See [CLAUDE.md](CLAUDE.md) for the complete 10-week development plan.

**Current Status:** Phase 1 - Backend Foundation

## Useful Commands

### Docker

```bash
# Start containers
docker-compose up -d

# Stop containers
docker-compose down

# View logs
docker-compose logs -f postgres

# Reset database (WARNING: deletes all data)
docker-compose down -v && docker-compose up -d

# Backup database
docker exec conflict-manager-db pg_dump -U dev_user conflict_manager > backup.sql

# Access PostgreSQL CLI
docker exec -it conflict-manager-db psql -U dev_user -d conflict_manager
```

### PostgreSQL CLI

```sql
-- List all tables
\dt

-- Describe a table
\d users

-- Show all data in a table
SELECT * FROM users;

-- Quit
\q
```

## License

Private project for personal use.
