ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verify_token VARCHAR(128);
ALTER TABLE users ADD COLUMN email_verify_token_expiry TIMESTAMP;

-- User đã tồn tại trước khi có email verification -> coi như đã verify
UPDATE users SET email_verified = TRUE;
