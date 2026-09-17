# Drill API

Spring Boot / Java 21 backend for Drill's study workspace. The companion UI is [os439-frontend](https://github.com/ybelai2/os439-frontend).

## What is implemented

- Email/password signup, login, current user, and logout. BCrypt password hashes; random seven-day bearer sessions with only SHA-256 token hashes stored in PostgreSQL.
- Private courses with codes, semesters, and descriptions; classes/lectures with notes and studied status; saved question/flashcard decks.
- Create, read, update, and delete endpoints at every level. Every nested request verifies the signed-in owner. Deleting a parent cascades to its children.
- PowerPoint parsing and Gemini study generation with course context; generated decks are saved under a class. Original PowerPoint binaries are **not** retained.
- Flyway schema migrations, request validation, an explicit CORS allowlist, upload limits, and per-instance throttling (30 auth requests/IP/minute, 10 generations/account/hour).

## Local development

Requires Java 21 and Docker (for PostgreSQL):

```sh
docker compose up -d
export GEMINI_API_KEY=your_key
bash mvnw spring-boot:run
```

The API defaults to `http://localhost:8080` with the development database from `compose.yaml`. Signup and CRUD do not require a Gemini key. Copy the values in `.env.example` into your shell or hosting environment; Spring does not automatically load a `.env` file.

```sh
bash mvnw verify
```

Tests run migrations against H2 in PostgreSQL compatibility mode and exercise authentication, token expiry/revocation, CRUD, cascade deletion, validation, CORS, and cross-account denial. CI uses Java 21. H2 tests do not replace a deployment smoke test against PostgreSQL.

## Configuration / deployment

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://host:5432/database?sslmode=require` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL credentials |
| `GEMINI_API_KEY` | Gemini API key, required only for generation |
| `GEMINI_MODEL` | Defaults to `gemini-2.5-flash` |
| `ALLOWED_ORIGINS` | Comma-separated exact frontend origins (no trailing slash) |
| `PORT` | Defaults to 8080 |

On Render, set the database credentials and the frontend origin. A `postgresql://user:password@host/database` URL must be converted to a `jdbc:postgresql://host/database` URL with credentials in the separate variables. Use a durable managed PostgreSQL database and HTTPS in production. Do not use the local demo credentials in production.

Flyway applies `V1__study_library.sql` on first startup and Hibernate validates it. Back up an existing database before rollout. If the target schema already contains unrelated tables and no Flyway history, use a new empty schema/database or reconcile migrations deliberately; do not automatically baseline an unknown schema.

Coordinate rollout with the frontend branch: protected upload endpoints now require authentication, so an older UI cannot generate decks. Configure `VITE_API_URL` on Vercel to point to this backend and redeploy the frontend. `/ping` remains public for health checks.

## API

Public: `POST /api/auth/signup` (`name`, `email`, `password`, minimum 12 characters), `POST /api/auth/login` (`email`, `password`). Both return `{token, expiresAt, user}`.

Send `Authorization: Bearer <token>` on all other requests:

| Path | Methods |
|---|---|
| `/api/auth/me` | GET |
| `/api/auth/logout` | POST |
| `/api/courses` | GET, POST |
| `/api/courses/{courseId}` | GET, PUT, DELETE |
| `/api/courses/{courseId}/classes` | GET, POST |
| `/api/courses/{courseId}/classes/{classId}` | GET, PUT, DELETE |
| `/api/courses/{courseId}/classes/{classId}/decks` | GET, POST |
| `/api/courses/{courseId}/classes/{classId}/decks/{deckId}` | GET, PUT, DELETE |
| `/api/courses/{courseId}/classes/{classId}/generate` | POST multipart `title`, `files` |
| `/api/extract` | POST multipart `files` |
| `/api/generate` | POST multipart `files` (legacy response, not saved) |

Course write body: `{title, code, semester, description}`. Class: `{title, notes, studied}`. Deck: `{title, content}` where `content` is a JSON string containing `flashcards` and `questions`. Updates use full PUT bodies. IDs belong to the authenticated account; foreign IDs return 404.

## Current boundaries

Email verification, password reset, persisted test scores, asynchronous generation, and original upload storage are not implemented. Study materials and class studied status persist; per-question practice progress does not. Sessions survive server restarts and expire after seven days; the UI stores the token in sessionStorage, so closing the tab normally requires signing in again. Add shared rate limiting for multiple replicas and scheduled cleanup of expired session rows as traffic grows. Generation is synchronous and can take several minutes.
