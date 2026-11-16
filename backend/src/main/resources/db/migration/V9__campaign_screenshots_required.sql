-- V9: the brand can ask for screenshots with the delivery proof (FR-F5.1, EEC-46). Never edit this file
-- once merged; add a new migration instead.
--
-- Part of the agreed terms: set while the campaign is a draft and frozen once it is sent, like the
-- other terms. When true, a final proof submission needs at least one screenshot; otherwise
-- screenshots stay optional. Existing campaigns keep the old behaviour (optional).
ALTER TABLE campaign
    ADD COLUMN screenshots_required boolean NOT NULL DEFAULT false;
