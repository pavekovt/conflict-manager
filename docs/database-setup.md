# Database Setup Guide

## Quick Start

### 1. Start PostgreSQL with Docker Compose

```bash
docker-compose up -d
```

This will start:
- **PostgreSQL 16** on `localhost:5432`
- **pgAdmin** on `http://localhost:5050`

### 2. Verify PostgreSQL is Running

```bash
docker-compose ps
```

You should see both `conflict-manager-db` and `conflict-manager-pgadmin` running.

### 3. Access pgAdmin (Optional)

Open `http://localhost:5050` in your browser:
- Email: `admin@example.com`
- Password: `admin`

To connect to the database in pgAdmin:
1. Right-click "Servers" → "Register" → "Server"
2. **General** tab: Name = `Conflict Manager DB`
3. **Connection** tab:
   - Host: `postgres` (or `localhost` if accessing from host machine)
   - Port: `5432`
   - Database: `conflict_manager`
   - Username: `dev_user`
   - Password: `dev_password`

## Exposed ORM Configuration

### Connection URL for Exposed

**JDBC URL:**
```
jdbc:postgresql://localhost:5432/conflict_manager
```

**Full connection details:**
- **Driver**: `org.postgresql.Driver`
- **URL**: `jdbc:postgresql://localhost:5432/conflict_manager`
- **User**: `dev_user`
- **Password**: `dev_password`

### Gradle Dependencies

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")

    // PostgreSQL JDBC Driver
    implementation("org.postgresql:postgresql:42.7.1")

    // HikariCP for connection pooling (recommended)
    implementation("com.zaxxer:HikariCP:5.1.0")
}
```

### Application Configuration

**Option 1: application.conf (HOCON)**

Create `src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080
    }
}

database {
    driver = "org.postgresql.Driver"
    url = "jdbc:postgresql://localhost:5432/conflict_manager"
    user = "dev_user"
    password = "dev_password"
    maxPoolSize = 10
}

ai {
    provider = "claude"  # or "openai"
    apiKey = ${?AI_API_KEY}  # from environment variable
}
```

**Option 2: application.yaml**

Create `src/main/resources/application.yaml`:

```yaml
ktor:
  deployment:
    port: 8080

database:
  driver: org.postgresql.Driver
  url: jdbc:postgresql://localhost:5432/conflict_manager
  user: dev_user
  password: dev_password
  maxPoolSize: 10

ai:
  provider: claude
  apiKey: ${AI_API_KEY}
```

### Database Initialization Code

**DatabaseFactory.kt:**

```kotlin
package com.example.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val driver = config.property("database.driver").getString()
        val url = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        val maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toInt() ?: 10

        val database = Database.connect(
            datasource = createHikariDataSource(
                url = url,
                driver = driver,
                user = user,
                password = password,
                maxPoolSize = maxPoolSize
            )
        )

        // Create tables
        transaction(database) {
            SchemaUtils.create(
                Users,
                Notes,
                Conflicts,
                Resolutions,
                AISummaries,
                Decisions,
                Retrospectives,
                RetrospectiveNotes
            )
        }
    }

    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String,
        password: String,
        maxPoolSize: Int
    ): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }
}

// Helper function for all database queries
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

### Initialize Database in Application.kt

```kotlin
package com.example

import com.example.db.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init(environment.config)

    // Configure routes, plugins, etc.
    configureRouting()
    configureSerialization()
    configureAuth()
}
```

## Environment Variables (Production)

For production, use environment variables instead of hardcoded credentials:

**application.conf:**
```hocon
database {
    driver = "org.postgresql.Driver"
    url = ${DATABASE_URL}
    user = ${DATABASE_USER}
    password = ${DATABASE_PASSWORD}
    maxPoolSize = ${?DATABASE_MAX_POOL_SIZE}
}
```

**Run with environment variables:**
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/conflict_manager"
export DATABASE_USER="dev_user"
export DATABASE_PASSWORD="dev_password"
export AI_API_KEY="your-api-key-here"

./gradlew run
```

## Database Migrations (Recommended for Production)

While `SchemaUtils.create()` works for development, use a migration tool for production:

### Option 1: Flyway

**Gradle dependency:**
```kotlin
implementation("org.flywaydb:flyway-core:10.4.1")
implementation("org.flywaydb:flyway-database-postgresql:10.4.1")
```

**Migration files in `src/main/resources/db/migration/`:**

`V1__initial_schema.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE note_status AS ENUM ('DRAFT', 'READY_FOR_DISCUSSION', 'DISCUSSED', 'ARCHIVED');
CREATE TYPE mood AS ENUM ('FRUSTRATED', 'ANGRY', 'SAD', 'CONCERNED', 'NEUTRAL');
CREATE TYPE conflict_status AS ENUM ('PENDING_RESOLUTIONS', 'SUMMARY_GENERATED', 'REFINEMENT', 'APPROVED', 'ARCHIVED');
CREATE TYPE decision_status AS ENUM ('ACTIVE', 'REVIEWED', 'ARCHIVED');
CREATE TYPE retro_status AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    notification_token VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status note_status NOT NULL DEFAULT 'DRAFT',
    mood mood,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conflicts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    initiated_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status conflict_status NOT NULL DEFAULT 'PENDING_RESOLUTIONS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resolutions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conflict_id UUID NOT NULL REFERENCES conflicts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resolution_text TEXT NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(conflict_id, user_id)
);

CREATE TABLE ai_summaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conflict_id UUID NOT NULL REFERENCES conflicts(id) ON DELETE CASCADE,
    summary_text TEXT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    approved_by_user_1 BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_user_2 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conflict_id UUID REFERENCES conflicts(id) ON DELETE SET NULL,
    summary TEXT NOT NULL,
    category VARCHAR(100),
    status decision_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE TABLE retrospectives (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scheduled_date TIMESTAMP,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status retro_status NOT NULL,
    ai_discussion_points TEXT,
    final_summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE retrospective_notes (
    retrospective_id UUID NOT NULL REFERENCES retrospectives(id) ON DELETE CASCADE,
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    PRIMARY KEY (retrospective_id, note_id)
);

-- Indexes for performance
CREATE INDEX idx_notes_user_id ON notes(user_id);
CREATE INDEX idx_notes_status ON notes(status);
CREATE INDEX idx_conflicts_status ON conflicts(status);
CREATE INDEX idx_resolutions_conflict_id ON resolutions(conflict_id);
CREATE INDEX idx_decisions_status ON decisions(status);
CREATE INDEX idx_retrospectives_status ON retrospectives(status);
```

**Initialize Flyway:**
```kotlin
import org.flywaydb.core.Flyway

fun initFlyway(url: String, user: String, password: String) {
    val flyway = Flyway.configure()
        .dataSource(url, user, password)
        .load()

    flyway.migrate()
}
```

## Useful Docker Commands

```bash
# Start containers
docker-compose up -d

# Stop containers
docker-compose down

# View logs
docker-compose logs -f postgres

# Access PostgreSQL CLI
docker exec -it conflict-manager-db psql -U dev_user -d conflict_manager

# Reset database (WARNING: deletes all data)
docker-compose down -v
docker-compose up -d

# Backup database
docker exec conflict-manager-db pg_dump -U dev_user conflict_manager > backup.sql

# Restore database
cat backup.sql | docker exec -i conflict-manager-db psql -U dev_user -d conflict_manager
```

## Common PostgreSQL Commands

Once connected via `psql`:

```sql
-- List all tables
\dt

-- Describe a table
\d users

-- List all databases
\l

-- Switch database
\c conflict_manager

-- Show all users
\du

-- Quit
\q
```

## Testing Connection

Quick test to verify everything works:

```kotlin
fun main() {
    Database.connect(
        url = "jdbc:postgresql://localhost:5432/conflict_manager",
        driver = "org.postgresql.Driver",
        user = "dev_user",
        password = "dev_password"
    )

    transaction {
        addLogger(StdOutSqlLogger)

        // Test query
        println("Database connected successfully!")
    }
}
```
