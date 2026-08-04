-- Seed decisions scored against rule-set v1 (active 2026-06-04 .. 2026-06-30)
INSERT INTO screening_decisions
    (id, candidate_id, parsed_at, name, email, position, source_verdict, decision, score,
     rule_set_version, rule_results, semantic_catalog_version, decided_at, version, overridden, override_reason)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'senior-asanov-bakyt', '2026-05-20T09:00:00Z',
     'Асанов Бакыт Эркинович', 'senior.asanov@email.com', 'java-senior', 'FIT', 'AUTO_APPROVE', 92, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: Java/Spring - 7 лет"},
        {"key": "postgres_acid", "result": "PASS", "points": 20, "reason": "OK: транзакции, индексы, EXPLAIN"},
        {"key": "kafka_reliability", "result": "PASS", "points": 25, "reason": "OK: producer/consumer, retry, DLQ"},
        {"key": "contracts", "result": "PASS", "points": 15, "reason": "OK: OpenAPI, JSON Schema"},
        {"key": "observability", "result": "WARN", "points": 7, "reason": "PARTIAL: Prometheus есть, tracing базово"}
      ]'::jsonb, '2026.06', '2026-06-04T12:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222202', 'senior-nazarova-aigul', '2026-06-05T10:00:00Z',
     'Назарова Айгуль Бекболотовна', 'aigul.nazarova@email.com', 'java-senior', 'FIT', 'AUTO_APPROVE', 100, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: 8 лет production Java"},
        {"key": "postgres_acid", "result": "PASS", "points": 20, "reason": "OK: SQL-оптимизация, партиционирование"},
        {"key": "kafka_reliability", "result": "PASS", "points": 25, "reason": "OK: outbox, идемпотентность"},
        {"key": "contracts", "result": "PASS", "points": 15, "reason": "OK: контрактное тестирование"},
        {"key": "observability", "result": "PASS", "points": 15, "reason": "OK: Grafana, OpenTelemetry"}
      ]'::jsonb, '2026.06', '2026-06-10T11:30:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222203', 'senior-tashiev-arstan', '2026-06-12T08:15:00Z',
     'Ташиев Арстан Куанышевич', 'arstan.tashiev@email.com', 'java-senior', 'PARTIAL', 'NEEDS_REVIEW', 67, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: 5 лет Java/Spring"},
        {"key": "postgres_acid", "result": "WARN", "points": 10, "reason": "PARTIAL: базовый SQL, без EXPLAIN"},
        {"key": "kafka_reliability", "result": "PASS", "points": 25, "reason": "OK: consumer groups, retry"},
        {"key": "contracts", "result": "WARN", "points": 7, "reason": "PARTIAL: OpenAPI без JSON Schema"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: метрики отсутствуют"}
      ]'::jsonb, '2026.06', '2026-06-15T14:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222204', 'senior-orozova-jamilya', '2026-06-16T09:45:00Z',
     'Орозова Жамиля Талантовна', 'jamilya.orozova@email.com', 'java-senior', 'NO_FIT', 'AUTO_REJECT', 12, 'v1',
     '[
        {"key": "java_spring", "result": "FAIL", "points": 0, "reason": "FAIL: нет опыта Spring"},
        {"key": "postgres_acid", "result": "FAIL", "points": 0, "reason": "FAIL: нет опыта Postgres"},
        {"key": "kafka_reliability", "result": "WARN", "points": 12, "reason": "PARTIAL: только базовый producer"},
        {"key": "contracts", "result": "FAIL", "points": 0, "reason": "FAIL: нет опыта контрактной разработки"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: нет опыта"}
      ]'::jsonb, '2026.06', '2026-06-18T10:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222205', 'senior-bekov-emil', '2026-06-19T13:20:00Z',
     'Беков Эмиль Жумабекович', 'emil.bekov@email.com', 'java-senior', 'PARTIAL', 'AUTO_APPROVE', 67, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: 6 лет Java/Spring"},
        {"key": "postgres_acid", "result": "WARN", "points": 10, "reason": "PARTIAL: индексы без анализа планов"},
        {"key": "kafka_reliability", "result": "PASS", "points": 25, "reason": "OK: DLQ, retry с backoff"},
        {"key": "contracts", "result": "WARN", "points": 7, "reason": "PARTIAL: контракт не версионируется"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: метрики не настроены"}
      ]'::jsonb, '2026.06', '2026-06-20T09:00:00Z', 2, TRUE,
     'Техническое интервью подтвердило уровень выше автооценки, решение утверждено вручную'),

    ('22222222-2222-2222-2222-222222222206', 'senior-moldalieva-aichurok', '2026-06-21T11:00:00Z',
     'Молдалиева Айчурок Нурлановна', 'aichurok.moldalieva@email.com', 'java-senior', 'FIT', 'AUTO_APPROVE', 92, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: архитектор, 9 лет"},
        {"key": "postgres_acid", "result": "PASS", "points": 20, "reason": "OK: репликация, тюнинг"},
        {"key": "kafka_reliability", "result": "PASS", "points": 25, "reason": "OK: exactly-once семантика"},
        {"key": "contracts", "result": "WARN", "points": 7, "reason": "PARTIAL: только REST, без SOAP"},
        {"key": "observability", "result": "PASS", "points": 15, "reason": "OK: distributed tracing"}
      ]'::jsonb, '2026.06', '2026-06-22T15:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222207', 'senior-sydykov-nurlan', '2026-06-24T08:30:00Z',
     'Сыдыков Нурлан Асанбекович', 'nurlan.sydykov@email.com', 'java-senior', 'NO_FIT', 'AUTO_REJECT', 25, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: крепкий middle+"},
        {"key": "postgres_acid", "result": "FAIL", "points": 0, "reason": "FAIL: нет продакшн опыта с БД"},
        {"key": "kafka_reliability", "result": "FAIL", "points": 0, "reason": "FAIL: не работал с Kafka"},
        {"key": "contracts", "result": "FAIL", "points": 0, "reason": "FAIL: нет контрактной разработки"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: нет опыта"}
      ]'::jsonb, '2026.06', '2026-06-25T10:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-222222222208', 'senior-toktosunova-elvira', '2026-06-27T09:10:00Z',
     'Токтосунова Эльвира Максатовна', 'elvira.toktosunova@email.com', 'java-senior', 'PARTIAL', 'NEEDS_REVIEW', 59, 'v1',
     '[
        {"key": "java_spring", "result": "PASS", "points": 25, "reason": "OK: 5 лет Java/Spring"},
        {"key": "postgres_acid", "result": "PASS", "points": 20, "reason": "OK: индексы, транзакции"},
        {"key": "kafka_reliability", "result": "FAIL", "points": 0, "reason": "FAIL: только теория"},
        {"key": "contracts", "result": "WARN", "points": 7, "reason": "PARTIAL: без JSON Schema"},
        {"key": "observability", "result": "WARN", "points": 7, "reason": "PARTIAL: только логи"}
      ]'::jsonb, '2026.06', '2026-06-28T13:00:00Z', 1, FALSE, NULL),

-- Seed decisions scored against rule-set v2 (active from 2026-07-01)
    ('22222222-2222-2222-2222-222222222209', 'senior-abdykadyrov-marat', '2026-07-02T09:00:00Z',
     'Абдыкадыров Марат Талантбекович', 'marat.abdykadyrov@email.com', 'java-senior', 'FIT', 'AUTO_APPROVE', 100, 'v2',
     '[
        {"key": "java_spring", "result": "PASS", "points": 20, "reason": "OK: 10 лет Java/Spring"},
        {"key": "postgres_acid", "result": "PASS", "points": 15, "reason": "OK: полный ACID-разбор"},
        {"key": "kafka_reliability", "result": "PASS", "points": 30, "reason": "OK: outbox, идемпотентность, DLQ"},
        {"key": "contracts", "result": "PASS", "points": 20, "reason": "OK: OpenAPI, XSD, JSON Schema"},
        {"key": "observability", "result": "PASS", "points": 15, "reason": "OK: SLO, алертинг"}
      ]'::jsonb, '2026.06', '2026-07-03T10:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-22222222220a', 'senior-usenova-cholpon', '2026-07-06T10:30:00Z',
     'Усенова Чолпон Бекназаровна', 'cholpon.usenova@email.com', 'java-senior', 'PARTIAL', 'AUTO_REJECT', 52, 'v2',
     '[
        {"key": "java_spring", "result": "PASS", "points": 20, "reason": "OK: 4 года Java/Spring"},
        {"key": "postgres_acid", "result": "WARN", "points": 7, "reason": "PARTIAL: без EXPLAIN ANALYZE"},
        {"key": "kafka_reliability", "result": "WARN", "points": 15, "reason": "PARTIAL: без DLQ"},
        {"key": "contracts", "result": "WARN", "points": 10, "reason": "PARTIAL: контракт не зафиксирован"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: метрики отсутствуют"}
      ]'::jsonb, '2026.06', '2026-07-08T12:00:00Z', 2, TRUE,
     'После технического интервью принято решение отклонить кандидатуру'),

    ('22222222-2222-2222-2222-22222222220b', 'senior-imanalieva-begimai', '2026-07-12T08:00:00Z',
     'Иманалиева Бегимай Уланбековна', 'begimai.imanalieva@email.com', 'java-senior', 'NO_FIT', 'AUTO_REJECT', 0, 'v2',
     '[
        {"key": "java_spring", "result": "FAIL", "points": 0, "reason": "FAIL: не соответствует профилю"},
        {"key": "postgres_acid", "result": "FAIL", "points": 0, "reason": "FAIL: не соответствует профилю"},
        {"key": "kafka_reliability", "result": "FAIL", "points": 0, "reason": "FAIL: не соответствует профилю"},
        {"key": "contracts", "result": "FAIL", "points": 0, "reason": "FAIL: не соответствует профилю"},
        {"key": "observability", "result": "FAIL", "points": 0, "reason": "FAIL: не соответствует профилю"}
      ]'::jsonb, '2026.06', '2026-07-15T09:00:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-22222222220c', 'senior-dzhaparov-ruslan', '2026-07-18T09:30:00Z',
     'Джапаров Руслан Бакытович', 'ruslan.dzhaparov@email.com', 'java-senior', 'FIT', 'AUTO_APPROVE', 85, 'v2',
     '[
        {"key": "java_spring", "result": "PASS", "points": 20, "reason": "OK: 7 лет Java/Spring"},
        {"key": "postgres_acid", "result": "PASS", "points": 15, "reason": "OK: тюнинг запросов"},
        {"key": "kafka_reliability", "result": "WARN", "points": 15, "reason": "PARTIAL: без exactly-once"},
        {"key": "contracts", "result": "PASS", "points": 20, "reason": "OK: строгое соответствие контракту"},
        {"key": "observability", "result": "PASS", "points": 15, "reason": "OK: Prometheus, Grafana"}
      ]'::jsonb, '2026.06', '2026-07-20T14:30:00Z', 1, FALSE, NULL),

    ('22222222-2222-2222-2222-22222222220d', 'senior-kadyrova-nurgul', '2026-07-22T10:00:00Z',
     'Кадырова Нургуль Эмильевна', 'nurgul.kadyrova@email.com', 'java-senior', 'PARTIAL', 'NEEDS_REVIEW', 72, 'v2',
     '[
        {"key": "java_spring", "result": "WARN", "points": 10, "reason": "PARTIAL: 3 года, junior+ уровень"},
        {"key": "postgres_acid", "result": "PASS", "points": 15, "reason": "OK: транзакции, блокировки"},
        {"key": "kafka_reliability", "result": "PASS", "points": 30, "reason": "OK: consumer rebalancing, DLQ"},
        {"key": "contracts", "result": "WARN", "points": 10, "reason": "PARTIAL: OpenAPI без JSON Schema"},
        {"key": "observability", "result": "WARN", "points": 7, "reason": "PARTIAL: базовые логи"}
      ]'::jsonb, '2026.06', '2026-07-25T11:15:00Z', 1, FALSE, NULL);
