-- EEC-75: TOTP credentials are admin-only. Older migrations must remain unchanged.
CREATE TABLE admin_mfa (
  user_id bigint PRIMARY KEY REFERENCES app_user(id),
  secret_ciphertext text NOT NULL,
  last_used_step bigint NOT NULL DEFAULT -1,
  enrolled_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE admin_mfa_recovery_code (
  user_id bigint NOT NULL REFERENCES admin_mfa(user_id),
  code_hash char(64) NOT NULL,
  PRIMARY KEY (user_id, code_hash)
);

-- Across browser sessions, cap MFA guesses per admin account in a rolling window.
CREATE TABLE admin_mfa_attempt (
  user_id bigint PRIMARY KEY REFERENCES app_user(id),
  attempt_count integer NOT NULL DEFAULT 0,
  first_attempt_at timestamptz NOT NULL DEFAULT now()
);
