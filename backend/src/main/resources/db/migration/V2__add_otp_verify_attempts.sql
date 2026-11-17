-- TNB-5: count wrong OTP entries per password-reset request (AC5: 3 wrong OTPs cancel the request)

ALTER TABLE otp_challenges
    ADD COLUMN verify_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE otp_challenges
    ADD CONSTRAINT chk_otp_challenges_verify_attempts CHECK (verify_attempts >= 0);
