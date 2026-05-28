# LOOP Platform — Phase 1 Implementation & Testing Guide (H2)

> **Phase:** 1 — Backend Skeleton, Security & JWT Auth
> **Stack:** Java 17 · Spring Boot 3.3 · H2 In-Memory Database
> **Date:** May 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [What Changed from the PostgreSQL Plan](#2-what-changed-from-the-postgresql-plan)
3. [Prerequisites](#3-prerequisites)
4. [Sequence of Implementation](#4-sequence-of-implementation)
   - [Layer 1 — Foundation](#layer-1--foundation)
   - [Layer 2 — Data Model](#layer-2--data-model)
   - [Layer 3 — Shared Concerns](#layer-3--shared-concerns)
   - [Layer 4 — Security Machinery](#layer-4--security-machinery)
   - [Layer 5 — Auth Feature](#layer-5--auth-feature)
5. [How to Run](#5-how-to-run)
6. [What Can Be Tested at This Point](#6-what-can-be-tested-at-this-point)
7. [⚠️ Why Login Cannot Work Yet](#7-️-why-login-cannot-work-yet)
8. [Expected Startup Logs](#8-expected-startup-logs)
9. [Common Errors & Fixes](#9-common-errors--fixes)
10. [Transitioning to PostgreSQL](#10-transitioning-to-postgresql)
11. [What Phase 1 Does NOT Include](#11-what-phase-1-does-not-include)

---

## 1. Overview

Phase 1 establishes the complete security backbone of the LOOP backend using
H2 as the database. H2 is an in-memory database that is embedded directly
inside the running application — no installation, no setup, no running service.

By the end of this phase the application starts up, auto-creates the `users`
table in H2 via Hibernate, and enforces JWT authentication on all routes except
the public auth endpoints. The H2 browser console is also available at
`http://localhost:8080/h2-console` to inspect the database live.

> **Important:** H2 is in-memory. The database is wiped completely every time
> the application restarts. This is fine for Phase 1 — no seed data exists yet
> anyway. Phase 2 will introduce the `DataSeeder` which re-inserts all data on
> every startup, making restarts non-destructive.

---

## 2. What Changed from the PostgreSQL Plan

Only three files differ from the original Phase 1 plan. Every other file —
all entities, repositories, services, controllers, filters — is identical.

| File | What changed |
|---|---|
| `pom.xml` | H2 dependency active; PostgreSQL dependency commented out |
| `application.properties` | H2 datasource URL, H2 dialect, H2 console enabled; PostgreSQL config commented out; `ddl-auto` changed from `update` to `create-drop` |
| `SecurityConfig.java` | Added `/h2-console/**` to public routes; added `frameOptions.sameOrigin()` so the H2 console renders correctly in the browser |

---

## 3. Prerequisites

Because H2 is embedded, there is nothing to install or configure beyond the
Java toolchain.

### Java & Maven

```bash
java -version    # Must be Java 17 or higher
mvn -version     # Must be Maven 3.6 or higher
```

That is the entire prerequisite list. No database installation, no `psql`
commands, no running services.

---

## 4. Sequence of Implementation

Write the files in this exact order. Each layer only depends on what came
before it — every import you write will already exist by the time you need it.

---

### Layer 1 — Foundation

*What everything else is built on.*

| # | File | Why this first |
|---|---|---|
| 1 | `pom.xml` | Defines every library available to the project |
| 2 | `application.properties` | Defines config values every layer reads via `@Value` |
| 3 | `LoopApplication.java` | The `main()` entry point |

**Verify after this layer:**

```bash
./mvnw dependency:resolve
# Should complete with BUILD SUCCESS and no missing dependency errors
```

---

### Layer 2 — Data Model

*Defines what a User looks like in the database.*

| # | File | Why this order |
|---|---|---|
| 4 | `Role.java` | Plain enum — no dependencies |
| 5 | `User.java` | JPA entity; depends on `Role` |
| 6 | `UserRepository.java` | Spring Data interface; depends on `User` |
| 7 | `UserResponse.java` | Outbound DTO; depends on `Role` |

---

### Layer 3 — Shared Concerns

*Cross-cutting classes used everywhere.*

| # | File | Why this order |
|---|---|---|
| 8 | `ErrorResponse.java` | JSON shape for all errors — no dependencies |
| 9 | `ResourceNotFoundException.java` | Custom `RuntimeException` — no dependencies |
| 10 | `GlobalExceptionHandler.java` | Uses both above — written last in this layer |

**What `GlobalExceptionHandler` catches:**

| Exception | HTTP Status | Trigger |
|---|---|---|
| `ResourceNotFoundException` | 404 | Entity not found |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails on a request field |
| `BadCredentialsException` | 401 | Wrong email or password |
| `Exception` (catch-all) | 500 | Any unhandled runtime exception |

---

### Layer 4 — Security Machinery

*The JWT infrastructure — written in strict dependency order.*

| # | File | Why this order |
|---|---|---|
| 11 | `JwtUtil.java` | Pure utility — no Spring dependencies beyond `@Value` |
| 12 | `CustomUserDetailsService.java` | Bridges `User` → Spring Security; needs `UserRepository` |
| 13 | `JwtAuthFilter.java` | Reads cookie; needs `JwtUtil` + `CustomUserDetailsService` |
| 14 | `SecurityConfig.java` | Wires the full filter chain; needs all three above |

**H2-specific additions in `SecurityConfig`:**

```java
// Allow the H2 browser console — development only
.requestMatchers("/h2-console/**").permitAll()

// Let the H2 console render its iframe in the browser
.headers(headers -> headers
    .frameOptions(frame -> frame.sameOrigin()))
```

---

### Layer 5 — Auth Feature

*The actual HTTP endpoints — always written last.*

| # | File | Why this order |
|---|---|---|
| 15 | `LoginRequest.java` | Inbound DTO — no dependencies |
| 16 | `AuthResponse.java` | Outbound DTO — no dependencies |
| 17 | `AuthService.java` | Business logic; needs `JwtUtil`, `UserRepository`, both DTOs |
| 18 | `AuthController.java` | HTTP layer; thin wrapper around `AuthService` |

**Endpoints exposed at this phase:**

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | No | Validates credentials, sets `httpOnly` cookie |
| `POST` | `/api/auth/logout` | No | Clears cookie by setting `Max-Age=0` |
| `GET` | `/api/auth/me` | Yes (JWT cookie) | Returns `{ email, role }` from active session |

---

## 5. How to Run

### Step 1 — Build

```bash
cd loop-backend
./mvnw clean install -DskipTests
```

### Step 2 — Start

```bash
./mvnw spring-boot:run
```

### Step 3 — Confirm startup

Look for these two lines:

```
Started LoopApplication in X.XXX seconds
Tomcat started on port 8080
```

Hibernate will log the `CREATE TABLE` statement for the `users` table:

```sql
create table users (
    id bigint generated by default as identity,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    created_at timestamp(6) not null,
    primary key (id)
)
```

> **Note:** The H2 DDL for `id` looks different from PostgreSQL's `BIGSERIAL`.
> This is normal — both map to the same `@GeneratedValue(IDENTITY)` annotation.
> The behaviour is identical.

---

## 6. What Can Be Tested at This Point

### Test 1 — H2 Console — verify the users table exists

Open your browser and navigate to:

```
http://localhost:8080/h2-console
```

Enter these connection details:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:loop_db` |
| User Name | `sa` |
| Password | *(leave blank)* |

Click **Connect**, then run:

```sql
SHOW TABLES;
```

**Expected output:**

```
TABLE_NAME
----------
USERS
```

Then inspect the table structure:

```sql
SHOW COLUMNS FROM USERS;
```

**Expected output:**

```
FIELD          | TYPE          | NULL | KEY
id             | BIGINT        | NO   | PRI
email          | VARCHAR(255)  | NO   | UNI
password_hash  | VARCHAR(255)  | NO   |
role           | VARCHAR(20)   | NO   |
created_at     | TIMESTAMP     | NO   |
```

---

### Test 2 — Protected route returns 401 without a cookie

```bash
curl -v http://localhost:8080/api/auth/me
```

**Expected response:**

```
HTTP/1.1 401 Unauthorized
```

---

### Test 3 — Public routes return 200 without a cookie

```bash
curl -v http://localhost:8080/api/markets
curl -v http://localhost:8080/api/segments
```

**Expected response:**

```
HTTP/1.1 200 OK
[]
```

Empty arrays are returned because no data exists yet. The key thing is `200`
not `401` — confirming the public route exemptions in `SecurityConfig` work.

---

### Test 4 — Login with an empty body returns 400

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected response:**

```json
{
  "timestamp": "2026-05-28T10:00:00",
  "status": 400,
  "message": "Email is required, Password is required",
  "path": "/api/auth/login"
}
```

---

### Test 5 — Login with an invalid email format returns 400

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"notanemail","password":"somepassword"}'
```

**Expected response:**

```json
{
  "timestamp": "2026-05-28T10:00:00",
  "status": 400,
  "message": "Email must be a valid email address",
  "path": "/api/auth/login"
}
```

---

### Test 6 — A tampered JWT cookie returns 401

```bash
curl -v http://localhost:8080/api/auth/me \
  --cookie "loop_token=this.is.a.fake.token"
```

**Expected response:**

```
HTTP/1.1 401 Unauthorized
```

---

### Test 7 — Manually insert a user and test a real login

H2 allows you to insert a test user directly in the console to verify the
full login → cookie → `GET /me` flow without waiting for Phase 2.

**Step 1:** Open `http://localhost:8080/h2-console` and run:

```sql
INSERT INTO users (email, password_hash, role, created_at)
VALUES (
  'admin@loop.com',
  '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
  'ADMIN',
  CURRENT_TIMESTAMP
);
```

> The hash above is the BCrypt hash for the password `password`. It is a
> well-known test value — do not use it in production.

**Step 2:** Attempt login with curl:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@loop.com","password":"password"}' \
  -c cookies.txt -v
```

**Expected response:**

```
HTTP/1.1 200 OK
Set-Cookie: loop_token=<jwt>; Path=/; HttpOnly
```

```json
{
  "email": "admin@loop.com",
  "role": "ADMIN",
  "message": "Login successful"
}
```

**Step 3:** Use the saved cookie to call `GET /api/auth/me`:

```bash
curl http://localhost:8080/api/auth/me \
  -b cookies.txt -v
```

**Expected response:**

```json
{
  "email": "admin@loop.com",
  "role": "ADMIN",
  "message": null
}
```

**Step 4:** Test logout:

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -b cookies.txt -v
```

**Expected:** `HTTP/1.1 204 No Content` and a `Set-Cookie` header with
`Max-Age=0`, which instructs the browser to delete the cookie.

**Step 5:** Confirm the cookie is gone by calling `GET /me` again:

```bash
curl http://localhost:8080/api/auth/me \
  -b cookies.txt -v
```

**Expected:** `HTTP/1.1 401 Unauthorized`

---

## 7. ⚠️ Why Login Cannot Work Yet (Without the Manual Insert)

> **This is expected behaviour — not a bug.**

Without the manual H2 console insert from Test 7 above, attempting to log in
returns `401 Unauthorized` even with correct credentials:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@loop.com","password":"Loop@Admin1"}'
```

```json
{
  "timestamp": "2026-05-28T10:00:00",
  "status": 401,
  "message": "Invalid email or password",
  "path": "/api/auth/login"
}
```

**Why this happens:**

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
CustomUserDetailsService.loadUserByUsername("admin@loop.com")
        │
        ▼
UserRepository.findByEmail("admin@loop.com")
        │
        ▼
  ← returns empty (users table has zero rows)
        │
        ▼
throws UsernameNotFoundException
        │
        ▼
Spring converts to BadCredentialsException
        │
        ▼
GlobalExceptionHandler → 401 Unauthorized
```

The `users` table was auto-created by Hibernate but it is empty. The
`DataSeeder` — which inserts `admin@loop.com` with a real BCrypt hash on
startup — is built in **Phase 2**.

**What Phase 2 adds to unblock this permanently:**

- `DataSeeder.java` — an `ApplicationRunner` that runs on every startup and
  inserts the admin user using `BCryptPasswordEncoder` to generate the hash
  at runtime rather than hardcoding it.
- Idempotency check (`segmentRepository.count() == 0`) so the seeder only
  inserts once on a fresh database and is skipped on subsequent restarts.
- All domain entities (markets, segments, themes, epics, features, user stories).

Once Phase 2 is complete, login works on first startup with no manual steps.

---

## 8. Expected Startup Logs

A healthy startup will produce:

```
INFO  com.example.loop.LoopApplication        - Starting LoopApplication
INFO  o.s.b.w.embedded.tomcat.TomcatWebServer - Tomcat initialized with port 8080
INFO  com.zaxxer.hikari.HikariDataSource      - HikariPool-1 - Starting
INFO  com.zaxxer.hikari.HikariDataSource      - HikariPool-1 - Start completed
INFO  o.s.b.a.h2.H2ConsoleAutoConfiguration   - H2 console available at '/h2-console'
INFO  j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory
INFO  o.s.s.web.DefaultSecurityFilterChain    - Will secure any request with [...]
INFO  com.example.loop.LoopApplication        - Started LoopApplication in X.XXX seconds
```

The H2 console log line confirms H2 is active and accessible.

**Red flags to watch for:**

| Log message | Cause | Fix |
|---|---|---|
| `Failed to configure a DataSource` | H2 dependency missing from `pom.xml` | Add the H2 dependency block |
| `IllegalArgumentException: JWT secret must be at least 32 characters` | Secret too short in properties | Make `app.jwt.secret` at least 32 characters |
| `H2 console not available` | Console not enabled | Ensure `spring.h2.console.enabled=true` is in properties |
| H2 console renders broken / blank | `frameOptions` not set to `sameOrigin` | Add the `frameOptions` config to `SecurityConfig` |

---

## 9. Common Errors & Fixes

### H2 console shows a blank page or broken layout

Spring Security blocks `<frame>` and `<iframe>` tags by default as a
clickjacking prevention measure. The H2 console uses frames internally to
render its UI.

**Fix:** Ensure this is in `SecurityConfig.securityFilterChain()`:

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.sameOrigin()))
```

---

### H2 console returns 403 Forbidden

The `/h2-console/**` path is not in the public routes list.

**Fix:** Ensure this line is in the `authorizeHttpRequests` block:

```java
.requestMatchers("/h2-console/**").permitAll()
```

---

### `setUserDetailsService` — method not found

```
The method setUserDetailService(...) is undefined for DaoAuthenticationProvider
```

**Fix:** The correct method name is `setUserDetailsService` — with an `s` at
the end of `Details`.

---

### Static reference to `JwtUtil.generateToken`

```
Cannot make a static reference to the non-static method generateToken(UserDetails)
```

**Fix:** Use the injected instance `jwtUtil` (lowercase), not the class name
`JwtUtil` (uppercase).

---

### `Cookie(String, int)` constructor not found

```
The constructor Cookie(String, int) is undefined
```

**Fix:** `Cookie` takes two `String` arguments. Use the `buildCookie()`
helper in `AuthService` instead of calling the constructor directly:

```java
// ❌ Wrong
Cookie cookie = new Cookie(token, cookieMaxAge);

// ✅ Correct
Cookie cookie = buildCookie(token, cookieMaxAge);
```

---

## 10. Transitioning to PostgreSQL

When you are ready to switch from H2 to PostgreSQL, make these three changes:

### Step 1 — `pom.xml`

```xml
<!-- Comment out H2 -->
<!--
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
-->

<!-- Uncomment PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Step 2 — `application.properties`

Comment out the H2 block and uncomment the PostgreSQL block:

```properties
# Comment out H2
# spring.datasource.url=jdbc:h2:mem:loop_db...
# spring.h2.console.enabled=true
# spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
# spring.jpa.hibernate.ddl-auto=create-drop

# Uncomment PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/loop_db
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=admin
spring.datasource.password=secret
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
```

### Step 3 — `SecurityConfig.java`

Remove the H2 console lines (they are harmless to leave in, but clean code
practice is to remove dev-only config before production):

```java
// Remove these two lines
.requestMatchers("/h2-console/**").permitAll()
.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
```

### Step 4 — Create the local PostgreSQL database

```bash
psql -U postgres
CREATE DATABASE loop_db;
CREATE USER admin WITH PASSWORD 'secret';
GRANT ALL PRIVILEGES ON DATABASE loop_db TO admin;
\q
```

That is the complete transition — no Java code changes, no entity changes,
no repository changes.

---

## 11. What Phase 1 Does NOT Include

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

*LOOP DFS Platform — Phase 1 Documentation (H2) · May 2026*
