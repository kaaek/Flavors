# Flavors

Flavors connects F&B brands (restaurants, cafés) with verified food creators.
It covers each campaign from the escrow payment to the QR-code redemption in
the restaurant, so brands see their real ROI.

EECE 430 course project, Fall 2026–27. Three two-week sprints:

| Sprint | Dates | Goal |
|---|---|---|
| 1 (v0) | 22 Oct – 5 Nov | Foundation: accounts, verified portfolios, creator search |
| 2 (v1) | 5 – 19 Nov | Booking to payout: campaign, escrow, delivery proof, release |
| 3 (v2) | 19 Nov – 3 Dec | Redeem offers, track ROI |

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4, Maven |
| Database | PostgreSQL 17, schema managed by Flyway |
| Frontend | React + TypeScript + Vite |
| Deployment | One Docker image (Spring Boot serves the built frontend) on Railway |
| CI/CD | GitHub Actions |

## Prerequisites

- **JDK 25** (e.g. Eclipse Temurin 25). Check: `java -version`
- **Node.js 22 LTS** and npm. Check: `node -v`
- **Docker** with Docker Compose, for the local database and the Testcontainers tests. Check: `docker compose version`
- **Git**
- **VS Code** is recommended. When you open the repo it suggests the extensions listed in `.vscode/extensions.json`; accept them.

## Repository layout

Paths are relative to the repository root. Entries marked *(planned)* arrive with EEC-27 (project foundation).

```
backend/      Spring Boot application (package com.flavors), Flyway migrations
frontend/     React + TypeScript single-page app (Vite)
docs/         This README and Architecture.md (design: schema, security, audit log, CI/CD)
.github/      Pull request template; CI/CD workflows (ci.yml, deploy-production.yml)
Dockerfile    Builds the frontend and backend into one image
docker-compose.yml  Local Postgres for development
railway.json  Railway build and health-check settings
```

See [Architecture.md](Architecture.md) for how the system is built and why.

## Running locally

Start the database, then the backend, then the frontend (three terminals, from the repository root):

```bash
docker compose up -d db                                   # Postgres on localhost:5432
cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run     # API on http://localhost:8080
cd frontend && npm install && npm run dev                 # app on http://localhost:5173
```

To create the admin account on startup, also set `FLAVORS_SEED_ADMIN_ENABLED=true`, `FLAVORS_ADMIN_EMAIL` and
`FLAVORS_ADMIN_PASSWORD` for the backend (there is no login screen until UC-F19.3, so this only creates the row).

To try the production image: `docker build -t flavors .`, then run it with the `PG*` variables pointing at a database.

### Frontend

```bash
cd frontend
npm install          # first time only
npm run dev          # http://localhost:5173, proxies /api to the backend on :8080
npm test             # Vitest
npm run lint         # oxlint
npm run format:check # Prettier (npm run format fixes)
npm run build        # type-check + production build into frontend/dist
```

### Backend tests

```bash
cd backend      # from the repository root
./mvnw verify    # unit tests + integration tests (*IT) against a throwaway Postgres
```

The tests start Postgres in Docker through Testcontainers, so Docker must be running and
your user must be able to use it. On Linux: `sudo usermod -aG docker $USER`, then log out
and back in. The first run downloads the `postgres:17-alpine` image.

### Run the backend without a local database

`backend/src/test/java/com/flavors/TestFlavorsApplication.java` starts the app against a
Testcontainers Postgres. Run its `main` from VS Code, or use `./mvnw spring-boot:test-run`.

## Branch conventions

```
main ◄── release/sprint-N ◄── develop ◄── EEC-NN-short-name
```

| Branch | Purpose | Created from | Merged into | Deploys to |
|---|---|---|---|---|
| `main` | What runs in production | (initial commit) | | **Production**, after a reviewer approves the deployment |
| `release/sprint-N` | The sprint's release candidate, frozen at sprint end | `develop` | `main` via PR | |
| `develop` | Integration branch; all finished work lands here | `main` | `release/sprint-N` via PR | **Staging**, automatically on every merge |
| `EEC-NN-short-name` | One feature/story | `develop` | `develop` via PR | |

Rules:

- **Name feature branches after the Jira issue:** `EEC-NN-short-name`, lowercase words joined by hyphens.
  Example: `EEC-27-foundation`. Jira links the branch to the issue automatically.
- **Never push directly to `main` or `develop`.** Every change goes through a pull request
  that needs **1 approval** and **passing CI**.
- **PR titles:** `EEC-NN: short summary`, e.g. `EEC-27: Project foundation`.
- **Commit messages:** start with the key, e.g. `EEC-27: Add audit log triggers`. Keep commits small,
  with one logical step each, so reviewers can read them one at a time.
- **Keep your branch current:** before opening a PR, merge (or rebase onto) the latest `develop`.
- **Sprint end:** create `release/sprint-N` from `develop`, open a PR `release/sprint-N` → `main`,
  and merge it after the demo check. Fixes found on the release branch go back into `develop` too.

Typical flow:

```bash
git switch develop && git pull
git switch -c EEC-31-creator-registration
# ...work, commit...
git push -u origin EEC-31-creator-registration
# open a PR into develop on GitHub
```

## Contributing

Read [Architecture.md](Architecture.md) first: it settles the schema, security and conventions. This
section covers what you need day to day on top of it.

### Not built yet

The foundation (EEC-27) is partly merged. Until the rest lands:

| Missing | What it means for you | Arrives with |
|---|---|---|
| Login endpoint | Nobody can log in to a running app; tests use a mock user (see below) | UC-F19.3 |

### Database changes

- The whole MVP schema already exists (V1–V5). Find your tables in
  [Architecture.md §4.4](Architecture.md#44-which-story-uses-which-table) and check the comments in the
  migration files: they say which rules the database enforces and which ones your code must enforce.
- **Never edit a migration that is merged.** Add a new file `V<n>__<what>.sql`.
- **Claim your version number in the team chat before you create the file.** Two branches that both add V6
  make Flyway refuse to start for whoever merges second. If someone merged your number first, rename yours
  to the next free one before merging.
- Follow the conventions in [Architecture.md §4.1](Architecture.md#41-conventions) (cents as `bigint`,
  `timestamptz`, `varchar` + `CHECK` for statuses, indexed foreign keys, no `ON DELETE CASCADE`).

### JPA entities

- Write entities only for the tables your story uses, in your feature's package (e.g. `com.flavors.campaign`).
  [`AppUser`](../backend/src/main/java/com/flavors/account/AppUser.java) is the example to copy.
- Hibernate checks every entity against the schema at startup (`ddl-auto: validate`), and every integration
  test starts the app. **One entity that doesn't match its table breaks every test**, so run
  `./mvnw verify` before pushing.
- Type mapping: `timestamptz` → `Instant`, `date` → `LocalDate`, `*_cents` → `long`, status columns →
  an enum with `@Enumerated(EnumType.STRING)`, `jsonb` → `@JdbcTypeCode(SqlTypes.JSON)`.

### Endpoints and security

- Put each endpoint under the area of the role that may call it: `/api/creator/`, `/api/brand/`,
  `/api/admin/`, or `/api/public/` for diner pages. `SecurityConfig` then returns 401/403 for you; don't add
  role checks by hand.
- Get the logged-in user with `@AuthenticationPrincipal CurrentUser user` (`user.getId()`, `user.getRole()`).
- The role check doesn't know who owns what. "This campaign belongs to the current brand" (NF-1) is checked
  in your service, using `user.getId()`.
- Errors go back as JSON `ProblemDetail` (see [Architecture.md §5](Architecture.md#5-security--built)).
- Frontend: every POST/PUT/PATCH/DELETE must send the value of the `XSRF-TOKEN` cookie in the
  `X-XSRF-TOKEN` header, or it gets a 403. The API client in step 9 will do this for you.

### Tests

- **Controller tests** (`*Test`, fast, no database): `@WebMvcTest(YourController.class)` +
  `@Import(SecurityConfig.class)`, logging in with
  `.with(user(new CurrentUser(id, email, Role.BRAND, "x", true)))`. See
  [`MeControllerTest`](../backend/src/test/java/com/flavors/account/MeControllerTest.java).
- **POST/PUT/DELETE in tests** need the CSRF cookie and header, as in
  [`RoleGuardTest`](../backend/src/test/java/com/flavors/common/security/RoleGuardTest.java)
  (`.cookie(new Cookie("XSRF-TOKEN", t)).header("X-XSRF-TOKEN", t)`). Spring's `csrf().asHeader()` helper
  does **not** work with this setup.
- **Database tests** (`*IT`): `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. All tests share
  one database and nothing is rolled back, so give each test its own data (e.g. random emails) and never rely
  on a table being empty. See the tests in `backend/src/test/java/com/flavors/db/`.

### Open decisions

- **SPIKE-1** (which social platform) and **SPIKE-2** (which payment provider) are still open. The `platform`
  and `provider` columns accept any text until then; don't hard-code one value.

## Deployment setup (one-time, by the repository admin)

The workflows in `.github/workflows/` deploy to Railway, but they need the following to exist first.
Nothing here is in git because it is account configuration and secrets.

**Railway** (https://railway.com)

1. Create a project `flavors` with two environments, `staging` and `production`.
2. In each environment add a **PostgreSQL** service and an **app** service. The app service builds from this
   GitHub repository using `railway.json` (Dockerfile build, health check `/actuator/health`). Name it `app`, or set
   the GitHub variable `RAILWAY_SERVICE` to its name.
3. On each app service set the variables below. Reference the database service's variables rather than copying
   values, so they follow the database:

   | Variable | Value |
   |---|---|
   | `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | `${{Postgres.PGHOST}}` etc. (Railway reference variables) |
   | `FLAVORS_SEED_ADMIN_ENABLED` | `true` (can be switched off after the first start) |
   | `FLAVORS_ADMIN_EMAIL`, `FLAVORS_ADMIN_PASSWORD` | the first admin's login; use a strong password |
   | `SPRING_PROFILES_ACTIVE` | `staging` / `prod` (optional: those profiles have no overrides yet) |

4. Generate a public domain for each app service (Settings, Networking).
5. In each environment create a **project token** (Project settings, Tokens).

**GitHub** (repository Settings)

| Where | Name | Value |
|---|---|---|
| Secrets | `RAILWAY_TOKEN_STAGING` | the staging project token |
| Secrets | `RAILWAY_TOKEN_PRODUCTION` | the production project token |
| Variables | `STAGING_URL` | e.g. `https://flavors-staging.up.railway.app` (no trailing slash) |
| Variables | `PRODUCTION_URL` | the production domain |
| Variables | `RAILWAY_SERVICE` | only if the app service is not named `app` |
| Environments | `production` | add **Required reviewers**, so a production deploy waits for approval |

After CI has run once, add its checks (`backend`, `frontend`, `docker`) as **required status checks** in the branch
protection rules of `main` and `develop`.

## Environment variables

Nothing secret is committed. Locally, the `dev` profile has defaults that match `docker-compose.yml`.
On Railway, the variables are set per environment (staging, production).

| Variable | Used for | Example / default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Which config profile to load: `dev`, `staging` or `prod` | `dev` |
| `PORT` | HTTP port the app listens on (Railway sets it) | `8080` |
| `PGHOST` | Postgres host | `localhost` |
| `PGPORT` | Postgres port | `5432` |
| `PGDATABASE` | Database name | `flavors` |
| `PGUSER` | Database user | `flavors` |
| `PGPASSWORD` | Database password | (local default from `docker-compose.yml`) |
| `FLAVORS_SEED_ADMIN_ENABLED` | Create the admin account on startup if it doesn't exist | `false` |
| `FLAVORS_ADMIN_EMAIL` | Email of the seeded admin (required when seeding is enabled) | `admin@flavors.local` |
| `FLAVORS_ADMIN_PASSWORD` | Password of the seeded admin (required when seeding is enabled) | (never commit) |

The `PG*` names match what Railway's Postgres service exposes, so staging and production need no renaming.
