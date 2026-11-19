
-- Persist the first time a payout requires manual review.
-- Reconciliation continues even after escalation.

ALTER TABLE escrow_release_operation
ADD COLUMN needs_review_at TIMESTAMPTZ;
