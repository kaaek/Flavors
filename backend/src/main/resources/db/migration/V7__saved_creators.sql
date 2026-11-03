-- V7: saved creators (FT-54 / EEC-71). Brands can bookmark approved creators
-- and return to them later.
-- The composite primary key prevents duplicate bookmarks.
-- Never edit this file once merged; add a new migration instead.
-- ---------------------------------------------------------------------------
-- Saved creators: one row per brand and creator pair.
-- ---------------------------------------------------------------------------
CREATE TABLE saved_creator (
    brand_id BIGINT NOT NULL REFERENCES brand(user_id),
    creator_id BIGINT NOT NULL REFERENCES creator_profile(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (brand_id, creator_id)
);
CREATE INDEX saved_creator_creator_id_idx
    ON saved_creator (creator_id);
