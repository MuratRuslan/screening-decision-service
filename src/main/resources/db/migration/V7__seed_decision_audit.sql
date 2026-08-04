-- One CREATED audit entry per seeded decision
INSERT INTO decision_audit (id, decision_id, action, actor, payload, created_at)
VALUES
    ('33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201', 'CREATED', 'screening-decision-service',
     '{"score": 92, "decision": "AUTO_APPROVE", "ruleSetVersion": "v1"}'::jsonb, '2026-06-04T12:00:00Z'),
    ('33333333-3333-3333-3333-333333333302', '22222222-2222-2222-2222-222222222202', 'CREATED', 'screening-decision-service',
     '{"score": 100, "decision": "AUTO_APPROVE", "ruleSetVersion": "v1"}'::jsonb, '2026-06-10T11:30:00Z'),
    ('33333333-3333-3333-3333-333333333303', '22222222-2222-2222-2222-222222222203', 'CREATED', 'screening-decision-service',
     '{"score": 67, "decision": "NEEDS_REVIEW", "ruleSetVersion": "v1"}'::jsonb, '2026-06-15T14:00:00Z'),
    ('33333333-3333-3333-3333-333333333304', '22222222-2222-2222-2222-222222222204', 'CREATED', 'screening-decision-service',
     '{"score": 12, "decision": "AUTO_REJECT", "ruleSetVersion": "v1"}'::jsonb, '2026-06-18T10:00:00Z'),
    ('33333333-3333-3333-3333-333333333305', '22222222-2222-2222-2222-222222222205', 'CREATED', 'screening-decision-service',
     '{"score": 67, "decision": "NEEDS_REVIEW", "ruleSetVersion": "v1"}'::jsonb, '2026-06-20T09:00:00Z'),
    ('33333333-3333-3333-3333-333333333306', '22222222-2222-2222-2222-222222222206', 'CREATED', 'screening-decision-service',
     '{"score": 92, "decision": "AUTO_APPROVE", "ruleSetVersion": "v1"}'::jsonb, '2026-06-22T15:00:00Z'),
    ('33333333-3333-3333-3333-333333333307', '22222222-2222-2222-2222-222222222207', 'CREATED', 'screening-decision-service',
     '{"score": 25, "decision": "AUTO_REJECT", "ruleSetVersion": "v1"}'::jsonb, '2026-06-25T10:00:00Z'),
    ('33333333-3333-3333-3333-333333333308', '22222222-2222-2222-2222-222222222208', 'CREATED', 'screening-decision-service',
     '{"score": 59, "decision": "NEEDS_REVIEW", "ruleSetVersion": "v1"}'::jsonb, '2026-06-28T13:00:00Z'),
    ('33333333-3333-3333-3333-333333333309', '22222222-2222-2222-2222-222222222209', 'CREATED', 'screening-decision-service',
     '{"score": 100, "decision": "AUTO_APPROVE", "ruleSetVersion": "v2"}'::jsonb, '2026-07-03T10:00:00Z'),
    ('33333333-3333-3333-3333-33333333330a', '22222222-2222-2222-2222-22222222220a', 'CREATED', 'screening-decision-service',
     '{"score": 52, "decision": "NEEDS_REVIEW", "ruleSetVersion": "v2"}'::jsonb, '2026-07-08T12:00:00Z'),
    ('33333333-3333-3333-3333-33333333330b', '22222222-2222-2222-2222-22222222220b', 'CREATED', 'screening-decision-service',
     '{"score": 0, "decision": "AUTO_REJECT", "ruleSetVersion": "v2"}'::jsonb, '2026-07-15T09:00:00Z'),
    ('33333333-3333-3333-3333-33333333330c', '22222222-2222-2222-2222-22222222220c', 'CREATED', 'screening-decision-service',
     '{"score": 85, "decision": "AUTO_APPROVE", "ruleSetVersion": "v2"}'::jsonb, '2026-07-20T14:30:00Z'),
    ('33333333-3333-3333-3333-33333333330d', '22222222-2222-2222-2222-22222222220d', 'CREATED', 'screening-decision-service',
     '{"score": 72, "decision": "NEEDS_REVIEW", "ruleSetVersion": "v2"}'::jsonb, '2026-07-25T11:15:00Z');

-- OVERRIDDEN audit entries for the two seed decisions that were manually overridden
INSERT INTO decision_audit (id, decision_id, action, actor, payload, created_at)
VALUES
    ('33333333-3333-3333-3333-333333333401', '22222222-2222-2222-2222-222222222205', 'OVERRIDDEN', 'api-client',
     '{"previousDecision": "NEEDS_REVIEW", "newDecision": "AUTO_APPROVE", "expectedVersion": 1, "reason": "Техническое интервью подтвердило уровень выше автооценки, решение утверждено вручную"}'::jsonb,
     '2026-06-21T10:00:00Z'),
    ('33333333-3333-3333-3333-333333333402', '22222222-2222-2222-2222-22222222220a', 'OVERRIDDEN', 'api-client',
     '{"previousDecision": "NEEDS_REVIEW", "newDecision": "AUTO_REJECT", "expectedVersion": 1, "reason": "После технического интервью принято решение отклонить кандидатуру"}'::jsonb,
     '2026-07-09T09:00:00Z');
