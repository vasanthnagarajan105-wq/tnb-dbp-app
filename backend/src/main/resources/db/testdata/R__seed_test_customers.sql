-- SYNTHETIC TEST DATA - loaded only in dev and sit profiles. Never in production.
-- All customers use the password: Test@1234
-- Mobile numbers and emails are fictitious.
-- Repeatable migration (R__): re-runs when this file changes; ON CONFLICT keeps it safe to re-run.

INSERT INTO customers (customer_id, full_name, mobile, email, password_hash, failed_attempts, locked, locked_at)
VALUES
    ('TNB100001', 'Arun Kumar',  '9000000001', 'arun.kumar@example.com',
     '$2a$10$uyAuS6./Fik.TxrmAGtt0.vJAK0kkqENgDyCTxl7cSyhOi2TW4G5i', 0, FALSE, NULL),
    ('TNB100002', 'Priya Raman', '9000000002', 'priya.raman@example.com',
     '$2a$10$uyAuS6./Fik.TxrmAGtt0.vJAK0kkqENgDyCTxl7cSyhOi2TW4G5i', 0, FALSE, NULL),
    ('TNB100003', 'Karthik S',   '9000000003', 'karthik.s@example.com',
     '$2a$10$uyAuS6./Fik.TxrmAGtt0.vJAK0kkqENgDyCTxl7cSyhOi2TW4G5i', 3, TRUE, NOW())
ON CONFLICT (customer_id) DO NOTHING;
