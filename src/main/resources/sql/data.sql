-- Demo data. Runs on every start, so every insert only adds a row that is not there yet.
-- Works on both H2 and MySQL. Tables are created by Hibernate from the entity classes.

-- Illustrative flagged zones in central Bengaluru. Synthetic examples, not real crime data.
INSERT INTO safety_zones (name, description, latitude, longitude, radius_km, risk_level, active, created_at)
SELECT 'Demo zone: unlit underpass', 'Synthetic example: poor street lighting and an isolated underpass after dark.',
       12.9770, 77.6060, 0.35, 'HIGH', TRUE, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM safety_zones WHERE name = 'Demo zone: unlit underpass');

INSERT INTO safety_zones (name, description, latitude, longitude, radius_km, risk_level, active, created_at)
SELECT 'Demo zone: quiet service road', 'Synthetic example: little public activity late at night.',
       12.9725, 77.5915, 0.30, 'MEDIUM', TRUE, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM safety_zones WHERE name = 'Demo zone: quiet service road');

INSERT INTO safety_zones (name, description, latitude, longitude, radius_km, risk_level, active, created_at)
SELECT 'Demo zone: low-footfall lane', 'Synthetic example: narrow lane with few shops open after 9 PM.',
       12.9808, 77.6004, 0.40, 'HIGH', TRUE, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM safety_zones WHERE name = 'Demo zone: low-footfall lane');
