# Тестовое задание для позиции Java Senior

Внутреннее название позиции: **Java — главный программист**. По уровню ожиданий это **Senior**: разработчик, который способен проектировать сервис, готовый к промышленной эксплуатации, объяснять архитектурные решения и закрывать риски надёжности, данных и эксплуатации.

## Контекст

Вы реализуете **Screening Decision Service** — микросервис платформы CV-Scan, который принимает результат парсинга резюме и принимает автоматическое решение по кандидату.

Платформа состоит из нескольких сервисов:

```
cv-parser  →  [Kafka: cv.parsed]  →  screening-decision-service  →  [Kafka: screening.decision.created]  →  candidate-service
                                      │
                                      └→ [Kafka: screening.decision.dlq]
```

Screening Decision Service выполняет четыре задачи:
1. Потребляет события `cv.parsed` из Kafka.
2. Валидирует входящее событие, считает итоговый балл по активному rule-set и сохраняет решение в PostgreSQL.
3. Публикует событие `screening.decision.created` надёжно, без потери при сбое между БД и Kafka.
4. Предоставляет REST API для просмотра решений, rule-set и ручного изменения решения.

Задание проверяет senior-навыки: контрактное API REST/SOAP, JSON Schema, XML/XSD, семантические ресурсы, транзакции, идемпотентность, надёжную работу с Kafka, outbox/DLQ, конкурентные обновления, Java Virtual Threads, SQL/индексы, тестирование и эксплуатационную готовность.

---

## Технические требования

### Стек
- **Java 21**
- **Java Virtual Threads** для I/O-bound допроверок
- **Spring Boot 3.x** (Web, Data JPA, Validation, Kafka, Actuator)
- **PostgreSQL** (основное хранилище)
- **Apache Kafka** (consumer + producer)
- **Flyway** или **Liquibase** (миграции БД)
- **Gradle** (сборка)
- **JUnit 5 + Mockito** (unit-тесты)
- **Testcontainers** (интеграционные тесты — PostgreSQL + Kafka)
- **Springdoc OpenAPI** (документация, `/swagger-ui.html`)
- **Micrometer / Actuator Prometheus endpoint** для базовых метрик
- **JSON Schema** для валидации тела Kafka-сообщения
- **SOAP + XML/XSD** для адаптера проверки образования

### Что не требуется
- Фронтенд
- Авторизация / аутентификация
- Kubernetes/Helm
- Полноценная серверная часть распределённой трассировки

---

## Контракт API

REST API полностью описан в файле **`contract/openapi.yaml`**.

> **Важно.** Контракт зафиксирован — реализовать его строго. Не добавлять поля, не менять имена, не менять коды ошибок. Отклонение от контракта приравнивается к ошибке реализации.

Swagger UI после запуска: `http://localhost:8080/swagger-ui.html`

OpenAPI JSON во время работы сервиса: `http://localhost:8080/v3/api-docs`

---

## Задания

### Задание 1. REST API по контракту

Реализовать все эндпоинты из `contract/openapi.yaml`:

| Метод | Путь | Описание |
|------|------|----------|
| POST | `/api/v1/rule-sets` | Создание rule-set |
| GET | `/api/v1/rule-sets/active` | Активный rule-set по позиции |
| GET | `/api/v1/decisions` | Список решений с фильтрацией и пагинацией |
| GET | `/api/v1/decisions/{id}` | Одно решение |
| GET | `/api/v1/decisions/by-candidate/{candidateId}` | Последнее решение по кандидату |
| PATCH | `/api/v1/decisions/{id}/override` | Ручное изменение решения |
| GET | `/api/v1/decisions/{id}/audit` | Audit trail решения |

Поведение должно строго соответствовать контракту: HTTP-статусы, структура ответов, формат ошибок, имена полей.

**Правила REST API:**
- `POST /rule-sets` создаёт rule-set. Пара `position + version` уникальна.
- Активный rule-set для позиции — rule-set с максимальным `activeFrom <= now()`.
- `PATCH /override` требует header `expectedVersion`.
- Если `expectedVersion` не совпадает с текущей версией решения, вернуть `409` с кодом `VERSION_CONFLICT`.
- Каждое ручное изменение увеличивает `version`, выставляет `overridden=true`, сохраняет `overrideReason` и пишет запись в аудит.

**Критерии оценки:**
- [ ] Все эндпоинты работают и возвращают HTTP-статусы по контракту
- [ ] Формат ошибок (`ErrorResponse`) совпадает с контрактом, включая `details.pointer`
- [ ] Фильтрация, поиск, пагинация и сортировка работают в БД, не в памяти
- [ ] Проверка версии защищает от одновременного ручного изменения решения
- [ ] Swagger UI и `/v3/api-docs` доступны

---

### Задание 2. Механизм правил и скоринг

При получении события `cv.parsed` сервис должен выбрать активный rule-set по `position` и посчитать итоговый балл.

Rule-set содержит веса критериев:

```json
{
  "position": "java-senior",
  "version": "v1",
  "activeFrom": "2026-06-04T00:00:00Z",
  "minApproveScore": 80,
  "maxRejectScore": 45,
  "weights": [
    { "key": "java_spring", "weight": 25 },
    { "key": "postgres_acid", "weight": 20 },
    { "key": "kafka_reliability", "weight": 25 },
    { "key": "contracts", "weight": 15 },
    { "key": "observability", "weight": 15 }
  ]
}
```

Правила расчёта:
- `OK` даёт 100% веса критерия.
- `PARTIAL` даёт 50% веса критерия.
- `NO` даёт 0% веса критерия.
- Если критерий из rule-set отсутствует во входящем событии, он считается `NO`.
- Итоговый `score` округляется вниз до целого числа и ограничивается диапазоном `0..100`.
- Если `score >= minApproveScore`, решение `AUTO_APPROVE`.
- Если `score <= maxRejectScore`, решение `AUTO_REJECT`.
- Иначе решение `NEEDS_REVIEW`.

Каждый критерий из rule-set должен попасть в `ruleResults`:
- `PASS` для `OK`
- `WARN` для `PARTIAL`
- `FAIL` для `NO` или отсутствующего критерия

**Критерии оценки:**
- [ ] Rule-set выбирается по позиции и `activeFrom`
- [ ] Score считается детерминированно и покрыт unit-тестами
- [ ] Отсутствующие критерии учитываются как `NO`
- [ ] Решение выбирается по threshold из rule-set
- [ ] В `DecisionResponse.ruleResults` есть понятная диагностика по каждому правилу

---

### Задание 3. Kafka consumer: идемпотентность, повторы, DLQ

#### Consumer — топик `cv.parsed`

Сервис потребляет события `cv.parsed`. Структура события совместима с Candidate Service:

```json
{
  "eventId": "660e8400-e29b-41d4-a716-446655440000",
  "candidateId": "senior-asanov-bakyt",
  "parsedAt": "2026-05-20T09:00:00Z",
  "name": "Асанов Бакыт Эркинович",
  "position": "java-senior",
  "posLabel": "Java — главный программист",
  "email": "senior.asanov@email.com",
  "phone": "+996 700 111222",
  "city": "Бишкек",
  "telegram": "@asanov_arch",
  "totalExp": "~7 г.",
  "stack": "Java 17/21, Spring Boot, Kafka, PostgreSQL, Redis, Testcontainers, OpenTelemetry",
  "education": "КГТУ им. Раззакова, ИТ, 2018",
  "verdict": "FIT",
  "summary": "Senior backend developer with production Kafka, Postgres optimization and observability experience.",
  "criteria": [
    { "key": "java_spring", "result": "OK", "comment": "Java/Spring — 7 лет" },
    { "key": "postgres_acid", "result": "OK", "comment": "Postgres, транзакции, индексы, EXPLAIN" },
    { "key": "kafka_reliability", "result": "OK", "comment": "Kafka producer/consumer, retry, DLQ, idempotency" }
  ],
  "experience": [
    { "period": "2021-04 — н.в.", "company": "GovTech Platform", "title": "Senior Java Developer", "duration": "~5 г." }
  ],
  "questions": [
    "Как проектировали идемпотентность Kafka consumer при нескольких репликах?"
  ]
}
```

**Идемпотентность:**
- Повторное событие с тем же `candidateId + parsedAt` не должно создавать второе решение.
- Если повторное событие пришло после уже опубликованного `screening.decision.created`, повторная публикация не допускается.
- Рекомендуется обеспечить идемпотентность уникальным индексом в БД, а не только проверкой `exists`.

**Retry/DLQ:**
- Для временных технических ошибок допустимы повторы с задержкой между попытками.
- Для невалидного бизнес-события после исчерпания повторов нужно публиковать сообщение в `screening.decision.dlq`.
- DLQ-событие должно содержать исходное тело сообщения, `errorCode`, `errorMessage`, `failedAt`.
- Невалидное событие не должно блокировать обработку следующих сообщений.
- Валидация входящего сообщения должна выполняться по `contract/json-schema/cv-parsed.schema.json` до бизнес-обработки.

**Критерии оценки:**
- [ ] Consumer читает `cv.parsed` и создаёт решение
- [ ] Повтор `candidateId + parsedAt` не создаёт дубль и не публикует второе событие результата
- [ ] Невалидное событие уходит в `screening.decision.dlq`
- [ ] Ошибки валидации по JSON Schema диагностируются с JSON Pointer
- [ ] Повторы и задержка между попытками настроены явно
- [ ] Consumer безопасен при нескольких репликах сервиса

---

### Задание 4. Kafka producer и outbox

#### Producer — топик `screening.decision.created`

После успешного создания решения опубликовать событие:

```json
{
  "eventId": "770e8400-e29b-41d4-a716-446655440000",
  "decisionId": "550e8400-e29b-41d4-a716-446655440000",
  "candidateId": "senior-asanov-bakyt",
  "parsedAt": "2026-05-20T09:00:00Z",
  "position": "java-senior",
  "decision": "AUTO_APPROVE",
  "score": 87,
  "ruleSetVersion": "v1",
  "decidedAt": "2026-06-04T12:00:00Z"
}
```

Требования:
- Использовать транзакционный outbox: создание решения и outbox-записи должны быть в одной DB-транзакции.
- Отдельный публикатор должен читать outbox и публиковать события в Kafka.
- После успешной публикации outbox-запись помечается как `SENT`.
- Повторный запуск публикатора не должен публиковать уже отправленные события.
- Имена Kafka-топиков и bootstrap servers настраиваются только через свойства приложения.

**Критерии оценки:**
- [ ] Outbox-таблица есть в миграциях
- [ ] Решение и outbox-запись создаются атомарно
- [ ] Публикатор отправляет `screening.decision.created`
- [ ] Отправленные события не публикуются повторно
- [ ] Ошибки публикации не теряют событие, а оставляют его для повторной отправки

---

### Задание 5. Миграции БД и транзакции

Использовать Flyway или Liquibase — без `spring.jpa.hibernate.ddl-auto=create/update`.

Минимальный набор таблиц:

| Таблица | Назначение |
|---------|------------|
| `rule_sets` | Rule-set по позиции и версии |
| `screening_decisions` | Итоговые решения |
| `decision_audit` | Аудит создания, replay/update и ручного изменения |
| `outbox_events` | Transactional outbox |

Минимальные индексы:
- `rule_sets(position, version)` unique
- `rule_sets(position, active_from)`
- `screening_decisions(candidate_id, parsed_at)` unique
- `screening_decisions(position, decision, decided_at)`
- `screening_decisions(score)`
- `outbox_events(status, created_at)`
- `decision_audit(decision_id, created_at)`

Seed-данные:
- Минимум 2 rule-set для `java-senior`: `v1` и `v2` с разным `activeFrom`.
- Минимум 10 решений с разными `decision`, `score`, `sourceVerdict`.

**Критерии оценки:**
- [ ] Миграции применяются при старте
- [ ] `ddl-auto` не `create` и не `update`
- [ ] Индексы и unique constraints созданы миграциями
- [ ] Транзакционные границы понятны и документированы
- [ ] Есть защита от race condition при параллельной обработке одного кандидата

---

### Задание 6. Наблюдаемость и эксплуатация

Реализовать:
- Actuator health endpoint.
- Prometheus endpoint `/actuator/prometheus`.
- Логирование ключевых операций: consume, decision created, duplicate ignored, DLQ published, outbox sent, ручное изменение.
- Correlation/MDC минимум по `candidateId` и `eventId` для Kafka-сообщений.
- README с описанием запуска, архитектурных решений и известных компромиссов.
- `docs/ADR-001.md` или раздел README: почему выбран outbox, как устроена идемпотентность, как обработаны повторы/DLQ и одновременное ручное изменение решения.

**Критерии оценки:**
- [ ] Health и Prometheus endpoints доступны
- [ ] Логи позволяют найти обработку конкретного `candidateId`
- [ ] Ошибки Kafka/DB не скрываются и диагностируются
- [ ] Архитектурные решения описаны коротко и по делу

---

### Задание 7. Контрактная валидация, SOAP/XML/XSD и семантические ресурсы

Реализовать модуль контрактной валидации и нормализации входящих данных.

#### JSON Schema

- Входящее Kafka-событие `cv.parsed` валидировать по `contract/json-schema/cv-parsed.schema.json`.
- Ошибки валидации отправлять в `screening.decision.dlq`.
- В диагностике ошибки указывать JSON Pointer до проблемного поля, например `/criteria/0/key`.
- Не заменять JSON Schema ручными `if` по строкам; допускается дополнительная бизнес-валидация после проверки по схеме.

#### SOAP/XML/XSD

В рамках допроверок реализовать SOAP/XML adapter для проверки образования:

- XSD контракта лежит в `contract/soap/education-verification.xsd`.
- Реальный внешний SOAP-сервис делать не нужно: можно поднять локальную заглушку или in-memory endpoint.
- XML request/response должны валидироваться по XSD.
- Для диагностики XML-ошибок использовать XPath или понятный path до элемента.
- Результат SOAP-проверки должен попадать в результат допроверок (`ruleResults` или данные аудита).

#### Семантические ресурсы

Каталог критериев лежит в `semantic/criteria-catalog.json`.

Требования:
- Нормализовать `criteria.key` из события через синонимы каталога.
- Rule-set должен ссылаться только на канонический `id` из каталога.
- Неизвестный ключ критерия не должен молча игнорироваться: он должен попасть в диагностику/аудит или DLQ, в зависимости от выбранной и описанной политики.
- Версию семантического каталога сохранить в решении или данных аудита.
- В README описать, как будет обновляться каталог без поломки существующих решений.

**Критерии оценки:**
- [ ] Тело Kafka-сообщения валидируется по JSON Schema
- [ ] JSON Schema ошибки содержат JSON Pointer
- [ ] SOAP/XML adapter использует валидацию по XSD
- [ ] XML-диагностика даёт понятный путь/XPath
- [ ] Ключи критериев нормализуются через семантический каталог
- [ ] Версия семантического каталога сохранена и описана в README

---

### Задание 8. Virtual Threads для допроверок

Перед сохранением решения сервис должен выполнить параллельные I/O-bound проверки кандидата. Реальные внешние сервисы реализовывать не нужно: сделайте локальные Spring-сервисы/адаптеры, которые имитируют задержку и результат проверки.

Минимальные проверки:
- `duplicate-profile-check` — поиск похожего профиля по email/phone.
- `sanctions-check` — проверка имени по условному списку блокировок.
- `education-format-check` — SOAP/XML проверка поля `education` через XSD-контракт из `contract/soap/education-verification.xsd`.

Требования:
- Использовать **Java 21 virtual threads**, например `Executors.newVirtualThreadPerTaskExecutor()` или Spring `VirtualThreadTaskExecutor`.
- Проверки должны выполняться параллельно, а не последовательно.
- Для каждой проверки должен быть тайм-аут.
- Общий результат допроверок должен попасть в `ruleResults` или данные аудита так, чтобы ревьюер видел, какие проверки прошли, завершились по тайм-ауту или вернули предупреждение.
- Ошибка одной допроверки не должна ронять весь consumer, если это не критичная бизнес-ошибка.
- Нельзя создавать неконтролируемое число параллельных обращений: опишите в README, как ограничиваете нагрузку на внешние зависимости (semaphore, rate limit, bounded queue или другой подход).
- В README объяснить, почему здесь выбраны virtual threads, и где они не помогут: CPU-bound задачи, pinning, блокировки внутри synchronized/native calls, ограничения connection pool.

**Критерии оценки:**
- [ ] Используется Java 21 virtual threads API или Spring executor поверх virtual threads
- [ ] Допроверки выполняются параллельно и имеют тайм-аут
- [ ] Ошибки/тайм-ауты отдельных проверок диагностируются и не блокируют Kafka consumer
- [ ] Есть контроль числа параллельных обращений и нагрузки на внешние зависимости
- [ ] В README объяснены компромиссы virtual threads
- [ ] Есть unit/integration-тест, доказывающий параллельное выполнение или поведение при тайм-ауте

---

### Задание 9. Тестирование

**Unit-тесты:**
- Score calculation: OK/PARTIAL/NO, отсутствующий критерий, threshold boundaries
- Rule-set selection по `activeFrom`
- Идемпотентность consumer
- Конфликт версий при ручном изменении
- Публикатор outbox не отправляет `SENT` повторно
- Допроверки на virtual threads: параллельность, тайм-аут, частичный сбой
- JSON Schema validation и нормализация через семантический каталог
- SOAP/XML/XSD validation для проверки образования

**Интеграционные тесты (Testcontainers):**
- PostgreSQL и Kafka реальные, не H2 и не embedded Kafka
- `cv.parsed` → решение создано → outbox event опубликован в `screening.decision.created`
- Повтор `cv.parsed` → дубля решения и дубля result event нет
- Невалидное событие → запись в `screening.decision.dlq`, следующие события продолжают обрабатываться
- Тайм-аут допроверки не блокирует обработку следующего Kafka-события
- Невалидное JSON-сообщение уходит в DLQ с JSON Pointer
- Невалидный SOAP/XML response диагностируется и не ломает обработку следующего сообщения
- `PATCH /override` с корректной версией → `200`, версия увеличена, аудит записан
- `PATCH /override` со старой версией → `409 VERSION_CONFLICT`
- `GET /decisions` с комбинацией фильтров и сортировки возвращает корректные данные

**Критерии оценки:**
- [ ] Unit-тесты покрывают бизнес-логику без Spring context
- [ ] Интеграционные тесты поднимают PostgreSQL и Kafka через Testcontainers
- [ ] Проверены пограничные случаи и сценарии ошибок
- [ ] Тесты изолированы и не зависят от порядка запуска

---

## Тестовые события

В папке `test-events/` подготовлены данные для ручной проверки:

| Файл | Описание |
|------|----------|
| `cv-parsed-sample.json` | Одно событие для быстрой проверки consumer |
| `cv-parsed-bulk.ndjson` | 8 строк: 6 валидных уникальных кандидатов (3 FIT, 2 PARTIAL, 1 NO_FIT), 1 невалидное бизнес-событие для DLQ, 1 намеренный дубль первой строки |

Публикация через **kcat**:

```bash
# Одно событие
printf '%s\n' "$(tr -d '\n' < test-events/cv-parsed-sample.json)" \
  | kcat -P -b localhost:9092 -t cv.parsed -l

# Пакет событий
kcat -P -b localhost:9092 -t cv.parsed -l test-events/cv-parsed-bulk.ndjson
```

Публикация через **kafka-console-producer**:

```bash
# Одно событие
tr -d '\n' < test-events/cv-parsed-sample.json \
  | kafka-console-producer.sh --bootstrap-server localhost:9092 --topic cv.parsed

# Пакет событий
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic cv.parsed \
  < test-events/cv-parsed-bulk.ndjson
```

---

## Ожидаемая структура проекта

Структура может отличаться, если решение архитектурно обосновано, но разделение ответственности обязательно:

```
src/
├── main/
│   ├── java/kg/tunduk/cvscan/screening/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── scoring/
│   │   ├── messaging/
│   │   ├── outbox/
│   │   ├── repository/
│   │   ├── model/
│   │   ├── dto/
│   │   ├── exception/
│   │   └── ScreeningDecisionApplication.java
│   └── resources/
│       ├── db/migration/
│       └── application.yml
└── test/
    ├── java/kg/tunduk/cvscan/screening/
    │   ├── service/
    │   ├── scoring/
    │   └── integration/
    └── resources/
        └── application-test.yml
```

---

## Что оценивается

### Обязательно
- Соответствие REST API контракту (`contract/openapi.yaml`)
- Механизм правил и объяснимые `ruleResults`
- Kafka consumer + DLQ + повторы
- Транзакционный outbox для producer
- Идемпотентность и защита от race condition
- Java 21 virtual threads для параллельных допроверок
- JSON Schema validation, SOAP/XML/XSD и семантические ресурсы
- Миграции БД и индексы
- Тесты unit + интеграционные с Testcontainers
- Наблюдаемость: health, prometheus, структурные логи/MDC
- Документированные архитектурные решения

### Будет плюсом
- `SELECT ... FOR UPDATE SKIP LOCKED` или другой корректный механизм конкурентной обработки outbox
- Пакетная публикация outbox с обратным давлением
- JSON Pointer в диагностике ошибок валидации
- Поддержка нескольких версий JSON Schema/XSD с обратной совместимостью
- `@EntityGraph` / fetch strategy без N+1
- OpenTelemetry trace/span attributes для Kafka и REST
- K6-скрипт быстрой проверки и нагрузки для эндпоинтов чтения

---

## Критерии уровня

| Критерий | Middle | Senior | Strong Senior |
|----------|--------|--------|---------------|
| Контракт | Реализует основной успешный сценарий | Реализует строго и диагностирует ошибки | Учитывает жизненный цикл контракта и обратную совместимость |
| Kafka | Consumer/producer работают | Повторы, DLQ, идемпотентность, outbox | Обоснованы порядок обработки, партиции и обратное давление |
| Virtual Threads | Использует executor без объяснения | Применяет для I/O-bound проверок с тайм-аутом | Объясняет pinning, пулы, контроль параллелизма и сценарии сбоев |
| БД | Миграции и JPA | Транзакции, индексы, защита от гонок | SQL-оптимизация, объяснимость, стратегия блокировок |
| Архитектура | Слои разделены | Решения документированы, границы ясны | Компромиссы и сценарии сбоев явно описаны |
| Тесты | Основные сценарии | Сценарии ошибок + Testcontainers | Конкурентные сценарии и устойчивость |
| Эксплуатация | Запускается локально | Health, metrics, логи | Доменные метрики, трассировка, инструкция для дежурного |

---

## Время выполнения

Рекомендуемое время: **16–24 часа**

Дедлайн сдачи: **7 дней** с момента получения задания

---

## Формат сдачи

1. **Репозиторий GitHub/GitLab** (публичный или invite)
2. **README.md** с:
   - Инструкциями по запуску
   - Описанием архитектуры
   - Описанием идемпотентности, повторов/DLQ, outbox и одновременного ручного изменения решения
   - Что не успели и почему
3. **Коммиты** — осмысленная история, не один «done»

---

## Запуск и проверка

```bash
# Сборка и тесты
./gradlew build

# Только тесты
./gradlew test

# Запуск инфраструктуры, если есть Docker Compose
docker compose up -d

# Запуск приложения
./gradlew bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

Actuator health: `http://localhost:8080/actuator/health`

Prometheus metrics: `http://localhost:8080/actuator/prometheus`

---

## Вопросы

Если возникли вопросы по заданию — задавайте до начала. Если в контракте или требованиях есть неоднозначность, зафиксируйте её в README и явно опишите принятое решение.
