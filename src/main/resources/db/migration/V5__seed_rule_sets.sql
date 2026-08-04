INSERT INTO rule_sets (id, position, version, active_from, min_approve_score, max_reject_score, weights, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'java-senior', 'v1', '2026-06-04T00:00:00Z', 80, 45,
     '[
        {"key": "java_spring", "weight": 25},
        {"key": "postgres_acid", "weight": 20},
        {"key": "kafka_reliability", "weight": 25},
        {"key": "contracts", "weight": 15},
        {"key": "observability", "weight": 15}
      ]'::jsonb,
     '2026-06-04T00:00:00Z'),

    ('11111111-1111-1111-1111-111111111102', 'java-senior', 'v2', '2026-07-01T00:00:00Z', 75, 40,
     '[
        {"key": "java_spring", "weight": 20},
        {"key": "postgres_acid", "weight": 15},
        {"key": "kafka_reliability", "weight": 30},
        {"key": "contracts", "weight": 20},
        {"key": "observability", "weight": 15}
      ]'::jsonb,
     '2026-07-01T00:00:00Z');
