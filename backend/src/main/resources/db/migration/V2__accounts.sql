-- V2: account details per role. A creator or brand is an app_user (V1) plus one profile row here,
-- keyed by the same id. Conventions as in V1 (docs/Architecture.md §4.1).
-- Never edit this file once merged; add a new migration instead.
--
-- Some tables point at campaigns (social_post_metric, profile_warning), but the campaign table is
-- only created in V3. Their campaign_id columns and indexes are created here; V3 adds the foreign keys.

-- ---------------------------------------------------------------------------
-- Creators (UC-F19.1, F1, F20). A new creator starts as PENDING_APPROVAL and is not
-- searchable and has no storefront until an admin approves them (FR-F19.4).
-- ---------------------------------------------------------------------------
CREATE TABLE creator_profile (
    user_id             bigint       PRIMARY KEY REFERENCES app_user (id),
    display_name        varchar(100) NOT NULL,
    bio                 text,
    food_category       varchar(50)  NOT NULL,
    -- City or country (FR-F19.2).
    location            varchar(100) NOT NULL,
    price_min_cents     bigint       NOT NULL CHECK (price_min_cents >= 0),
    price_max_cents     bigint       NOT NULL,
    verification_status varchar(20)  NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (verification_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    rejection_reason    text,
    -- The admin who approved or rejected the creator, and when.
    reviewed_by         bigint       REFERENCES app_user (id),
    reviewed_at         timestamptz,
    -- Public storefront address (/s/<slug>). Null until the creator has a storefront.
    storefront_slug     varchar(80)  UNIQUE,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CHECK (price_min_cents <= price_max_cents),
    -- A rejection must tell the creator why.
    CHECK (verification_status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

CREATE INDEX creator_profile_reviewed_by_idx ON creator_profile (reviewed_by);

-- ---------------------------------------------------------------------------
-- Connected social accounts (UC-F19.4, F1.3). Follower and engagement figures come
-- from the platform, never typed by the creator, so brands can trust them.
-- ---------------------------------------------------------------------------
CREATE TABLE social_account (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    creator_id       bigint       NOT NULL REFERENCES creator_profile (user_id),
    -- No CHECK list yet: SPIKE-1 picks the platform for the MVP.
    platform         varchar(30)  NOT NULL,
    -- The account's id on the platform.
    external_id      varchar(100) NOT NULL,
    -- OAuth token, encrypted by the application before it is stored. Null after a disconnect.
    access_token_enc bytea,
    -- NEEDS_RECONNECTION when the creator disconnects or a daily pull fails (EX-F1.3.1).
    status           varchar(20)  NOT NULL DEFAULT 'CONNECTED'
        CHECK (status IN ('CONNECTED', 'NEEDS_RECONNECTION')),
    followers        bigint       CHECK (followers >= 0),
    -- Percentage, e.g. 4.25 means 4.25 %.
    engagement_rate  numeric(7, 2) CHECK (engagement_rate >= 0),
    -- Shown as 'last updated'; a failed pull keeps the old time.
    last_pulled_at   timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    -- One platform account can't be connected by two creators.
    UNIQUE (platform, external_id)
);

CREATE INDEX social_account_creator_id_idx ON social_account (creator_id);

-- Per-post figures from the daily pull (FR-F1.2), summed per campaign for the ROI
-- dashboards (FR-F13.6). One row per post, overwritten by each pull.
CREATE TABLE social_post_metric (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    social_account_id bigint       NOT NULL REFERENCES social_account (id),
    external_post_id  varchar(100) NOT NULL,
    -- The campaign this post was made for; null for the creator's other posts.
    -- Foreign key added in V3.
    campaign_id       bigint,
    views             bigint       NOT NULL DEFAULT 0 CHECK (views >= 0),
    likes             bigint       NOT NULL DEFAULT 0 CHECK (likes >= 0),
    comments          bigint       NOT NULL DEFAULT 0 CHECK (comments >= 0),
    shares            bigint       NOT NULL DEFAULT 0 CHECK (shares >= 0),
    pulled_at         timestamptz  NOT NULL,
    UNIQUE (social_account_id, external_post_id)
);

CREATE INDEX social_post_metric_campaign_id_idx ON social_post_metric (campaign_id);

-- ---------------------------------------------------------------------------
-- Brands (UC-F19.2). One account per restaurant, no admin approval in the MVP;
-- branches are records under it (FR-F19.8).
-- ---------------------------------------------------------------------------
CREATE TABLE brand (
    user_id        bigint       PRIMARY KEY REFERENCES app_user (id),
    business_name  varchar(150) NOT NULL,
    contact_person varchar(100) NOT NULL,
    phone          varchar(30)  NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

-- A brand needs at least one branch to register (FR-F19.6). That rule spans two inserts,
-- so the registration service enforces it, not the database.
CREATE TABLE branch (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id   bigint       NOT NULL REFERENCES brand (user_id),
    name       varchar(100) NOT NULL,
    location   varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX branch_brand_id_idx ON branch (brand_id);

-- ---------------------------------------------------------------------------
-- Warnings shown on a profile when that side cancels an accepted campaign (FR-F9.2).
-- ---------------------------------------------------------------------------
CREATE TABLE profile_warning (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES app_user (id),
    -- Foreign key added in V3.
    campaign_id bigint      NOT NULL,
    reason      text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX profile_warning_user_id_idx ON profile_warning (user_id);
CREATE INDEX profile_warning_campaign_id_idx ON profile_warning (campaign_id);
