-- V6: the campaigns a creator chose to feature on her verified portfolio (UC-F1.2, FR-F1.6).
-- Conventions as in V1 (docs/Architecture.md §4.1).
-- Never edit this file once merged; add a new migration instead.
--
-- Only COMPLETED campaigns can be featured (EX-F1.2.1), and only the campaign's own creator can
-- feature it. Those rules span two tables, so the application enforces them, not the database.
-- Whether a featured campaign is shown as 'verified' (completed and at least one redemption,
-- FR-F1.3) is worked out when the portfolio is read, so nothing about it is stored here.

CREATE TABLE portfolio_feature (
    -- A campaign has exactly one creator, so it can be featured at most once.
    campaign_id bigint      PRIMARY KEY REFERENCES campaign (id),
    creator_id  bigint      NOT NULL REFERENCES creator_profile (user_id),
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- The portfolio page loads all featured campaigns of one creator.
CREATE INDEX portfolio_feature_creator_id_idx ON portfolio_feature (creator_id);