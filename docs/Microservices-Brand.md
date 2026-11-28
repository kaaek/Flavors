# Brand service (EEC-95, Phase 1b)

`services/brand` is the first domain service cut out of the monolith. It is a pure extraction: the code and tests were
moved from `backend/`, and no behaviour changed. This page records what moved, what did not, and every temporary
dependency on the monolith, so the data-ownership phase knows what to replace.

## What the service owns

| | |
|---|---|
| Tables | `brand`, `branch`, `saved_creator` |
| Endpoints | `/api/brand/profile`, `/api/brand/branches/**`, `/api/brand/saved-creators/**`, `/api/creator/brands/search` |
| Package names | unchanged (`com.flavors.controller`, `service`, `entity`, ...) |
| Image | `flavors-brand` (`services/brand/Dockerfile`, built from the repository root) |

The gateway (`deploy/gateway/nginx.conf`), the compose stack and the Kubernetes base route those prefixes to it. The
monolith no longer has a copy of the controllers, services, repositories, DTOs, mappers or tests for them, so exactly
one copy is live.

## Phase 1 database rule

The service connects to the **same Postgres database as the monolith** (`PGHOST`, `PGDATABASE`, ... point at it).
Its Flyway is **off** (`spring.flyway.enabled=false`); the schema belongs to `backend/src/main/resources/db/migration`.
A schema change the service needs is a new versioned migration there, never an edit of a merged one. Hibernate runs
with `ddl-auto: validate`, so a mismatch between an entity and the schema fails startup.

Tests build the same schema by pointing Flyway at the monolith's migration folder
(`services/brand/src/test/resources/application.properties`), so they need a full repository checkout. CI runs them
whenever a monolith migration changes (`.github/workflows/libs.yml`).

`compose` and Kubernetes give each service its own empty database (`brand-db`). The brand service does **not** use it in
Phase 1; its manifests point at the monolith's database instead (`monolith-db`).

## Temporary cross-reads (replace in the data-ownership phase)

Each row is a place where one service reads a table that another service owns. All are read-only unless the note says
otherwise.

| # | Reader | Reads | Why | Replaced by |
|---|---|---|---|---|
| 1 | brand service, `BrandSearchRepository` | `app_user.status` (identity) | the brand search hides suspended brands (`u.status = 'ACTIVE'`) | a `user_status` replica fed by identity's `UserStatusChanged` event (plan §1.1) |
| 2 | brand service, `JdbcCreatorDirectory` (behind the `CreatorDirectory` interface) | `creator_profile.verification_status` (creator) | saving a creator needs "exists and approved" | an HTTP call to the creator service; only the `CreatorDirectory` implementation changes |
| 3 | brand service, audit | writes the monolith's `audit_log` | the platform `AuditLogger` writes `audit_log`; the table is shared in Phase 1 | the service's own `audit_log` (platform template) |
| 4 | monolith, `CampaignServiceImpl`, `OfferServiceImpl`, `StorefrontService`, `CampaignMapper` | `brand`, `branch` through the **read-only copies** `Brand`, `Branch`, `BrandRepository`, `BranchRepository`, `BranchResponse` that stay in `backend/` | campaigns validate and display brand and branch data | API calls to the brand service, or event-fed replicas |
| 5 | monolith, `CreatorSearchRepository` | `saved_creator` (native SQL join) | the "saved" flag on creator search results | a call to the brand service that returns the brand's saved creator ids (plan §1.1) |
| 6 | monolith, `BrandRegistrationServiceImpl` | **writes** `app_user`, `brand`, `branch` | registration has not moved (see next section) | identity's `POST /internal/users` plus the brand service's own registration |

Nothing in the monolith writes `brand`, `branch` or `saved_creator` apart from row 6.

## Registration stays in the monolith (split out of this PR)

`POST /api/public/auth/register/brand` is **not** served by the brand service yet. The plan (§1.3) makes brand
registration a three-step flow: call identity's `POST /internal/users` (status `PENDING_PROFILE`), create the brand and
its branches locally, then call `activate` (or `delete-pending` on failure). That endpoint belongs to the identity
service, which another worker is building in parallel and which has not merged. The monolith's current registration
creates the user and the brand rows in one database transaction using code (`AppUserRepository`, the password encoder,
the email-uniqueness rules) that the brand service must not own and cannot reach without identity.

So registration stays in the monolith, unchanged: `BrandRegistrationController`, `BrandRegistrationService(Impl)`,
`BrandRegistrationRequest`, `BrandRegistrationResponse`, their tests, and the `Brand`/`Branch` entities and
repositories it needs. The gateway keeps `/api/public/auth/register/brand` on the default upstream (the monolith), and so
does the Kubernetes Ingress.

**When identity merges**, move registration into the brand service in a small follow-up:

1. Add a `UserDirectoryClient` interface to the service (`create(email, password, role)`, `activate(id)`,
   `deletePending(id)`), with an HTTP implementation for identity's `/internal/users`.
2. Move `BrandRegistrationController/Service/Request/Response` and their tests, create the brand and branch rows in
   a local transaction between `create` and `activate`, and call `deletePending` if anything fails.
3. Point the two route entries (`nginx.conf`, `ingress.yaml`) back at brand, and delete the registration code and the
   write access from the monolith (cross-read 6 disappears).

## Authentication

The service uses the JWT resource-server setup from `libs/platform` (same path rules as the monolith's
`SecurityConfig`: `/api/creator/**` needs `CREATOR`, `/api/brand/**` needs `BRAND`, CSRF double-submit for cookie
requests). It validates access tokens against `FLAVORS_JWKS_URI` (identity's JWKS).

**Merge-order dependency.** The monolith still logs users in with a session cookie, and the brand service only
understands identity's tokens. Until identity merges and the gateway sends login to it, a real browser session
that reaches the brand routes through the gateway gets `401`. Production (Railway) is unaffected, because it still
deploys the monolith alone; the gateway, compose and Kubernetes stacks are the target platform. Merge this PR
together with or after identity if the compose or Minikube demo must stay usable with real logins.

Tests sign their own tokens with a throwaway key served by a JWKS server inside the test JVM
(`BrandServiceIT`, plus `TestUsers` for the controller slice tests), so this PR does not depend on identity's PR.

## Run it on docker compose

```bash
docker compose -f deploy/compose/docker-compose.yml up -d --build        # brand is part of the default stack now
```

Until identity exists nothing serves `http://identity:8080/.well-known/jwks.json`. To try the brand endpoints
by hand, serve any JWKS under a name the stack can reach and point the service at it:

```bash
BRAND_JWKS_URI=http://<jwks-host>/.well-known/jwks.json docker compose -f deploy/compose/docker-compose.yml up -d --build
```

then call `http://localhost:8088/api/brand/profile` with `Authorization: Bearer <token>`, where the token is an RS256
JWT signed by that key with the claims `sub` (a brand's `app_user.id`) and `role` (`BRAND`).

## Behaviour that could not be preserved

- **Sessions.** Brand endpoints now need an access token, not the monolith's session cookie (see Authentication).
- **`CurrentUser`.** Controllers receive the platform's `CurrentUser` (built from the token's claims) instead of the
  monolith's session object. Its `getId()` is the same, so the code is unchanged.
- **Audit actor.** The audit row's `actor_label` is the token's `email` claim when identity includes one, otherwise
  `user:<id>` (the monolith used the session's email). `actor_user_id` is unchanged.
- Everything else (status codes, error bodies, validation messages, SQL) is the monolith's code, moved unchanged.
  `BrandSearchCriteria` carries its own `DEFAULT_SIZE = 20` instead of reading the creator search's constant.
