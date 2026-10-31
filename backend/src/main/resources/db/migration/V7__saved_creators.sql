CREATE TABLE saved_creator (
    brand_id BIGINT NOT NULL REFERENCES brand(user_id),
    creator_id BIGINT NOT NULL REFERENCES creator_profile(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (brand_id, creator_id)
);
CREATE INDEX saved_creator_creator_id_idx
    ON saved_creator (creator_id);
