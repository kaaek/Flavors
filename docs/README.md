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
frontend/     React + TypeScript single-page app (planned)
docs/         This README and Architecture.md (design: schema, security, audit log, CI/CD)
.github/      Pull request template; CI/CD workflows (planned)
Dockerfile    Builds the frontend and backend into one image (planned)
```

See [Architecture.md](Architecture.md) for how the system is built and why.

## Running locally

*Full setup (local database, frontend) is coming in EEC-27 (project foundation).*

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
