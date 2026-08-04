# Screening Decision Service

Kafka-driven microservice that consumes parsed-resume events (`cv.parsed`), scores a
candidate against the active rule-set for their position, persists the decision, and
publishes the result (`screening.decision.created`) via a transactional outbox. Exposes a
REST API for rule-sets, decisions, and manual override.

```
cv-parser  →  [Kafka: cv.parsed]  →  screening-decision-service  →  [Kafka: screening.decision.created]  →  candidate-service
                                      │
                                      └→ [Kafka: screening.decision.dlq]
```

Built for the `java-senior` take-home task in [`java-senior/TASK.md`](java-senior/TASK.md).
That folder (contract OpenAPI/JSON Schema/XSD, semantic catalog, sample events) is the
fixed, authoritative contract and is **not modified** by this implementation — the app
copies those files into `src/main/resources` at build time and loads them from the
classpath, so the built jar is self-contained. `ContractResourcesConsistencyTest` fails
the build if the copies ever drift from the originals.

## Stack

Java 21 (virtual threads), Spring Boot 3.5.3 (Web, Data JPA, Validation, Kafka, Actuator),
PostgreSQL, Apache Kafka, Flyway, Gradle, JUnit 5 + Mockito, Testcontainers, springdoc
OpenAPI, Micrometer/Prometheus, `com.networknt:json-schema-validator`. No SOAP framework
and no JAXB — see [§ SOAP/XML adapter](#soapxml-education-adapter).

**Deviation from the scaffold, documented up front:** the Spring Initializr-generated
project this was built from pinned Spring Boot **4.1.0** with non-standard/preview
artifact ids. TASK.md's stated stack is "Spring Boot 3.x", so the build was deliberately
downgraded to **3.5.3** with the real, standard artifact ids
(`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-kafka`,
`flyway-core`, ...). `ext['testcontainers.version'] = '2.0.5'` in `build.gradle` pins
Testcontainers explicitly, since Boot 3.5.3's managed BOM would otherwise resolve an
older 1.x line incompatible with the already-present `TestcontainersConfiguration`
(`org.testcontainers.kafka.KafkaContainer` / `org.testcontainers.postgresql.PostgreSQLContainer`
package layout).

## Running locally

```bash
docker compose up -d          # Postgres 16 + single-node Kafka (KRaft, port 9092)
./gradlew bootRun             # applies Flyway migrations on startup
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`

```bash
./gradlew build     # compile + full test suite
./gradlew test      # tests only
```

Publish the manual test events (from `java-senior/test-events/`) with `kcat`:

```bash
printf '%s\n' "$(tr -d '\n' < java-senior/test-events/cv-parsed-sample.json)" \
  | kcat -P -b localhost:9092 -t cv.parsed -l

kcat -P -b localhost:9092 -t cv.parsed -l java-senior/test-events/cv-parsed-bulk.ndjson
```

`cv-parsed-bulk.ndjson` has 6 valid unique candidates, 1 row that fails JSON Schema
validation (empty `name`, invalid `email` — routed to `screening.decision.dlq` with JSON
Pointer diagnostics), and 1 exact duplicate of the first row (same `candidateId` +
`parsedAt` — silently ignored, no second decision or second `screening.decision.created`).

> **Verified with Docker:** this implementation was originally built in a sandbox without
> Docker (only the 68 Docker-independent unit tests could run there). It has since been
> re-verified with Docker available: `./gradlew test` passes **79/79**, including all five
> Testcontainers integration test classes and the app's own context-load test against real
> Postgres and Kafka. `docker compose up -d && ./gradlew bootRun` was also run directly —
> publishing the sample event produced a decision, an outbox row that reached `SENT`, and a
> real `screening.decision.created` message on the broker with a matching `decisionId`/
> `score`; `/actuator/health` and `/actuator/prometheus` responded correctly.
>
> Three real bugs were caught by this end-to-end verification and are now fixed:
> 1. The V6 seed migration used the wrong JSON enum values for `RuleEvaluation.result`
>    (`NO`/`PARTIAL`, copied from the unrelated `CriterionResult` enum, instead of
>    `RuleResult`'s actual `FAIL`/`WARN`) — this crashed JSON deserialization for 11 of the
>    13 seeded decisions the moment they were read back via JPA.
> 2. `PATCH /override`'s response showed the *pre*-override `version` because the entity's
>    in-memory `@Version` field only updates once Hibernate actually flushes the UPDATE,
>    which by default happens at transaction commit — after the response DTO had already
>    been built. Fixed by flushing explicitly before building the response.
> 3. `docker-compose.yml`'s Kafka service used a dual-listener config that the official
>    `apache/kafka` image rejected at startup (`advertised.listeners` ended up containing
>    the non-routable `0.0.0.0`); replaced with the simpler single-listener KRaft pattern
>    from Kafka's own quickstart compose example.

`kafka-console-producer.sh`/`.bat` work too — note it sends each **line** of stdin as a
separate message, so multi-line pretty-printed JSON must be collapsed to one line first
(exactly what the `tr -d '\n'` above does); skipping that turns one valid event into many
garbage ones. The service handles that gracefully either way (every malformed line lands
in the DLQ individually, without stalling the partition), but it's not what you want for a
manual smoke test.

## Configuration

All Kafka topic names and `bootstrap-servers` come only from properties (`application.yml`
/ env), never hardcoded, per TASK.md.

| Property | Meaning | Default |
|---|---|---|
| `spring.kafka.bootstrap-servers` | Kafka bootstrap | `localhost:9092` |
| `app.kafka.topics.cv-parsed` / `decision-created` / `decision-dlq` | Topic names | `cv.parsed` / `screening.decision.created` / `screening.decision.dlq` |
| `app.kafka.consumer.retry.*` | Exponential backoff for transient consumer errors | 500ms initial, ×2, capped at 5s, gives up after 30s |
| `app.outbox.poll-interval-ms` / `batch-size` / `send-timeout-ms` | Outbox publisher tick, claim batch size, per-send timeout | 1000 / 50 / 5000 |
| `app.precheck.max-concurrent-calls` | Semaphore permits bounding concurrent precheck "calls" | 8 |
| `app.precheck.timeout-ms` | Per-check timeout | 2000 |
| `app.semantic.unknown-key-policy` | `AUDIT` (default, continue + record) or `DLQ` (reject whole event) | `AUDIT` |

## Architecture & key decisions

Full detail in [`docs/ADR-001.md`](docs/ADR-001.md). Summary:

### Idempotency

`screening_decisions(candidate_id, parsed_at)` has a **unique DB constraint** — this, not
an application-level `exists()` check, is the actual idempotency guard, because it's the
only thing that's safe across concurrent consumer replicas (an exists-then-insert check
has a race window between the two statements). The consumer inserts the decision and
**flushes immediately** so a duplicate surfaces as a `DataIntegrityViolationException`
right there; the caller catches it, logs "duplicate ignored", and returns — no audit row,
no outbox row, no error. Only on success are the audit row and outbox row written, in the
same transaction as the decision insert.

### Retry & DLQ

Transient errors (DB/Kafka connectivity blips) retry with `ExponentialBackOff` (bounded by
`max-elapsed-time-ms`) via Spring Kafka's `DefaultErrorHandler` — blocking/synchronous
retry, not a separate retry-topic pipeline, which is enough for this scope. Validation and
business errors (`NonRetryableEventException`: malformed JSON, JSON Schema violations,
unknown rule-set, optionally unknown criterion keys) are registered as non-retryable and
skip straight to recovery. Both paths funnel through one `DlqPublishingRecoverer`,
guaranteeing a uniform DLQ envelope (`originalPayload`, `errorCode`, `errorMessage`,
`details[]` with JSON Pointers where applicable, `failedAt`, `sourceTopic`, `partition`,
`offset`) and guaranteeing a bad message never blocks the next one on the partition.

### Transactional outbox

Decision insert + `CREATED` audit insert + `NEW` outbox insert are one DB transaction.
`OutboxPublisher` claims a batch with a native `SELECT ... FOR UPDATE SKIP LOCKED` query
(safe for multiple app instances) and sends each row **inside that same claiming
transaction** — see the ADR for why a separate `REQUIRES_NEW` transaction per row would
actually create a lock/deadlock risk instead of being "more correct". Delivery is
**at-least-once**: a crash between the Kafka ack and the transaction commit re-sends the
row on the next poll; the event's stable `eventId` is what lets a downstream consumer
dedupe.

### Concurrent manual override

`PATCH /decisions/{id}/override` checks concurrency twice: an explicit comparison against
the `expectedVersion` header (gives the exact contract-format `409 VERSION_CONFLICT`
message for the common "stale client" case), and JPA's own `@Version` column on
`ScreeningDecisionEntity` (real `WHERE id=? AND version=?` compare-and-swap at commit,
closing the race for two genuinely concurrent requests). Both map to the same 409.

### Semantic catalog

`semantic/criteria-catalog.json` (`version: "2026.06"`) maps synonym aliases to canonical
criterion ids. Incoming `criteria[].key` values are normalized through it before scoring;
rule-sets may only reference canonical ids (`POST /rule-sets` returns 400 if not). An
unmapped key is **never silently dropped** — by default (`AUDIT` policy) it's recorded in
`decision_audit.payload.unmappedCriteria` and processing continues; `DLQ` policy instead
routes the whole event to the DLQ with `errorCode=UNKNOWN_CRITERION_KEY`. The catalog
version used at scoring time is persisted on the decision and the `CREATED` audit payload.

**Catalog evolution:** adding new ids/aliases is safe and non-breaking. Renaming or
removing an existing canonical id is a breaking change — add a new id instead and keep the
old one valid, so rule-sets and historical decisions referencing it don't need retroactive
migration.

### Virtual threads for prechecks

Three simulated I/O checks (`duplicate-profile-check`, `sanctions-check`,
`education-format-check` — the last one goes through the [SOAP/XML adapter](#soapxml-education-adapter))
run in parallel on a dedicated `Executors.newVirtualThreadPerTaskExecutor()` bean, each
wrapped in its own `CompletableFuture.orTimeout(...)` so one slow check can't block
collecting the others. Concurrency toward the *simulated external dependency* is bounded
by a `Semaphore` (`app.precheck.max-concurrent-calls`), **not** by the executor: virtual
threads are cheap to create by the thousand, so limiting their count wouldn't actually
protect anything — the real bottleneck is the dependency's own capacity. Crucially,
`Semaphore.acquire()` parks a virtual thread without pinning its carrier, unlike a
`synchronized` block (deliberately not used anywhere in this path). Results land in the
`CREATED` audit payload's `checks[]` field, not in `ruleResults` — TASK.md allows either,
and injecting synthetic rows into `ruleResults` would blur its rule-set-criterion
semantics.

**Where virtual threads don't help** (and this codebase deliberately doesn't lean on them
for): CPU-bound work — `ScoreCalculator` runs on the calling thread, never dispatched to
the virtual-thread executor, because virtual threads only help when a thread is *blocked*,
not when it's *computing*. `synchronized` blocks and native/JNI calls pin the carrier
thread, defeating the point — none appear in the precheck path. Connection-pool limits:
HikariCP's `maximum-pool-size` is still the real ceiling on concurrent DB work regardless
of how many virtual threads are launched; spinning up thousands of them just means
thousands *queue* for a connection rather than bypassing the pool. Kafka partition count
similarly bounds real consumer parallelism no matter the thread model, and per-candidate
ordering isn't guaranteed across partitions — which is fine here because idempotency is
enforced by the DB constraint, not by message ordering.

### SOAP/XML education adapter

`contract/soap/education-verification.xsd` defines two trivial complex types. A full SOAP
stack (WSDL, envelope handling, Spring-WS/CXF) would be substantial added complexity for
near-zero benefit, and TASK.md itself says a real external service isn't required. Instead:
`EducationVerificationXmlCodec` marshals/unmarshals by hand via DOM (proper escaping, no
string concatenation — no JAXB either, since the JDK's DOM/SAX/`javax.xml.validation` APIs
are already sufficient for a schema this small and JAXB was removed from the JDK).
`EducationVerificationXsdValidator` runs a schema-validating SAX parse pairing an
`ErrorHandler` with a `ContentHandler` that tracks the open-element stack, so violations
get a best-effort element path (documented as "path, not true XPath" — a real XPath engine
is unjustified at this scope) rather than just a line/column number.
`EducationVerificationStub` stands in for the real external service and genuinely
round-trips XML *strings* (not domain objects), so the codec/validator path is exercised
the way a real remote call would be. `SoapEducationAdapter` validates both the outbound
request and the inbound response against the XSD and never throws past its own boundary —
every failure becomes a result the precheck orchestrator can report.

## Contract ambiguities recorded

- `AuditAction.UPDATED_BY_REPLAY` exists in the OpenAPI contract's enum but nothing in
  TASK.md's requirements currently produces it. It's mapped for schema completeness but
  never emitted — reserved for a hypothetical future "recompute" admin endpoint.
- "Latest decision" for `GET /decisions/by-candidate/{candidateId}` is
  `ORDER BY decided_at DESC LIMIT 1`. Multiple decisions per candidate are legitimate
  (different `parsedAt` re-screenings share a `candidateId` but not a `(candidateId, parsedAt)`
  pair), so "latest" is defined by decision time, not by uniqueness of the candidate.
- The semantic catalog version is persisted on the decision (`semantic_catalog_version`
  column) and in the `CREATED` audit payload, but deliberately **not** added to
  `DecisionResponse` JSON — the contract's response field list is fixed and TASK.md says
  not to add fields.

## Testing

Unit tests (JUnit 5 + Mockito + AssertJ, no Spring context except where noted) cover: score
calculation (OK/PARTIAL/NO, missing criteria, both threshold boundaries inclusive, weight-
sum clamping), rule-set active-selection by `activeFrom`, semantic normalization (alias/
case-insensitive resolution, unknown-key handling), JSON Schema validation (valid sample +
hand-crafted invalid payloads, JSON Pointer assertions), the full consumer pipeline
(malformed/blank JSON, schema violations, unknown-criterion-key under both policies,
missing rule-set, successful persist with MDC assertions, duplicate-event exception
swallowing), DLQ envelope construction, outbox publishing (empty batch, success, failure/
retry-count, one failure not blocking the batch), XSD validation and the SOAP adapter's
heuristic branches, precheck parallelism/timeout/partial-failure/semaphore-serialization,
rule-set creation (duplicate, unknown-canonical-key), and decision override
(version-match/stale-version/not-found).

Integration tests (`src/test/.../integration/`, Testcontainers with **real** Postgres and
Kafka via the existing `TestcontainersConfiguration`) cover the end-to-end flows described
in TASK.md — see that package for the current list. These require Docker; all pass — see
the verification note above.

## What wasn't done / known limitations

- No k6 load-testing script (listed as a bonus item; skipped for time).
- No OpenTelemetry trace/span attributes (bonus item; skipped for time).
- No `@EntityGraph`/explicit fetch-graph tuning — the entity graph here is shallow enough
  (no collection associations between the three main entities) that N+1 isn't a real risk
  in the current query set.
- Batch outbox publishing exists (claim size bounded by `app.outbox.batch-size`,
  non-overlapping `fixedDelay` scheduling as backpressure) but there's no adaptive
  backoff on batch size under sustained backlog beyond that.
- Structured JSON logging (e.g. `logstash-logback-encoder`) wasn't added — logs are
  pattern-formatted text with MDC fields, sufficient for `grep candidateId=X` but not for
  direct ingestion into a log aggregator's JSON pipeline without a Logback layout change.
