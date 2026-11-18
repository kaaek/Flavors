# EEC-45: escrow release and creator payout design

Status: Step 7 escrow read API and frontend integration implemented, 17 November 2026. V10 persistence, approval-triggered
authorization, transactional audit, explicit recovery of legacy approvals, and scheduled deadline
authorization, atomic rejection freezing, and simulated payout confirmation are implemented. Real payouts
remain pending. Escrow read responses and both campaign pages now expose truthful release progress.
FR-F7.12 remains pending external payout capability
and confirmation of the two-business-day SLA; no simulation transfers money to real creators.
EEC-45 is Sprint 2; EEC-54 is Sprint 3.

## Agreed boundaries

- Whish is the intended provider. Flavors has applied for a virtual account; approval is pending.
  Creator transfers, refunds, payout APIs, status queries, automated settlement, and payout timing
  have not been confirmed. Account approval alone does not establish any of these capabilities.
- Preserve `RELEASED`: the creator payout has been confirmed. Authorizing release or receiving
  a transfer request acknowledgement must not set this status.
- Development and tests will use an explicitly simulated payout provider. Production payouts
  remain disabled until the account, capabilities, credentials, and confirmation contract are verified.
- Collection (`PaymentProvider`) and creator payout (`PayoutProvider`, proposed) are different ports.
  `WHISH_MODE` selects collection only and must not implicitly enable payouts.
- EEC-45 owns release authorization, payout processing, fee/net calculation, and rejection freezing.
  EEC-54 owns dispute creation, evidence, decisions, and resolution in Sprint 3. The existing persisted
  rejection review is its durable input; no dispute-opening service or handoff table is added.
- No existing migration is edited. No full dispute implementation or refund workflow is added here.

## Existing integration points

`ProofReviewServiceImpl.review` already locks the campaign, saves one review per submission, and
audits it. Approval authorizes release while leaving escrow held; rejection freezes escrow in the same
transaction. Its identical retry branch returns the existing decision and repairs missing authorization
or freezing for legacy decisions without duplicating completed effects.

`ProofReviewService.findOverdueCampaignIds` / `CampaignRepository.findOverdueProofCampaignIds`
identify at most 100 unanswered current submissions past Timer C. They are candidate reads, not an
authorization to transfer money. A worker must lock and recheck each candidate independently.

`PaymentServiceImpl` provides the transaction/network/transaction pattern, `EscrowPayment` maps
fee/net/released timestamps, and V5 already has `RELEASE` payment transactions. `Money.percent(15)`
rounds HALF_UP in integer cents; compute net by subtraction, never by rounding 85% independently.
`AuditLogger` joins the business transaction. `IdempotencyService` protects database actions, not
external network effects. Existing Testcontainers tests exercise real PostgreSQL and shared locks.

## Release and payout state model

One ordinary full release operation exists per escrow, regardless of whether approval or Timer C
authorized it. Amounts and eligibility evidence are snapshotted once and never changed by a retry.

| Event | Escrow status | Payout operation status | Campaign |
|---|---|---|---|
| Confirmed collection | `HELD` | No operation | `ACTIVE`, then proof workflow |
| Valid approval or unanswered expired Timer C | `HELD` | `AUTHORIZED` | `PROOF_SUBMITTED`; review is finished |
| Transfer accepted but not confirmed paid | `HELD` | `SUBMITTED` | Unchanged |
| Submission/query outcome uncertain | `HELD` | `UNKNOWN` | Unchanged |
| Provider definitively refuses/fails transfer | `HELD` | `FAILED` | Unchanged; operational attention/retry policy |
| Authoritative confirmation of creator payment | `RELEASED` | `CONFIRMED` | `COMPLETED` |
| Valid brand rejection, before release authorization | `FROZEN` | No release operation | Remains `PROOF_SUBMITTED` in Sprint 2; EEC-54 owns dispute creation/state transition |

`AUTHORIZED` remains durable when production payouts are disabled. It does not mean that a transfer
has been sent. `FAILED` does not unfreeze or refund anything. `UNKNOWN` never permits a blind new
transfer. A correction request leaves escrow `HELD` and opens the next proof round without a release.

`authorized_at` starts the payout-service target; retries must not reset it. `submitted_at` records
the first accepted transfer. `confirmed_at` and `escrow_payment.released_at` are set only when payment
is confirmed. The precise business-day calendar, holidays, cutoff, and due-date computation remain
external decisions. Do not display a guaranteed two-business-day arrival date before they are agreed.

At authorization, store original amount, fee, and creator net in the operation. At confirmation, copy
the final fee/net into the existing escrow fields and mark the release transaction successful in the
same transaction. For a full release, `fee + net = original amount`, and refunded amount remains zero.
For $200.00 the fee is $30.00 and creator net is $170.00. Existing V5 balance constraints remain intact.

## Database persistence implemented in Step 2

`V10__escrow_release_operations.sql` adds durable payout state only. V1 through V9 were present when
the version was selected; none is modified. Recheck version availability before merging if another
branch adds V10. No dispute-related database object is introduced.

### `escrow_release_operation`

| Columns | Type and constraint |
|---|---|
| `id` | `bigint generated always as identity`, primary key |
| `escrow_id` | `bigint NOT NULL UNIQUE`, FK to `escrow_payment` |
| `submission_id` | `bigint NOT NULL`, FK to `proof_submission`, indexed |
| `review_id` | Nullable `bigint`, FK to `proof_review`, indexed; set for approval, null for Timer C |
| `authorization_source` | `varchar(20) NOT NULL`, CHECK `APPROVAL` or `TIMER_C` |
| `original_amount_cents`, `fee_cents`, `creator_net_cents` | `bigint NOT NULL`; original > 0, fee/net >= 0, CHECK fee + net = original |
| `currency` | `varchar(3) NOT NULL`, CHECK `USD` |
| `status` | `varchar(20) NOT NULL`, CHECK `AUTHORIZED`, `SUBMITTED`, `UNKNOWN`, `FAILED`, `CONFIRMED` |
| `operation_key` | `varchar(100) NOT NULL UNIQUE`; stable logical payout identity, e.g. `escrow-release:<escrowId>` |
| `payout_provider` | Nullable `varchar(30)`; independent of the collection provider, pinned before first dispatch |
| `provider_ref` | Nullable `varchar(100)`; UNIQUE pair `(payout_provider, provider_ref)` |
| `destination_ref` | Nullable `varchar(200)` opaque provider beneficiary identifier; required before dispatch, immutable thereafter |
| `payment_transaction_id` | Nullable `bigint UNIQUE`, FK to `payment_transaction`; indexed by uniqueness |
| `authorized_at`, `created_at`, `updated_at` | `timestamptz NOT NULL` |
| `submitted_at`, `confirmed_at`, `last_attempt_at`, `next_attempt_at` | Nullable `timestamptz` |
| `dispatch_attempts`, `query_attempts` | `integer NOT NULL DEFAULT 0`, CHECK >= 0 |
| `retryable` | `boolean NOT NULL DEFAULT false`; applies to definitive failure only |
| `last_error_code` | Nullable `varchar(100)`; sanitized code, no provider payload, credentials, or personal data |
| `lease_token`, `lease_until` | Nullable `uuid` and `timestamptz`; both present or both null |

Additional checks: approval source requires review ID; Timer C source has no review ID;
`status = CONFIRMED` iff `confirmed_at IS NOT NULL`; submitted/confirmed states require the pinned
provider and destination, with `submitted_at` set. Index `(status, next_attempt_at, id)` for worker
selection. Foreign keys have no cascading business-data deletion. Cross-table ownership, current
proof/review eligibility, and amount agreement are checked under the campaign lock.

The operation snapshots money; EEC-45 does not yet introduce a creator beneficiary onboarding table.
How a real `destination_ref` is obtained, verified, protected, and changed before dispatch is blocked
on the provider agreement. Simulation uses synthetic identifiers, never real payout destinations.

One linked `payment_transaction` row of type `RELEASE` represents the logical net transfer, initially
`PENDING`. Its amount is creator net, not the gross amount. The operation stores the fee separately.
Provider failures map this row to `FAILED`; reconciliation of a late success can confirm the same row.
Retries reuse the logical operation/reference and do not create another charge or successful release.
Provider acceptance is not `SUCCEEDED`; only confirmed creator payment is. Do not reuse a collection
reference as a payout reference. The one-operation constraint is for this ticket's full release only;
EEC-54 must coordinate future split/refund operation identities rather than bypass it.

The migration also enforces exact HALF_UP 15% fee rounding using PostgreSQL numeric arithmetic,
valid state transitions, immutable authorization/money snapshots, pinned provider/destination/transaction
and provider references, nondecreasing attempt counters, coherent lease/timestamp fields, and terminal
confirmation. A new operation starts `AUTHORIZED`; dispatch preparation persists `UNKNOWN` before
network I/O, then provider acceptance is `SUBMITTED` and actual payment confirmation is `CONFIRMED`.
The entity uses the existing `Money` utility for the snapshot. The repository documents campaign ->
operation locking. No operation method updates escrow, invokes a provider, or records a review.
Later service transactions must update escrow and audit atomically with confirmed payout; Step 2
does not automatically change escrow even when a test persists a confirmed operation.

## Services, transaction boundaries, and processing

Step 3 implements `EscrowReleaseService` / `EscrowReleaseServiceImpl` with
`authorizeApproved(campaignId, submissionId, reviewId)` (true when created, false on an identical replay)
and `recoverApprovedReviews()` (count created). These are internal service methods, with no new API.
Approval and its release operation/audit join one transaction. Authorization rechecks the owning brand,
latest submitted round, absence of rejection, campaign state, and held agreed escrow amount under the
campaign lock. Existing identical operations are returned without recalculating amounts or resetting
timestamps, including retries after later payout processing. The Step 2 entity uses `Money` for the
immutable fee/net snapshot; escrow remains `HELD`.

The campaign lock and unique escrow/operation key provide idempotency. This path does not nest the
generic idempotency service under that lock, which would invert its existing lock order. Recovery uses
keyset batches of 100 eligible legacy approvals, then rechecks each in a separate transaction. A failed
candidate remains discoverable on the next invocation; other candidates can continue. Recovery is an
explicit service operation, not a scheduled task or startup hook in Step 3.

Step 4 adds `authorizeOverdue(campaignId)` to the same service. It starts an independent transaction,
locks the campaign, reads the time after locking, and rechecks campaign state, current/latest submitted
round, deadline, absence of a current review or any historical rejection, held escrow/agreed amount,
and absence of an operation. It calls the same authorization creation/audit and entity money snapshot
as approval, using `TIMER_C` with no review ID. Creation, audit, and clearing the completed deadline
commit together. Escrow remains `HELD`; no synthetic brand review or payment transaction is inserted.

`EscrowReleaseDeadlineJob` follows Timer A/B scheduling conventions: every minute by default in
`Asia/Beirut`, configurable with `flavors.escrow.release-deadline-cron` and
`flavors.escrow.release-deadline-zone`. Cron `-` disables the job. It calls
`EscrowReleaseProcessingService.authorizeOverdueBatch()`, which suspends any caller transaction,
reuses `ProofReviewService.findOverdueCampaignIds()` (at most 100), and invokes the proxied release
service once per candidate. Each campaign has its own commit/rollback and failures are logged by
campaign ID and exception type without aborting later candidates. Uncommitted work remains eligible
on the next run; committed operations are excluded by the query and checked again under lock.
The query also excludes ineligible escrow and historical rejections so those do not occupy the batch.
Multiple application instances may scan the same IDs; the shared campaign lock and unique operation
identity serialize authorization. No cluster-wide scheduler lease is needed for this database-only path.

Interfaces and planned extensions (implementations belong under `service/impl/`; no controller calls a repository):

- `EscrowReleaseService`: `authorizeApproved(campaignId, submissionId, reviewId)`,
  `authorizeOverdue(campaignId)`, and `freezeForRejection(campaignId, submissionId, reviewId)`.
  Approval and freezing join the review transaction; Timer C uses an independent transaction.
  Each locks/rechecks the campaign; no persistence entity crosses the service boundary.
- `EscrowReleaseProcessingService`: bounded `recoverReviewFollowUps()`, `authorizeOverdueBatch()`,
  and `processPendingPayouts()`. Approval and Timer C call the same authorization rules. Recovery
  will handle both old approved reviews and old rejected reviews that have not yet been processed.
  Only `authorizeOverdueBatch()` is implemented on this processing service in Step 4.
- `PayoutProvider`: `name()`, `submit(PayoutRequest)`, and `query(PayoutLookup)` returning typed
  `PayoutResult` outcomes: `PENDING`, `CONFIRMED`, `FAILED`, `UNKNOWN`. Requests carry operation key,
  destination reference, integer cents, and currency. Results distinguish provider acknowledgement
  from authoritative payment confirmation and include the provider reference when available.

Approval transaction: save the review, authorize a unique release, snapshot money, audit, commit.
Rejection transaction (Step 5): save the review, change `HELD` to `FROZEN`, audit, commit.
The persisted rejection is the Sprint 3 integration record; no dispute is created and no handoff is
published. Leave campaign `PROOF_SUBMITTED` until EEC-54 opens the dispute. No half-applied rejection
if any step fails. Identical retries must repair
legacy missing follow-ups before returning, or leave them discoverable by recovery. No release may
be authorized against a rejection/correction review, frozen/refunded escrow, stale proof, or ended
campaign. No synthetic brand approval is inserted when Timer C expires.

Step 5 implements `freezeForRejection(campaignId, submissionId, reviewId)` on the existing release
service. `ProofReviewServiceImpl` invokes it for a new rejection and for an identical rejection retry.
Its `MANDATORY` transaction propagation requires the caller's business transaction. It reuses the
campaign lock, rechecks the current/latest submitted round and owning brand's persisted rejection,
loads escrow with `findByCampaignId`, and refuses any existing release operation. Only `HELD` escrow
in a campaign awaiting proof review can start a freeze. `EscrowPayment.freeze` guards the transition;
the managed entity and `ESCROW_FROZEN` audit commit with the review, its audit, and deadline clearing.
The existing endpoint's ownership, reason, and strict deadline checks still precede a new decision.

An identical retry on already frozen escrow returns without updating financial state, timestamps, or
audit. A legacy held rejection can be repaired by retrying that same decision after the original
deadline; this does not insert or re-audit the historical review. No automatic legacy-rejection scan
is added in Step 5. Approval and Timer C retain their held-escrow/no-rejection guards. Freezing refuses
an existing operation, so it cannot conflict with a previously committed authorization. There is no
payout in this Step 5 path; Step 6 processing rechecks held escrow under the same campaign lock.
No new endpoint, migration, dispute row, or campaign `DISPUTED` transition is introduced.

Timer C uses the same campaign row lock as review and submission. After locking, read the time and
recheck `PROOF_SUBMITTED`, current submitted round, no review, deadline <= now, `HELD` escrow, and no
existing operation. At the exact deadline manual review is closed and Timer C is eligible. Concurrent
approval and scheduler paths converge on one operation. Rejection committed first prevents release;
an expired candidate read alone must never override a committed rejection or correction request.

Payout processing follows three phases:

1. Short database transaction: claim a due operation with a lease, using the campaign lock; pin
   provider/destination, persist reference/attempt information and `RELEASE` row before dispatch.
2. After commit: submit/query with no transaction or database lock held.
3. New transaction: lock the campaign and operation, verify lease and identity, then apply the result.
   On confirmed payment atomically update operation, transaction, escrow fee/net/status/timestamps,
   campaign completion, audit, and notifications. Duplicate confirmations change nothing.

A worker crash after dispatch is an unknown outcome. Persist `UNKNOWN` before the network boundary
so expired leases cannot be mistaken for never-dispatched work. Query by the stable operation identity
even if a response containing the provider reference was lost. Retry only after authoritative proof
that no transfer occurred, or under a provider-guaranteed idempotent resubmission contract. `UNKNOWN`
must never be mapped to a definitive failure just because a query timed out or returned an ambiguous
not-found answer. If that contract cannot be implemented, stop automatic submission and escalate.

Reuse `IdempotencyService.execute` for database-only worker preparation, with a fingerprint of the
immutable operation identity, destination, amount, and currency. Its result is a preparation receipt,
not payment confirmation. Keep a single lock order: idempotency row (when used), campaign, operation.
Review transactions already own the campaign lock, so they rely on the operation's natural unique key
and must not acquire an idempotency row in reverse order. Replayed preparation receipts must be
checked against current lease/status before any network action. Provider idempotency remains required.

Jobs process bounded batches with a transaction per campaign and fair retry scheduling so one blocked
operation cannot starve later ones. All writes use service transactions and the existing audit logger.
No provider invocation runs inside `IdempotencyService.execute` or a review transaction.

### Step 6 execution and reconciliation

`PayoutProvider` is separate from `PaymentProvider` (collection). Internal `PayoutRequest` carries the
stable operation key, synthetic destination, snapshotted creator net, and currency; `PayoutResult`
distinguishes pending acknowledgement, confirmed payment, definitive failure, and unknown outcome.
Results must match the complete request identity. Only definitive failure with an explicit guarantee
that no transfer occurred may be retryable. Timeout, ambiguous not-found, invalid identity, and lost
responses never make an unknown operation dispatchable again.

`flavors.payout.mode` / `FLAVORS_PAYOUT_MODE` defaults to `disabled` in every environment, independently
of `WHISH_MODE`. `UnavailablePayoutProvider` leaves authorizations durable and funds held. Explicit
`simulated` mode additionally requires an active `dev` or `test` profile; `prod` or `production`
forbids it even alongside dev/test. Unknown/real modes fail startup: there is no verified real adapter,
credential contract, or real beneficiary onboarding. The worker will not manufacture real destinations.

`EscrowPayoutService` / implementation runs without a caller transaction. It prepares a lease inside
`IdempotencyService.execute` with a fingerprint of the immutable snapshot, provider, and destination,
locking idempotency row -> campaign -> operation. It rechecks current proof/review, held agreed escrow,
campaign state, due time, and pinned provider/destination. One linked `RELEASE` transaction stores creator
net. Before first dispatch the operation becomes `UNKNOWN` and pins the provider/destination/transaction
with a two-minute UUID lease. A preparation receipt is never treated as a payment receipt or replayed
blindly: its live lease and eligibility are checked again after commit before provider I/O.

Submission/query occurs with no transaction or database lock held. A second transaction locks campaign
and operation, checks the lease token and result identity, and applies the outcome. Confirmation atomically
sets operation `CONFIRMED`, linked transaction `SUCCEEDED`, escrow snapshot/`RELEASED`/release timestamp,
campaign `COMPLETED`, and `PAYOUT_CONFIRMED`, `ESCROW_RELEASED`, `CAMPAIGN_COMPLETED` audits. Simulation
is explicitly marked in audit details and provider references. Submission acknowledgement keeps funds
held and the payment transaction pending. No fee is recalculated. Duplicate/stale results cannot apply twice.

`EscrowPayoutJob` runs every minute in `Asia/Beirut` by default, configurable with
`flavors.payout.processing-cron` / `flavors.payout.processing-zone`; cron `-` disables it. Each invocation
selects at most 100 due operations. Each claim/result has its own transaction and one failed operation
does not abort the rest. Pending/unknown outcomes wait one minute before queries; active leases exclude
concurrent claims. Expired leases and interrupted confirmation commits are recovered by querying the same
operation identity, never by dispatching again. A definitive safe failure may retry after one minute using
the same operation key and linked transaction; definitive nonretryable failures require operational review.

The simulator is an in-memory, idempotent fake ledger and performs no external transfers. Reconciliation
after a worker interruption can recover an accepted simulation while that ledger exists. If the entire
simulator process/ledger is lost, its query returns `UNKNOWN`, even with a reference: it cannot prove a
transfer and never invents a confirmation or permits a new dispatch. Such dev/test operations remain
held for inspection. A real adapter must provide durable lookup/idempotency guarantees before activation.
No real Whish payout endpoint or credential is assumed. Notifications remain later work; Step 7 labels
simulation explicitly in the escrow read model and campaign payment views.

## Sprint boundary: EEC-45 in Sprint 2, EEC-54 in Sprint 3

EEC-45 records rejection and freezes funds. The existing `proof_review` row (`REJECTED`, reason,
submission ID, deciding brand and timestamp), linked to the campaign via `proof_submission`, is the
durable integration record EEC-54 can discover later with `FROZEN` escrow. No new dispute service,
handoff table, acknowledgement API, dispute migration, or `Dispute` entity is part of this branch.

EEC-54 owns creating/reusing the dispute, moving the campaign to `DISPUTED`, evidence-window policy,
evidence submission, admin decisions, and resolution. Coordinate legacy rejection discovery and
evidence timing with its developer in Sprint 3. In Sprint 2, TC-F7.2.3 verifies persisted rejection,
frozen escrow, and prevention of release; actual dispute creation remains explicitly deferred.

## API and frontend integration points

Keep the existing approval endpoint; do not add a brand/creator endpoint that can release arbitrary
escrow. Extend the existing ownership-checked brand/creator escrow GET responses through an
`EscrowMapper`. Include original amount, authorized fee/net (nullable before authorization), escrow
status, payout status, and authorization/submission/confirmation timestamps. Never expose destination
identifiers, provider credentials, worker leases, or raw errors.

`ProofReviewResponse.requiredFollowUp` describes intent only; payment views read the actual operation.
`ProofOutcome.tsx`, both campaign detail pages, `api/payments.ts` and API types must show:

- Release authorized / payout pending: original amount, fee, creator net; no claim of receipt.
- Payout unavailable or failed: truthful pending/failure information and operational follow-up.
- Confirmed payout / escrow released: confirmed amount and date, with simulation explicitly labelled.
- Frozen after rejection: payment blocked; dispute creation is deferred to EEC-54 (Sprint 3).

Step 7 implements this through `EscrowMapper` and additive `EscrowResponse` fields. The existing
`campaignId`, `status`, original `amountCents`, `heldAt`, `releasedAt`, and `updatedAt` fields remain.
Nullable `feeCents`, `creatorNetCents`, `payoutStatus`, `authorizationSource`, `authorizedAt`,
`submittedAt`, `confirmedAt`, and `simulated` describe an operation when one exists; `payoutAvailable`
states whether execution is configured. Amounts come from the immutable snapshot, with no read-time
fee calculation. No provider/destination references, operation keys, transaction IDs, leases, counters,
credentials, or error codes are exposed. Both reads retain their ownership/role checks and execute
read-only at repeatable-read isolation so escrow and operation states share a consistent snapshot.

Both campaign pages use `payment/EscrowPanel.tsx` and the typed `getEscrow` API helper. It shows original
payment, fee/net when an operation exists, progress and timestamps, automatic Timer C authorization,
and explicit simulation/no-real-transfer wording. Only a confirmed operation with released escrow
shows confirmed payout. Frozen rejection blocks payment and names Sprint 3 EEC-54 without inventing
a dispute. `showsEscrow` retains released/frozen/refunded records while hiding never-paid expired
pending records. `ProofOutcome` describes authorization or freezing rather than obsolete automation claims.

The pages and panel are keyed by campaign/role, isolating local state across navigation. Requests are
cancelled logically on unmount or changed refresh dependencies; late results cannot update another
campaign. The panel refreshes visible unfinished escrow every 30 seconds and on focus/visibility,
skips overlapping calls, and stops network polling for released/refunded states. A local brand review
refreshes the campaign and panel immediately; status changes from jobs refresh the campaign header.
Refresh errors retain the last known information with an explicit stale-data warning. Existing styles
and integer-cent formatting are reused. No public release endpoint is added.

## Planned implementation files and tests

Implemented now: `entity/EscrowReleaseOperation.java`, `repository/EscrowReleaseOperationRepository.java`,
`enums/PayoutStatus.java`, `enums/ReleaseAuthorizationSource.java`, V10, and entity unit/integration tests.
Step 3 adds approval authorization/recovery and Step 4 adds Timer C authorization and its scheduled
job. Both reuse these persistence primitives without executing transfers.

Step 6 implements `PayoutProvider`, simulated/unavailable adapters, payout request/result DTOs,
configuration, and `EscrowPayoutService` / implementation plus its job. Execution is separate from
the existing Timer C processing service so each worker has its own transaction boundaries and schedule.
Step 7 adds `EscrowMapper` and payout-facing escrow API/frontend mapping. Notifications remain later
work; a real adapter remains blocked on the external contract. The release deadline job is implemented in Step 4.

Modify `ProofReviewServiceImpl`, `EscrowPayment`, `Campaign`, `PaymentTransaction` and repository
queries as required; extend `EscrowResponse` and `PaymentServiceImpl` read mapping; preserve the
collection port and adapters. V10 is implemented; recheck its version before merging. The real Whish
payout adapter, destination onboarding, and any admin manual-payment confirmation API are blocked
design follow-ups, not assumed implementations in this plan.

Unit and PostgreSQL/Testcontainers integration tests must cover approval and Timer C authorization,
fee rounding, net conservation, immutable snapshots, repeated and concurrent requests, overlapping jobs,
approval/rejection versus deadline races, recovery of old reviews, rollback atomicity, leases and worker
crashes, unknown outcomes and reconciliation before retry, definitive failures, duplicate/late provider
confirmation, frozen escrow, persisted rejection, and ownership/role boundaries. Simulated confirmation must
be explicit. TC-F7.2.1 asserts `RELEASED` only after simulation confirms payment, not immediately after
approval. TC-F7.2.2 uses the same path after timeout. TC-F7.2.3 asserts frozen funds and a persisted
rejection without depending on Sprint 3 dispute creation. Extend existing payment/review
tests and add frontend tests for pending, confirmed, frozen, unavailable, and simulation wording.

## External confirmations and rollout gates

### Final acceptance scope (Step 8)

The live [EEC-45 ticket](https://michouli.atlassian.net/browse/EEC-45) still describes actual money
release and includes dispute opening in EX-F7.2.2 / TC-F7.2.3. The implementation must not be described
as fully satisfying that literal end-to-end scope while real payouts are disabled. Application logic
and simulation can be reviewed independently of those external/deferred outcomes:

| Criterion | Application implementation | Literal end-to-end acceptance |
|---|---|---|
| FR-F7.9 | Approval atomically authorizes release; simulation can confirm it | Partial: actual creator transfer requires verified Whish capability |
| FR-F7.10 | Timer C automatically authorizes unanswered current proof after five days; same payout path | Partial: automatic real transfer requires the real provider |
| FR-F7.11 | HALF_UP 15% snapshot, net by subtraction, confirmation-only `RELEASED`; simulation verified | Partial for actual settlement; fee/accounting/confirmation rules are implemented |
| FR-F7.12 | No guaranteed arrival date or SLA claim | Blocked: provider capability, confirmation contract and two-business-day SLA remain external |
| EX-F7.2.1 | Timer C path is implemented and simulated end to end | Partial until real settlement is available |
| EX-F7.2.2 | Rejection and `FROZEN` commit together, with durable review history | Partial: dispute opening belongs to Sprint 3 EEC-54 |
| TC-F7.2.1 | Approval -> simulated payout -> fee/net/read model/released state verified | Pass in simulation; real-money variant externally blocked |
| TC-F7.2.2 | Expired five-day proof -> deadline job -> payout job -> creator read model verified | Pass in simulation; real-money variant externally blocked |
| TC-F7.2.3 | Frozen escrow, saved rejection, no payout and no dispute row verified | Partial: literal dispute-opening assertion is intentionally deferred |

[EEC-54](https://michouli.atlassian.net/browse/EEC-54) owns dispute opening, evidence, decisions and
resolution. Its deferred work is not silently treated as delivered here. Do not close EEC-45 as fully
accepted on the strength of simulation alone; reviewers must explicitly acknowledge this delivery boundary.

### Activation prerequisites

1. Whish virtual account approval and separate authorization for independent creator payouts.
2. Official transfer/status API, credentials, beneficiary requirements, currency/amount validation,
   idempotency guarantees, reconciliation after lost responses, and the meaning of final confirmation.
3. Verified settlement timing, business calendar/holidays/cutoffs, fees, and failure/reversal handling.
4. A verified payout-destination onboarding/security design. The application currently has none.
5. EEC-54's Sprint 3 rejection-discovery and evidence-window policy (not a blocker for Sprint 2 payout persistence).
6. Recheck V10 availability before merging if another branch adds a migration concurrently.

Development/tests use simulated payouts. Production defaults to unavailable and refuses simulated
payout configuration, independently of collection settings. A production flag alone must not create
a working Whish adapter: production activation requires the confirmed capability contract and a
reviewed implementation. A manual fallback would need a separately approved operator workflow and
verifiable transfer evidence; it is not enabled by this design. Do not advertise FR-F7.12 as delivered
until actual creator settlement within the agreed service window has been established.
## Manual Payout Investigation

Payouts that exceed 10 reconciliation attempts or fail
without permission to retry require manual investigation.

Operations can be identified using this read-only query:

```sql
SELECT id, escrow_id, operation_key, status,
       query_attempts, dispatch_attempts,
       payout_provider, provider_ref, last_error_code
FROM escrow_release_operation
WHERE (status IN ('UNKNOWN', 'SUBMITTED') AND query_attempts >= 10)
   OR (status = 'FAILED' AND retryable = false);