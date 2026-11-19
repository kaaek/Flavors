
-- EEC-45: Persist evidence for manual payout decisions.
-- This table records decisions; it does not transfer money.

CREATE TABLE payout_manual_resolution (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    operation_id BIGINT NOT NULL
        REFERENCES escrow_release_operation(id),

    admin_user_id BIGINT NOT NULL
        REFERENCES app_user(id),

    resolution VARCHAR(40) NOT NULL
        CHECK (resolution IN (
            'PAYOUT_CONFIRMED',
            'NO_TRANSFER_CONFIRMED'
        )),

    evidence_reference VARCHAR(500) NOT NULL
        CHECK (btrim(evidence_reference) <> ''),

    reason VARCHAR(1000) NOT NULL
        CHECK (btrim(reason) <> ''),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payout_manual_resolution_operation_idx
    ON payout_manual_resolution(operation_id);
