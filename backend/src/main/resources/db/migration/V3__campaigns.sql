-- V3: campaigns, from the brand's draft to completion, plus counter-offers, cancellations and reviews.
-- Conventions as in V1 (docs/Architecture.md §4.1).
-- Never edit this file once merged; add a new migration instead.

-- ---------------------------------------------------------------------------
-- Campaigns (UC-F3.1, F4.1, F9.1, F22.1). Status flow (spec §4):
--   DRAFT -> AWAITING_CREATOR_RESPONSE (Timer A, 3 days)
--         -> REJECTED | DECLINED | EXPIRED | COUNTERED -> AWAITING_PAYMENT (Timer B, 3 days)
--         -> ACTIVE (escrow held) -> PROOF_SUBMITTED (Timer C, 5 days)
--         -> CORRECTIONS_REQUESTED (max 2) | DISPUTED -> COMPLETED
--   Side exits: CANCELLED, REMOVED (by an admin).
-- The application moves campaigns between statuses; the database checks the values and the
-- rules that hold in every status.
-- ---------------------------------------------------------------------------
CREATE TABLE campaign (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id               bigint       NOT NULL REFERENCES brand (user_id),
    -- Exactly one creator per campaign (FR-F3.1). May be empty while the campaign is a draft.
    creator_id             bigint       REFERENCES creator_profile (user_id),
    name                   varchar(150) NOT NULL,
    status                 varchar(30)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'AWAITING_CREATOR_RESPONSE', 'COUNTERED', 'REJECTED', 'DECLINED',
                          'EXPIRED', 'AWAITING_PAYMENT', 'ACTIVE', 'PROOF_SUBMITTED',
                          'CORRECTIONS_REQUESTED', 'DISPUTED', 'COMPLETED', 'CANCELLED', 'REMOVED')),
    -- The brand's 'budget': what it offers to pay the creator.
    price_offered_cents    bigint       CHECK (price_offered_cents >= 0),
    -- What will actually be paid: the offered price, or the counter-offer price if the brand approved
    -- one (FR-F4.6). Set when the creator accepts.
    agreed_price_cents     bigint       CHECK (agreed_price_cents >= 0),
    start_date             date,
    last_posting_date      date,
    -- The brand may move the last posting date once (FR-F9.4).
    posting_extension_used boolean      NOT NULL DEFAULT false,
    content_requirements   text,
    sent_at                timestamptz,
    -- Timer A: sent_at + 3 days for the creator to answer (FR-F4.5).
    response_deadline      timestamptz,
    accepted_at            timestamptz,
    -- Timer B: accepted_at + 3 days for the brand to pay (FR-F7.6).
    payment_deadline       timestamptz,
    -- Timer C: 5 days after each final proof submission, then payment is released (FR-F6.5).
    proof_review_deadline  timestamptz,
    correction_rounds_used smallint     NOT NULL DEFAULT 0 CHECK (correction_rounds_used BETWEEN 0 AND 2),
    removed_reason         text,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    CHECK (start_date <= last_posting_date),
    -- A draft may be incomplete (FR-F3.4), but a campaign can't be sent without these (FR-F3.3).
    CHECK (status = 'DRAFT' OR (creator_id IS NOT NULL AND price_offered_cents IS NOT NULL
                                AND start_date IS NOT NULL AND last_posting_date IS NOT NULL)),
    -- An admin must give a reason to remove a campaign (EX-F22.1.2).
    CHECK (status <> 'REMOVED' OR removed_reason IS NOT NULL)
);

CREATE INDEX campaign_brand_id_idx ON campaign (brand_id);
CREATE INDEX campaign_creator_id_idx ON campaign (creator_id);
-- The timer jobs look up campaigns by status (e.g. all AWAITING_PAYMENT past their deadline).
CREATE INDEX campaign_status_idx ON campaign (status);

-- The brand's branches the campaign is for (FR-F3.1). The application checks that each branch
-- belongs to the campaign's brand.
CREATE TABLE campaign_branch (
    campaign_id bigint NOT NULL REFERENCES campaign (id),
    branch_id   bigint NOT NULL REFERENCES branch (id),
    PRIMARY KEY (campaign_id, branch_id)
);

-- The primary key's index starts with campaign_id; branch_id needs its own.
CREATE INDEX campaign_branch_branch_id_idx ON campaign_branch (branch_id);

-- What the creator has to publish, e.g. 2 x Instagram reel (FR-F3.1).
CREATE TABLE campaign_deliverable (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id bigint      NOT NULL REFERENCES campaign (id),
    -- No CHECK lists yet: SPIKE-1 picks the platform, and the post types depend on it.
    platform    varchar(30) NOT NULL,
    type        varchar(30) NOT NULL,
    count       integer     NOT NULL CHECK (count > 0)
);

CREATE INDEX campaign_deliverable_campaign_id_idx ON campaign_deliverable (campaign_id);

-- ---------------------------------------------------------------------------
-- Counter-offer (FR-F4.2-F4.4). The creator may counter once per campaign; the unique
-- campaign_id makes a second one impossible (EX-F4.1.4).
-- ---------------------------------------------------------------------------
CREATE TABLE counter_offer (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id  bigint      NOT NULL UNIQUE REFERENCES campaign (id),
    -- The full proposed terms, not only what changed, so the brand sees one complete offer.
    price_cents  bigint      NOT NULL CHECK (price_cents >= 0),
    -- Same shape as campaign_deliverable rows: [{"platform": ..., "type": ..., "count": ...}].
    deliverables jsonb       NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    decided_at   timestamptz,
    -- decided_at is set exactly when the brand has answered.
    CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- ---------------------------------------------------------------------------
-- Cancellations (UC-F9.1). Every cancellation and its outcome is recorded (FR-F9.5).
-- ---------------------------------------------------------------------------
CREATE TABLE cancellation (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id  bigint      NOT NULL REFERENCES campaign (id),
    cancelled_by bigint      NOT NULL REFERENCES app_user (id),
    -- The stage decides the rule that applies (FR-F9.1-F9.4):
    --   BEFORE_ACCEPTANCE: brand cancels freely, no charge
    --   BEFORE_WORK:       full refund, warning to whoever cancels
    --   WORK_STARTED:      goes to a dispute (UC-F8.1)
    --   NO_PROOF_BY_DEADLINE: no proof by the last posting date, brand gets a full refund
    stage        varchar(30) NOT NULL
        CHECK (stage IN ('BEFORE_ACCEPTANCE', 'BEFORE_WORK', 'WORK_STARTED', 'NO_PROOF_BY_DEADLINE')),
    outcome      varchar(20) NOT NULL CHECK (outcome IN ('NO_CHARGE', 'FULL_REFUND', 'DISPUTE_OPENED')),
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX cancellation_campaign_id_idx ON cancellation (campaign_id);
CREATE INDEX cancellation_cancelled_by_idx ON cancellation (cancelled_by);

-- ---------------------------------------------------------------------------
-- Reviews (UC-F1.4): the brand rates the creator once per completed campaign (FR-F1.11).
-- Reviews can't be edited after submission; the application has no update for them.
-- ---------------------------------------------------------------------------
CREATE TABLE review (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id bigint      NOT NULL UNIQUE REFERENCES campaign (id),
    rating      smallint    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    -- Optional.
    text        text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Foreign keys left open in V2, now that campaign exists.
-- ---------------------------------------------------------------------------
ALTER TABLE social_post_metric
    ADD CONSTRAINT social_post_metric_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES campaign (id);

ALTER TABLE profile_warning
    ADD CONSTRAINT profile_warning_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES campaign (id);
