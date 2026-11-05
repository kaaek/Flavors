-- V8: search (UC-F2.1 creators for brands, plus brands for creators). Never edit this file once
-- merged; add a new migration instead.
--
-- pg_trgm ships with Postgres (contrib) and is a "trusted" extension, so the database owner can
-- create it without superuser rights (true for the Railway Postgres service too).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Brands get a cuisine and a description so creators can search for them. Nullable, so brands that
-- registered before this migration keep working; the brand fills them in later.
ALTER TABLE brand
    ADD COLUMN cuisine     varchar(50),
    ADD COLUMN description text;

-- Trigram GIN indexes make the ILIKE '%word%' searches fast. The typo-tolerant word_similarity()
-- comparisons run as a filter on the rows left over; at MVP size that is cheap.
CREATE INDEX creator_profile_display_name_trgm_idx ON creator_profile USING gin (display_name gin_trgm_ops);
CREATE INDEX creator_profile_food_category_trgm_idx ON creator_profile USING gin (food_category gin_trgm_ops);
CREATE INDEX creator_profile_location_trgm_idx ON creator_profile USING gin (location gin_trgm_ops);
CREATE INDEX creator_profile_bio_trgm_idx ON creator_profile USING gin (bio gin_trgm_ops);
CREATE INDEX brand_business_name_trgm_idx ON brand USING gin (business_name gin_trgm_ops);
CREATE INDEX brand_cuisine_trgm_idx ON brand USING gin (cuisine gin_trgm_ops);
CREATE INDEX branch_name_trgm_idx ON branch USING gin (name gin_trgm_ops);
CREATE INDEX branch_location_trgm_idx ON branch USING gin (location gin_trgm_ops);
