# LOOP Platform — Phase 1 Implementation & Testing Guide

> **Phase:** 1 — Backend Skeleton, Security & JWT Auth
> **Stack:** Java 17 · Spring Boot 3.3 · PostgreSQL 16 (local install)
> **Date:** May 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Sequence of Implementation](#3-sequence-of-implementation)
   - [Layer 1 — Foundation](#layer-1--foundation)
   - [Layer 2 — Data Model](#layer-2--data-model)
   - [Layer 3 — Shared Concerns](#layer-3--shared-concerns)
   - [Layer 4 — Security Machinery](#layer-4--security-machinery)
   - [Layer 5 — Auth Feature](#layer-5--auth-feature)
4. [How to Run](#4-how-to-run)
5. [What Can Be Tested at This Point](#5-what-can-be-tested-at-this-point)
6. [⚠️ Why Login Cannot Work Yet](#6-️-why-login-cannot-work-yet)
7. [Expected Startup Logs](#7-expected-startup-logs)
8. [Common Errors & Fixes](#8-common-errors--fixes)
9. [What Phase 1 Does NOT Include](#9-what-phase-1-does-not-include)

---

## 1. Overview

Phase 1 establishes the complete security backbone of the LOOP backend. By the
end of this phase, the application starts up, connects to a local PostgreSQL
database, auto-creates the `users` table via Hibernate, and enforces JWT
authentication on all routes except the public auth endpoints.

No domain data (markets, segments, themes, features) exists yet. No user
accounts exist in the database yet. The security infrastructure is fully wired
and ready — it simply has no users to authenticate against until Phase 2
seeds the database.

---

## 2. Prerequisites

### Local PostgreSQL setup

Run these commands once before starting the application for the first time:

```bash
# Connect to your local PostgreSQL instance
psql -U postgres

# Inside the psql shell
CREATE DATABASE loop_db;
CREATE USER admin WITH PASSWORD 'secret';
GRANT ALL PRIVILEGES ON DATABASE loop_db TO admin;
\q
```

### Verify the connection

```bash
psql -h localhost -U admin -d loop_db
# Should connect successfully — type \q to exit
```

### Java & Maven

```bash
java -version    # Must be Java 17 or higher
mvn -version     # Must be Maven 3.6 or higher
```

---

## 3. Sequence of Implementation

Write the files in this exact order. Each layer only depends on what came
before it — every import you write will already exist by the time you need it.

---

### Layer 1 — Foundation

*What everything else is built on. No Java code yet — just build config and app
entry point.*

| # | File | Why this first |
|---|---|---|
| 1 | `pom.xml` | Defines every library available to the project — nothing compiles without this |
| 2 | `application.properties` | Defines config values (`jwt.secret`, `cookie.name`, DB URL) that every layer reads via `@Value` |
| 3 | `LoopApplication.java` | The `main()` entry point — Spring Boot scans from this class's package downward |

**Key thing to verify after Layer 1:**
The Maven dependencies resolve without errors. Run:

```bash
./mvnw dependency:resolve
```

---

### Layer 2 — Data Model

*Defines what a User looks like in the database and how to query them. The
security layer needs this to load users during authentication.*

| # | File | Why this order |
|---|---|---|
| 4 | `Role.java` | A plain enum — no dependencies at all; simplest possible file |
| 5 | `User.java` | The JPA entity; depends on `Role` |
| 6 | `UserRepository.java` | Spring Data interface; depends on `User` |
| 7 | `UserResponse.java` | Outbound DTO; depends on `Role` — written here so it exists when `AuthService` needs it |

**Key relationships established:**

```
Role (enum)
  └── User (@Entity)  →  maps to the `users` table in PostgreSQL
        └── UserRepository  →  provides findByEmail(), existsByEmail()
```

---

### Layer 3 — Shared Concerns

*Cross-cutting classes that every other layer will use. Written before the
security and auth layers so they can import them freely.*

| # | File | Why this order |
|---|---|---|
| 8 | `ErrorResponse.java` | The JSON shape returned for all errors — no dependencies |
| 9 | `ResourceNotFoundException.java` | A custom `RuntimeException` — no dependencies |
| 10 | `GlobalExceptionHandler.java` | Uses both `ErrorResponse` and `ResourceNotFoundException`; must be last in this layer |

**What `GlobalExceptionHandler` catches at this phase:**

| Exception | HTTP Status | When triggered |
|---|---|---|
| `ResourceNotFoundException` | 404 | Entity not found by ID |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails on a request body field |
| `BadCredentialsException` | 401 | Wrong email or password on login |
| `Exception` (catch-all) | 500 | Any unhandled runtime exception |

---

### Layer 4 — Security Machinery

*The JWT infrastructure. Written in dependency order — each file needs the one
before it.*

| # | File | Why this order |
|---|---|---|
| 11 | `JwtUtil.java` | Pure utility class — generates and validates tokens; no Spring dependencies other than `@Value` |
| 12 | `CustomUserDetailService.java` | Bridges the `User` entity to Spring Security's `UserDetails`; needs `UserRepository` |
| 13 | `JwtAuthFilter.java` | Reads the cookie on every request; needs both `JwtUtil` and `CustomUserDetailService` |
| 14 | `SecurityConfig.java` | Wires the entire filter chain together; needs all three files above — always written last in this layer |

**The flow these four files create together:**

```
Incoming HTTP request
        │
        ▼
JwtAuthFilter          → reads the loop_token cookie
        │                 calls JwtUtil.validateToken()
        │                 calls CustomUserDetailService.loadUserByUsername()
        │                 sets SecurityContextHolder
        ▼
SecurityConfig         → checks if the route requires auth
        │                 passes authenticated requests through
        │                 rejects unauthenticated requests with 401
        ▼
Controller / 401
```

**Key decisions in `SecurityConfig`:**

```java
// These routes need NO cookie — public access
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/markets").permitAll()
.requestMatchers(HttpMethod.GET, "/api/segments").permitAll()
.requestMatchers(HttpMethod.GET, "/api/catalogue/**").permitAll()

// Everything else — valid JWT cookie required
.anyRequest().authenticated()
```

---

### Layer 5 — Auth Feature

*The actual HTTP endpoints. Written last because they depend on everything
above. DTOs are written before the service and controller that use them.*

| # | File | Why this order |
|---|---|---|
| 15 | `LoginRequest.java` | Inbound DTO — what the client sends; no dependencies |
| 16 | `AuthResponse.java` | Outbound DTO — what the server returns; no dependencies |
| 17 | `AuthService.java` | Business logic; needs `JwtUtil`, `UserRepository`, both DTOs, and `AuthenticationManager` |
| 18 | `AuthController.java` | HTTP layer — thin wrapper around `AuthService`; written last because it depends on the service |

**The three endpoints exposed at this phase:**

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | No | Validates credentials, sets `httpOnly` cookie |
| `POST` | `/api/auth/logout` | No | Clears cookie by setting `Max-Age=0` |
| `GET` | `/api/auth/me` | Yes (JWT cookie) | Returns `{ email, role }` from the active session |

---

## 4. How to Run

### Step 1 — Build the project

```bash
cd loop-backend
./mvnw clean install -DskipTests
```

### Step 2 — Start the application

```bash
./mvnw spring-boot:run
```

### Step 3 — Confirm startup

You should see this in the terminal output:

```
Started LoopApplication in X.XXX seconds
Tomcat started on port 8080
```

Hibernate will also log the `CREATE TABLE` statement for the `users` table on
the very first run:

```sql
create table users (
    id bigserial not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    created_at timestamp not null,
    primary key (id)
)
```

---

## 5. What Can Be Tested at This Point

Even without any user data in the database, the following can be verified to
confirm the security layer is wired correctly.

---

### Test 1 — Protected route returns 401 without a cookie

```bash
curl -v http://localhost:8080/api/auth/me
```

**Expected response:**

```
HTTP/1.1 401 Unauthorized
```

This confirms `JwtAuthFilter` and `SecurityConfig` are working — unauthenticated
requests to protected routes are rejected before reaching the controller.

---

### Test 2 — Public routes are accessible without a cookie

```bash
curl -v http://localhost:8080/api/markets
curl -v http://localhost:8080/api/segments
```

**Expected response:**

```
HTTP/1.1 200 OK
[]
```

An empty array is returned because no market or segment data exists yet (Phase 2
seeds this data). The important thing is that the response is `200`, not `401`
— confirming the public route exemptions in `SecurityConfig` work correctly.

---

### Test 3 — Login with missing fields returns 400

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected response:**

```json
{
  "timestamp": "2026-05-25T10:00:00",
  "status": 400,
  "message": "Email is required, Password is required",
  "path": "/api/auth/login"
}
```

This confirms `@Valid` validation on `LoginRequest` and the
`GlobalExceptionHandler` are both working correctly.

---

### Test 4 — Login with invalid email format returns 400

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"notanemail","password":"somepassword"}'
```

**Expected response:**

```json
{
  "timestamp": "2026-05-25T10:00:00",
  "status": 400,
  "message": "Email must be a valid email address",
  "path": "/api/auth/login"
}
```

---

### Test 5 — Tampered or fake JWT cookie returns 401

```bash
curl -v http://localhost:8080/api/auth/me \
  --cookie "loop_token=this.is.a.fake.token"
```

**Expected response:**

```
HTTP/1.1 401 Unauthorized
```

This confirms `JwtAuthFilter` correctly rejects malformed or unsigned tokens.

---

### Test 6 — Verify the users table was created in PostgreSQL

```bash
psql -h localhost -U admin -d loop_db -c "\dt"
```

**Expected output:**

```
        List of relations
 Schema |  Name  | Type  | Owner
--------+--------+-------+-------
 public | users  | table | admin
```

This confirms Hibernate successfully read the `User` entity and auto-created
the table on startup.

---

### Test 7 — Verify the table structure matches the schema

```bash
psql -h localhost -U admin -d loop_db -c "\d users"
```

**Expected output:**

```
                        Table "public.users"
    Column     |            Type             | Nullable |      Default
---------------+-----------------------------+----------+--------------------
 id            | bigint                      | not null | nextval(...)
 email         | character varying(255)      | not null |
 password_hash | character varying(255)      | not null |
 role          | character varying(20)       | not null |
 created_at    | timestamp without time zone | not null |
```

---

## 6. ⚠️ Why Login Cannot Work Yet

> **This is expected behaviour — not a bug.**

Attempting to log in with real credentials at this point will always return
a `401 Unauthorized`:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@loop.com","password":"Loop@Admin1"}'
```

```json
{
  "timestamp": "2026-05-25T10:00:00",
  "status": 401,
  "message": "Invalid email or password",
  "path": "/api/auth/login"
}
```

**Why this happens:**

The authentication flow works as follows:

```
POST /api/auth/login
        │
        ▼
AuthService.login()
        │
        ▼
AuthenticationManager.authenticate()
        │
        ▼
CustomUserDetailService.loadUserByUsername("admin@loop.com")
        │
        ▼
UserRepository.findByEmail("admin@loop.com")
        │
        ▼
  ← returns empty (no rows in users table)
        │
        ▼
throws UsernameNotFoundException → Spring converts to BadCredentialsException
        │
        ▼
GlobalExceptionHandler → 401 Unauthorized
```

The `users` table exists and is empty. There are no accounts to authenticate
against because the `DataSeeder` — which inserts the admin account with a
BCrypt-hashed password — is built in **Phase 2**.

**What Phase 2 adds to unblock this:**

- `DataSeeder.java` — an `ApplicationRunner` that runs once on startup and
  inserts the admin user using `BCryptPasswordEncoder` to hash the password
  correctly before storage.
- All domain entities (markets, segments, themes, epics, features, user stories).
- Idempotency check (`segmentRepository.count() == 0`) so the seeder only
  runs on a fresh database and is safe across restarts.

Once Phase 2 is complete, the full login → cookie → protected route flow
will work end to end.

---

## 7. Expected Startup Logs

A healthy Phase 1 startup will produce logs similar to the following:

```
INFO  com.example.loop.LoopApplication     - Starting LoopApplication
INFO  o.s.b.w.embedded.tomcat.TomcatWebServer - Tomcat initialized with port 8080
INFO  com.zaxxer.hikari.HikariDataSource   - HikariPool-1 - Starting
INFO  com.zaxxer.hikari.HikariDataSource   - HikariPool-1 - Start completed
INFO  o.h.e.t.j.p.i.JtaPlatformInitiator  - HHH000490: Using JtaPlatform implementation
INFO  j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory
INFO  o.s.s.web.DefaultSecurityFilterChain - Will secure any request with filters: [...]
INFO  com.example.loop.LoopApplication     - Started LoopApplication in X.XXX seconds
```

**Red flags to watch for at startup:**

| Log message | Cause | Fix |
|---|---|---|
| `Connection refused` on port 5432 | PostgreSQL is not running | Start your local PostgreSQL service |
| `password authentication failed for user "admin"` | Wrong DB credentials | Check `application.properties` username/password match what you created in psql |
| `database "loop_db" does not exist` | Database not created | Run the CREATE DATABASE command from Section 2 |
| `java.lang.IllegalArgumentException: The JWT secret key must be at least 32 characters` | Secret too short in properties | Make `app.jwt.secret` at least 32 characters |

---

## 8. Common Errors & Fixes

### `setUserDetailService` — method not found

```
The method setUserDetailService(...) is undefined for DaoAuthenticationProvider
```

**Fix:** The correct Spring method name is `setUserDetailsService` (with an `s`
at the end of `Details`).

---

### Static reference to `JwtUtil.generateToken`

```
Cannot make a static reference to the non-static method generateToken(UserDetails)
```

**Fix:** Use the injected instance `jwtUtil` (lowercase), not the class name
`JwtUtil` (uppercase). `generateToken` is an instance method.

---

### `buildError` method not found in `GlobalExceptionHandler`

```
The method buildError(HttpStatus, String, HttpServletRequest) is undefined
```

**Fix:** The private `buildError` helper method was not included when copying
the file. Add it before the class closing brace:

```java
private ErrorResponse buildError(HttpStatus status, String message,
                                 HttpServletRequest request) {
    return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .message(message)
            .path(request.getRequestURI())
            .build();
}
```

---

### `Cookie(String, int)` constructor not found

```
The constructor Cookie(String, int) is undefined
```

**Fix:** `Cookie` takes two `String` arguments — name and value. Use the
`buildCookie()` helper instead of calling the constructor directly:

```java
// ❌ Wrong
Cookie cookie = new Cookie(token, cookieMaxAge);

// ✅ Correct
Cookie cookie = buildCookie(token, cookieMaxAge);
```

---

## 9. What Phase 1 Does NOT Include

The following are intentionally out of scope for Phase 1 and will cause
compilation errors if attempted before their respective phases:

| Feature | Included in |
|---|---|
| Admin user seeding (`DataSeeder.java`) | Phase 2 |
| Domain entities (Market, Segment, Theme, Epic, Feature, UserStory) | Phase 2 |
| Sitemap endpoints (`GET /api/sitemap`) | Phase 3 |
| Write endpoints (`POST /api/themes` etc.) | Phase 4 |
| Admin user management (`POST /api/admin/users`) | Phase 5 |
| React frontend | Phase 6 |
| PRD import with Claude AI | Phase 9 |
| API key management | Phase 10 |
| Caching layer (Caffeine) | Phase 11 |

---

*LOOP DFS Platform — Phase 1 Documentation · May 2026*
