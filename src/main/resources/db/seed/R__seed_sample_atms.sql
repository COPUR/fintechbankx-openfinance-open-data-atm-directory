-- Sample ATMs for dev and CI only. Flyway reads classpath:db/seed only when
-- ATM_DIRECTORY_SEED_ENABLED=true (FlywaySeedConfiguration); never in staging/prod.
-- Idempotent: rows that already exist (seeded or imported) are left untouched.

INSERT INTO atm (atm_id, name, status, latitude, longitude, address_line, city, country_code,
                 accessibility, services, currency, updated_at, version)
VALUES
    ('ATM-001', 'Downtown Branch ATM', 'InService', 25.2048, 55.2708, 'Sheikh Zayed Rd', 'Dubai', 'AE',
     'Wheelchair', ARRAY['CashWithdrawal', 'CashDeposit'], 'AED', TIMESTAMPTZ '2026-03-01T00:00:00Z', 0),
    ('ATM-002', 'Marina ATM', 'InService', 25.0800, 55.1400, 'Dubai Marina Walk', 'Dubai', 'AE',
     'Wheelchair', ARRAY['CashWithdrawal'], 'AED', TIMESTAMPTZ '2026-03-02T00:00:00Z', 0),
    ('ATM-003', 'Abu Dhabi Mall ATM', 'OutOfService', 24.4950, 54.3820, 'Abu Dhabi Mall', 'Abu Dhabi', 'AE',
     'Standard', ARRAY['CashWithdrawal'], 'AED', TIMESTAMPTZ '2026-03-03T00:00:00Z', 0)
ON CONFLICT (atm_id) DO NOTHING;
