-- V4: follower offers, diner claims (personal QR codes) and redemptions at the till.
-- Conventions as in V1 (docs/Architecture.md §4.1).
-- Never edit this file once merged; add a new migration instead.

-- ---------------------------------------------------------------------------
-- Offers (UC-F10.1): one percentage discount per campaign (FR-F10.1). Whether an offer is
-- live (campaign ACTIVE, not expired, not full) is computed by the application (FR-F10.4).
-- ---------------------------------------------------------------------------
CREATE TABLE offer (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id        bigint      NOT NULL UNIQUE REFERENCES campaign (id),
    discount_percent   smallint    NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
    expires_at         timestamptz NOT NULL,
    -- Days the offer can be used, one bit per day: Monday = 1, Tuesday = 2, Wednesday = 4, ...
    -- Sunday = 64. Every day = 127. At least one day must be set.
    valid_days         smallint    NOT NULL DEFAULT 127 CHECK (valid_days BETWEEN 1 AND 127),
    -- Hours the offer can be used, in the restaurant's local time. Both null means all day.
    -- Hours past midnight (e.g. 22:00-02:00) are not supported in the MVP.
    valid_from         time,
    valid_to           time,
    -- True: any of the brand's branches. False: only the branches in offer_branch.
    all_branches       boolean     NOT NULL DEFAULT true,
    min_purchase_cents bigint      NOT NULL DEFAULT 0 CHECK (min_purchase_cents >= 0),
    -- Null means no maximum.
    max_redemptions    integer     CHECK (max_redemptions > 0),
    -- Redemptions allowed per diner for this campaign; one unless the brand allows more (FR-F10.3).
    per_person_limit   smallint    NOT NULL DEFAULT 1 CHECK (per_person_limit >= 1),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CHECK ((valid_from IS NULL) = (valid_to IS NULL)),
    CHECK (valid_from < valid_to)
);

-- The branches of an offer with all_branches = false. The application checks that each branch
-- belongs to the campaign's brand.
CREATE TABLE offer_branch (
    offer_id  bigint NOT NULL REFERENCES offer (id),
    branch_id bigint NOT NULL REFERENCES branch (id),
    PRIMARY KEY (offer_id, branch_id)
);

CREATE INDEX offer_branch_branch_id_idx ON offer_branch (branch_id);

-- ---------------------------------------------------------------------------
-- Claims (UC-F10.2): a diner enters a phone number on the storefront and gets a personal QR code.
-- The phone number is personal data (NF-14): it is stored only encrypted, shown masked to the brand
-- and never to the creator.
-- ---------------------------------------------------------------------------
CREATE TABLE offer_claim (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id   bigint      NOT NULL REFERENCES offer (id),
    -- Encrypted by the application; decrypted only to show the masked number to the brand.
    phone_enc  bytea       NOT NULL,
    -- Keyed hash (HMAC) of the normalised phone number, to find a diner's earlier claims without
    -- decrypting every row. It is keyed because phone numbers are few enough that a plain hash
    -- could be reversed by trying them all.
    phone_hash varchar(64) NOT NULL,
    -- When the diner accepted the consent notice; no claim exists without it (EX-F10.2.1).
    consent_at timestamptz NOT NULL,
    -- Random value in the QR code; it reveals nothing about the diner or the offer.
    qr_token   varchar(64) NOT NULL UNIQUE,
    status     varchar(20) NOT NULL DEFAULT 'UNUSED' CHECK (status IN ('UNUSED', 'REDEEMED')),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- For the per-person limit: "how many times has this phone claimed this offer?" (FR-F10.7).
CREATE INDEX offer_claim_offer_phone_idx ON offer_claim (offer_id, phone_hash);

-- A diner holds at most one unused claim per offer: claiming again shows the same QR code instead
-- of creating a second one (EX-F10.2.3). Being a unique index, it also holds when two claims
-- arrive at the same moment.
CREATE UNIQUE INDEX offer_claim_one_unused_uq ON offer_claim (offer_id, phone_hash) WHERE status = 'UNUSED';

-- ---------------------------------------------------------------------------
-- Redemptions (UC-F11.1): recorded only by a successful scan at the till, when the bill is paid
-- and the discount given (FR-F11.2).
-- ---------------------------------------------------------------------------
CREATE TABLE redemption (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Unique: the same QR code scanned twice registers once (FR-F11.7, NF-6).
    claim_id       bigint      NOT NULL UNIQUE REFERENCES offer_claim (id),
    -- The ids below can all be reached through the claim, but they are copied here because the ROI
    -- dashboards count and sum redemptions per campaign, creator, brand and branch (F12, F13, F14),
    -- and must update within a minute (NF-13). A redemption is never changed, so the copies can't go stale.
    offer_id       bigint      NOT NULL REFERENCES offer (id),
    campaign_id    bigint      NOT NULL REFERENCES campaign (id),
    creator_id     bigint      NOT NULL REFERENCES creator_profile (user_id),
    brand_id       bigint      NOT NULL REFERENCES brand (user_id),
    branch_id      bigint      NOT NULL REFERENCES branch (id),
    -- Bill total before the discount, as the cashier typed it.
    bill_cents     bigint      NOT NULL CHECK (bill_cents > 0),
    -- bill x discount percent (FR-F11.6).
    discount_cents bigint      NOT NULL CHECK (discount_cents >= 0),
    redeemed_at    timestamptz NOT NULL DEFAULT now(),
    CHECK (discount_cents <= bill_cents)
);

CREATE INDEX redemption_offer_id_idx ON redemption (offer_id);
CREATE INDEX redemption_campaign_id_idx ON redemption (campaign_id);
CREATE INDEX redemption_creator_id_idx ON redemption (creator_id);
CREATE INDEX redemption_brand_id_idx ON redemption (brand_id);
CREATE INDEX redemption_branch_id_idx ON redemption (branch_id);

-- ---------------------------------------------------------------------------
-- Failed scans (FR-F11.5): logged with the reason and never counted as redemptions (FR-F12.3).
-- ---------------------------------------------------------------------------
CREATE TABLE scan_failure (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- As scanned, with no foreign key: an invalid token matches no claim.
    qr_token   varchar(64) NOT NULL,
    branch_id  bigint      NOT NULL REFERENCES branch (id),
    -- Null when the failure happened before a bill amount was entered.
    bill_cents bigint      CHECK (bill_cents > 0),
    -- The rejections listed in FR-F11.4 and EX-F11.1.1-F11.1.5.
    reason     varchar(30) NOT NULL
        CHECK (reason IN ('INVALID_TOKEN', 'OFFER_NOT_LIVE', 'OFFER_EXPIRED', 'OUTSIDE_VALID_TIME',
                          'ALREADY_USED', 'PERSON_LIMIT_REACHED', 'WRONG_BRANCH', 'BELOW_MINIMUM',
                          'OFFER_FULL')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX scan_failure_branch_id_idx ON scan_failure (branch_id);
