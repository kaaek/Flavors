# Services

One directory per domain service (identity, creator, brand, campaign, proof, payment, offer, notification;
see EEC-95). Each is its **own Maven project**, with its **own Dockerfile**, **own database** and **own Flyway
history**. `_template` is the starting point; it is a working service, not a stub.

```
libs/platform    shared Spring Boot library: JWT, CSRF, errors, audit, idempotency, outbox/inbox, Money
libs/contracts   versioned event and internal-API records (no framework dependency)
services/_template
```

## Build and test locally

The libraries are not published anywhere yet, so install them into your local Maven repository first, then
build the service. Docker must be running (the integration tests start Postgres and RabbitMQ).

```bash
./libs/mvnw -f libs/pom.xml install            # builds and tests libs/contracts and libs/platform
cd services/_template && ./mvnw verify          # unit tests + Testcontainers integration tests (*IT)
docker build -f services/_template/Dockerfile -t flavors-template .   # from the repository root
```

CI does the same in `.github/workflows/libs.yml`.

## Create a service from the template

1. Copy the directory: `cp -r services/_template services/<name>` (for example `services/brand`).
   Delete `target/` if it came along.
2. In `services/<name>/pom.xml` change `artifactId` and `<name>`.
3. Rename the Java package `com.flavors.template` to `com.flavors.<name>` and `TemplateApplication` to
   `<Name>Application`. Delete `TemplateController`, `TemplatePingService`, `TemplatePingConsumer` and their
   tests' sample calls once you have written your own endpoints (keep the test base class and the ideas:
   token signing, CSRF, audit, outbox/inbox).
4. In `application.yml` change `spring.application.name`. Keep the `PG*`, `RABBITMQ_*` and `FLAVORS_JWKS_URI`
   variables: the Kubernetes manifests and the compose stack provide them under those names.
5. Keep `V1__platform_foundation.sql` as it is (audit log, idempotency keys, outbox, inbox: copies of
   `libs/platform/src/main/resources/db/templates/*.sql`; a test fails if they drift). Your tables go in
   `V2__...sql` and later. Never edit a migration once it is merged. Foreign keys to another service's
   tables are not allowed: store the id and check it through that service (EEC-95 §1.2).
6. In `Dockerfile` change the default of `ARG SERVICE` to your directory name.
7. Add the directory name to the `matrix.service` lists in `.github/workflows/libs.yml`.

## What the platform gives you (no code needed)

| Concern | How you use it |
|---|---|
| **Login / roles** | Requests carry the access token (`Authorization: Bearer` header, or the HttpOnly cookie `flavors_access`). The platform validates it against identity's JWKS (`FLAVORS_JWKS_URI`, optional `FLAVORS_JWT_ISSUER`). Claims: `sub` (user id), `role` (`CREATOR`/`BRAND`/`ADMIN`), `mfa`. Path rules are the monolith's: `/api/public/**` open, `/api/creator/**`, `/api/brand/**`, `/api/admin/**` need `ROLE_ADMIN_MFA` (an admin token only gets it when `mfa` is set), other `/api/**` any signed-in user except a pending admin. Take `@AuthenticationPrincipal CurrentUser user`. |
| **CSRF** | Same as the monolith's `csrf.spa()`: the SPA reads the `XSRF-TOKEN` cookie and sends it back in `X-XSRF-TOKEN`. Requests that send a Bearer header (service-to-service) skip the check; cookie requests never do. |
| **Errors** | `ProblemDetail` for 400/401/403/409. Throw `ProblemException(status, detail)` or add your own `@RestControllerAdvice`. |
| **Audit (NF-7)** | Inject `AuditLogger` and call `record(...)` **inside your `@Transactional` method**; it fails outside a transaction on purpose. |
| **Idempotency (NF-6)** | `IdempotencyService.execute(scope, key, fingerprint, action, Type.class)`. The action must be database-only. |
| **Events out** | `OutboxWriter.write(type, version, aggregateType, aggregateId, stateVersion, payload)` inside the same transaction as the change. The relay publishes to the `flavors.events` topic exchange (routing key = type) and needs `spring.rabbitmq.publisher-confirm-type=correlated` (already in `application.yml`). |
| **Events in** | Declare a queue and binding (see `TemplatePingConsumer`), decode with `EventCodec`, and wrap the work in `InboxService.runOnce(consumer, messageId, type, work)`. |
| **Correlation** | `X-Correlation-Id` (or `X-Request-Id`) is read or created per request, logged in the MDC as `correlationId`, copied into outbox events. |
| **Money / clock** | `com.flavors.platform.money.Money` (cents, no floats); inject `java.time.Clock` instead of `Instant.now()`. |
| **Health** | `/actuator/health/liveness` and `/actuator/health/readiness` (readiness needs the database). |

## Rules

- A change to an event or internal-API contract goes through `libs/contracts` with captain review. Never edit
  a published version; add `v2` next to `v1`.
- Nothing here contains domain logic. Payment and escrow event shapes come from the saga design document.
- `backend/` and `frontend/` are not touched by Phase 0.
