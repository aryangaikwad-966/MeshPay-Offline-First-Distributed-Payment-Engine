# MeshPay — Offline-First Distributed Payment Infrastructure

### Secure delayed packet synchronization, durable idempotency, and event-driven recovery

MeshPay explores how a backend can safely accept delayed payment packets when clients reconnect, requests race or retry, and distributed services fail between workflow steps. It combines cryptographic packet validation, PostgreSQL-backed idempotency, Redis coordination, Kafka messaging, persisted Saga state, and an operational dashboard.

> **Scope and disclaimer:** MeshPay is a local engineering demonstration, not a banking ledger, payment processor, or production settlement system. `SETTLED` means the demo Saga completed its local workflow; no external money movement occurs.

## Technology stack

| Area | Technologies |
| --- | --- |
| Services | Java 17, Spring Boot 3.3, Spring Cloud, Maven Wrapper |
| Persistence and coordination | PostgreSQL 16, Redis 7, Flyway for payment lifecycle migrations |
| Messaging | Apache Kafka 3, ZooKeeper |
| Packet security | AES-256-GCM, RSA-OAEP, RSA-PSS |
| Dashboard | React, TypeScript, Tailwind CSS, Nginx |
| Operations | Prometheus, Grafana, Micrometer, OpenTelemetry dependencies |
| Local environment and tests | Docker Compose, JUnit 5, Testcontainers (payment integration test) |

## Contents

- [Verified highlights](#verified-highlights)
- [Architecture](#architecture)
- [Payment packet lifecycle](#payment-packet-lifecycle)
- [Services and responsibilities](#services-and-responsibilities)
- [Prerequisites and quick start](#prerequisites-and-quick-start)
- [Environment variables and packet key configuration](#environment-variables-and-packet-key-configuration)
- [Security and packet protocol](#security-and-packet-protocol)
- [API reference](#api-reference)
- [Dashboard](#dashboard)
- [Idempotency and correctness guarantees](#idempotency-and-correctness-guarantees)
- [Transactional outbox and Kafka delivery semantics](#transactional-outbox-and-kafka-delivery-semantics)
- [Saga orchestration and recovery](#saga-orchestration-and-recovery)
- [Observability](#observability)
- [Testing and verification](#testing-and-verification)
- [Known limitations and future work](#known-limitations-and-future-work)
- [Repository structure](#repository-structure)
- [License status](#license-status)

## Verified highlights

| Capability | Audit result |
| --- | --- |
| Delayed packet ingestion | Implemented on the server. No offline wallet or client packet-builder is included. |
| Packet cryptography | RSA-OAEP/SHA-256 key unwrap, AES-GCM authentication, RSA-PSS/SHA-256 sender verification are implemented. Four cryptography unit tests passed. |
| Idempotency | Redis `SET NX` is used as a short-lived coordination claim; PostgreSQL unique constraints are the durable guard. Redis failure falls back to PostgreSQL. |
| Concurrent duplicates | A 24-request Testcontainers test exists but was skipped in the recorded run because its JVM could not reach Docker. A separate live Compose run on 2026-10-21 recorded one durable payment, one synchronization outbox event, and one completed Saga. This is a single correctness demonstration, not a benchmark. |
| Payment outbox | Implemented for Payment Service. Rows are claimed with `SKIP LOCKED`, published with broker acknowledgment, retried, and eventually sent to a DLT. Failure recovery has not been comprehensively tested. |
| Saga | State and transition history are persisted. Commands are published synchronously; there is no transactional Saga command outbox. Compensation changes local workflow state only. |
| Dashboard | Three read-only views use live Payment/Saga APIs. No login integration, retry controls, or lifecycle-history detail view is currently included. |
| Metrics and tracing | Prometheus scrape targets, application metrics, Grafana provisioning, and OpenTelemetry dependencies/configuration exist. Live scrape/panel population and trace export have not been verified end to end. |

## Architecture

```mermaid
flowchart LR
    Client[Offline client or bridge] -->|HTTP packet sync| Gateway[API Gateway :8080]
    Gateway -->|Eureka-discovered route| Payment[Payment Service :8081]
    Gateway -->|Eureka-discovered route| Saga[Saga Service :8082]

    Browser[React dashboard :3001] -->|Nginx proxy /api/payment| Payment
    Browser -->|Nginx proxy /api/saga| Saga

    Payment -->|SET NX coordination claim| Redis[(Redis)]
    Payment -->|payment + lifecycle + outbox transaction| PayDB[(Payment PostgreSQL)]
    Payment -->|outbox lifecycle events| Kafka[(Kafka)]
    Kafka -->|payment events| Saga
    Saga -->|persist state and history| SagaDB[(Saga PostgreSQL)]
    Saga -->|synchronously published commands| Kafka
    Kafka -->|Saga commands| Payment

    Payment -->|registers| Eureka[Eureka :8761]
    Saga -->|registers| Eureka
    Gateway -. service discovery .-> Eureka

    Prom[Prometheus :9090] -->|scrape| Payment
    Prom -->|scrape| Saga
    Prom -->|scrape| Gateway
    Grafana[Grafana :3000] -->|Prometheus datasource| Prom
```

The dashboard's Nginx proxy talks directly to Payment and Saga APIs; it does not route dashboard calls through the API Gateway. Redis is not the source of payment correctness. Payment event publication uses the Payment Service outbox. Saga command publication does not use a durable transactional outbox.

## Payment packet lifecycle

```text
Created locally while offline (client capability; client is not included)
        ↓ submitted later
Synchronized request → cryptographic/schema/time validation
        ↓ payment row + lifecycle row + outbox row commit together
Durably synchronized → Saga validation → settlement request → settled locally
                                                     ↘ failure / local compensation
```

These states are intentionally distinct:

- **Locally created:** packet exists on a client while disconnected; this repository does not implement that client.
- **Synchronized:** the server accepted the request and committed its payment, lifecycle, and initial outbox data.
- **Validated:** a later workflow step accepted the payment according to the demo's server-side rules.
- **Durably accepted:** payment state is committed in PostgreSQL; Redis is only a fast coordination layer.
- **Saga completed / locally settled:** the simulated workflow reached its terminal state. This is not a real-world settlement confirmation.

## Services and responsibilities

| Service | Host port | Responsibility |
| --- | ---: | --- |
| API Gateway | 8080 | Routes `/api/payment/**` and `/api/saga/**` through Eureka; applies configured circuit breakers and fallbacks. |
| Payment Service | 8081 | Packet verification, synchronization, PostgreSQL records, Redis claim, payment outbox, command consumers, Actuator metrics. |
| Saga Service | 8082 | Saga state and transition history, event deduplication, workflow commands, compensation state. |
| Eureka Server | 8761 | Service registry. |
| Dashboard | 3001 | Read-only overview, recent transactions/search, Saga and outbox views. |
| Payment PostgreSQL | 5433 | Payment, lifecycle, packet-attempt, processed-command, and outbox data. |
| Saga PostgreSQL | 5434 | Saga states, transitions, and processed-event records. |
| Redis | 6379 | Expiring packet synchronization claims. |
| Kafka / ZooKeeper | 9092 host / 29092 Compose; 2181 | Inter-service commands and payment lifecycle events. |
| Prometheus / Grafana | 9090 / 3000 | Configured metrics scraping and provisioned dashboard. |

## Prerequisites and quick start

Prerequisites: Docker Desktop with Compose. Java 17 is needed for Maven tests; Node.js/npm is only needed to run/build the dashboard outside Docker.

```bash
docker compose up --build -d
docker compose ps
```

The payment service applies Flyway migrations at startup. Compose named volumes preserve local state when containers are recreated.

To stop without deleting local data:

```bash
docker compose down
```

To intentionally delete local PostgreSQL, Redis, Kafka, and monitoring volumes:

```bash
docker compose down -v
```

`down -v` is destructive; it is not required for ordinary rebuilds or upgrades.

### Local URLs

| UI / endpoint | URL |
| --- | --- |
| React dashboard | [http://localhost:3001](http://localhost:3001) |
| API Gateway | [http://localhost:8080](http://localhost:8080) |
| Payment Swagger UI | [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) |
| Saga Swagger UI | [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html) |
| Payment health / metrics | `http://localhost:8081/actuator/health` / `/actuator/prometheus` |
| Saga health / metrics | `http://localhost:8082/actuator/health` / `/actuator/prometheus` |
| Gateway health / metrics | `http://localhost:8080/actuator/health` / `/actuator/prometheus` |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Grafana | [http://localhost:3000](http://localhost:3000) |
| Eureka | [http://localhost:8761](http://localhost:8761) |

Swagger is available without authentication in the Compose `dev` profile. In a secured profile, endpoints follow service security rules and may require a JWT.

## Environment variables and packet key configuration

Compose defaults to `SPRING_PROFILES_ACTIVE=dev`, where Payment and Saga permit unauthenticated requests. Defaults such as `changeme` and `dev-only-change-me` are for local use only; do not expose this configuration publicly. Compose reads a root `.env` file, which is ignored by `.gitignore`; no `.env.example` is currently provided.

| Variable | Compose behavior / format |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Defaults to `dev`; use a secured profile only after configuring JWT settings. |
| `DB_USER`, `DB_PASSWORD` | PostgreSQL credentials; defaults are `meshpay` / `changeme`. |
| `REDIS_PASSWORD`, `REDIS_PORT` | Redis password and host port; defaults are `changeme` / `6379`. Container-to-container port remains 6379. |
| `GRAFANA_ADMIN_PASSWORD` | Grafana `admin` password; defaults to `dev-only-change-me`. |
| `MESHPAY_PACKET_RSA_PRIVATE_KEY` | Base64-encoded DER RSA PKCS#8 server private key. |
| `MESHPAY_PACKET_RSA_PUBLIC_KEY` | Base64-encoded DER X.509 / SubjectPublicKeyInfo server public key. |
| `MESHPAY_PACKET_TRUSTED_SIGNERS` | Semicolon-delimited `senderVpa=base64(DER X.509 RSA public key)` entries. |
| `MESHPAY_JWKS_URI` | JWKS endpoint for secured Payment/Saga profiles. |
| `MESHPAY_JWT_ISSUER`, `MESHPAY_JWT_AUDIENCE` | Required issuer and audience for secured Payment/Saga profiles. |

The application also defines `MESHPAY_PACKET_MAX_OFFLINE_AGE` (default `30d`), but the current Compose file does not forward that variable into the Payment container. Configure it through an application/Compose override if you need to change the default. No private keys or trusted signer keys are included in the repository. Key generation, provisioning, onboarding, secure storage, and rotation are operator responsibilities.

Outside `dev`, the Payment and Saga services require JWT authentication for protected routes, validate the configured issuer, audience, and standard token time claims, and restrict operations/metrics routes to `meshpay:ops` / `meshpay:metrics:read` scopes. JWT integration with an actual identity provider has not been tested. The Gateway has no separate Resource Server security configuration in this repository, and its Actuator exposure includes gateway diagnostics; secure it and restrict service/management network access before any non-local deployment. The dashboard has no token/session integration, so it is intended for the local dev profile unless an authenticated proxy is added.

## Security and packet protocol

Packet envelope and plaintext schemas are defined in:

- `meshpay-payment-service/src/main/java/com/demo/meshpay/payment/api/PaymentPacketRequest.java`
- `meshpay-payment-service/src/main/java/com/demo/meshpay/payment/api/DecryptedPaymentPacket.java`

Verified implementation behavior:

1. Packet contents are encrypted with AES-256-GCM; the service requires a 12-byte IV and verifies the GCM authentication tag during decryption.
2. The AES key is unwrapped with RSA-OAEP using SHA-256 and MGF1-SHA-256. Configured recipient RSA keys must match and be at least 2048 bits.
3. The sender signs a canonical envelope with RSA-PSS/SHA-256. The trusted sender public key is selected from `MESHPAY_PACKET_TRUSTED_SIGNERS`.
4. GCM additional authenticated data binds the protocol label, packet ID, normalized sender, and sender nonce.
5. The service checks decrypted identity against the envelope, validates required payment fields and the accepted time window, and rejects nonce reuse.

Encryption and signatures serve different purposes: encryption provides confidentiality and authenticated ciphertext; RSA-PSS authenticates the packet sender. Neither alone prevents replay. The durable replay guard is sender plus nonce in PostgreSQL. The logical packet hash is SHA-256 over a versioned canonical identity including packet ID, normalized parties, amount, currency, signed time, and nonce; PostgreSQL additionally has unique constraints for packet ID and sender/nonce.

The receiver checks IV length and GCM authentication, but cannot prove that a client generated a fresh random AES key/IV for every encryption. `bridgeNodeId` and `hopCount` are transport metadata and are not included in the canonical signature/AAD; treat them as untrusted. This repository does not include a packet-creation client, secure hardware key store, automated trusted-signer lifecycle, production secret manager, or key-rotation procedure.

## API reference

Swagger/OpenAPI is the request-schema reference for the service endpoints.

| Method and path | Purpose |
| --- | --- |
| `POST /api/payment/packets/sync` | Verify and synchronize a signed encrypted packet. `SYNCHRONIZED` → 201, `DUPLICATE` → 200, `IN_PROGRESS` → 202 with `Retry-After`; nonce replay is rejected. |
| `GET /api/payment/packets/recipient-key` | Return the configured server public key; returns unavailable when packet encryption is not configured. |
| `GET /api/payment/ops/overview` | PostgreSQL payment, attempt, duplicate, pending, and outbox counts. |
| `GET /api/payment/ops/transactions` | Recent 100 transactions; API supports `search`, `status`, `from`, and `to` filters. |
| `GET /api/payment/ops/transactions/{identifier}` | Transaction details and lifecycle history by packet ID or canonical hash. |
| `GET /api/payment/ops/attempts` | Recent packet attempts and outcomes. |
| `GET /api/payment/ops/outbox` | Recent outbox publication status and sanitized error code. |
| `GET /api/saga/ops/overview` | Counts of persisted, completed, compensated, and active Sagas. |
| `GET /api/saga/ops/sagas` | Recent Saga states. |
| `GET /api/saga/ops/sagas/{sagaId}/history` | Persisted Saga transition history. |
| `GET /actuator/health`, `GET /actuator/prometheus` | Service health and metrics (availability depends on profile/security). |

## Dashboard

The React + TypeScript dashboard is served by Nginx on port 3001 and proxies API requests directly to Payment and Saga. Its three views are Overview, Transactions, and Reliability. Data refreshes every 15 seconds, and loading/error/empty states distinguish unavailable data from a real zero.

The current UI shows aggregate counters, recent transactions with client-side ID/hash search, recent Saga states, and recent outbox events. Although APIs expose transaction history and filter parameters, the UI does not yet expose those details/filters. The dashboard is read-only: it has no authentication flow, retry/replay action, or Saga transition-history drill-down.

To build outside Docker:

```bash
cd meshpay-dashboard
npm ci
npm run build
npm audit
```

## Idempotency and correctness guarantees

The canonical logical packet hash is the idempotency key. Redis uses `SET NX` with a 60-second claim lease and caches a committed marker for up to 30 days. A Redis connection error falls back to database processing. PostgreSQL unique constraints on packet hash, packet ID, and `(sender_vpa, nonce)` are the durable duplicate/replay barriers. A stale or expired Redis claim therefore cannot override the database's correctness role.

Payment row, `SYNCHRONIZED` lifecycle record, and initial synchronization outbox row are written in one PostgreSQL transaction. A duplicate request does not create another payment or synchronization outbox row. The transaction commit is the durable acceptance boundary; later validation and local settlement are separate workflow steps.

The Testcontainers test asserts one payment and one outbox effect for 24 concurrent calls, but it was skipped in the recorded Maven run. The manual live run provides evidence for that single run only; Redis outage, stale-claim recovery, database rollback, and high-load behavior are not covered by executed automated tests.

## Transactional outbox and Kafka delivery semantics

The Payment Service's scheduled outbox processor selects due unprocessed rows in batches with PostgreSQL `FOR UPDATE SKIP LOCKED`. It waits for Kafka's send result before marking a row processed. Failed sends increment retries and use bounded exponential delay; after the configured retry threshold, the publisher sends to a topic suffixed `.DLT` and marks that row processed only if Kafka acknowledges the dead-letter publication.

Delivery is **at least once**. A crash after Kafka acknowledges a message but before the database transaction commits can cause publication again. Payment command consumers and Saga event consumers record processed identifiers in their respective databases within the listener transaction; Kafka listener acknowledgment is record-based. These mechanisms support duplicate delivery handling but are not a proof of globally exactly-once effects. DLT inspection/replay is manual, and DLT/consumer recovery is not covered by integration tests.

## Saga orchestration and recovery

Saga state and transition history are stored in Saga PostgreSQL. The workflow reacts to packet synchronization, requests validation, requests settlement, processes local completion, or records compensation/failure states. Event deduplication and Saga DB state updates occur in the listener transaction. Transition changes are checked against an explicit allowed-transition table.

Saga commands are sent synchronously to Kafka while Saga transitions are being processed. The `SagaOutboxProcessor` is a placeholder; it does not persist or publish a transactional Saga outbox. A failure around the Saga DB commit and command publication can leave state and command delivery needing operator investigation. Compensation records local workflow state only; no external transfer exists to reverse. Restart, duplicate, failure, and compensation paths are not covered by Saga automated tests.

## Observability

Prometheus is configured to scrape Payment, Saga, and Gateway Actuator endpoints. Application code registers packet accepted/rejected/duplicate/replay and Redis-fallback counters, Payment/Saga workflow counters and timers, plus outbox/Saga gauges. Grafana provisioning points at Prometheus and includes queries for packet outcomes, outbox backlog/age, payment timing, Saga activity, and scrape health.

These configurations and metric definitions are present, but populated Prometheus/Grafana panels were not independently verified in the recorded run. OpenTelemetry bridge/exporter dependencies and sampling settings exist; Compose has no collector service or configured collector endpoint, and cross-service trace export/propagation has not been verified end to end. Resilience4j packet rate limiting and Gateway circuit-breaker routes are configured; failure/threshold behavior has not been systematically tested.

## Testing and verification

Run backend tests with Java 17:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"  # macOS; set JAVA_HOME manually on other platforms
./mvnw -f meshpay-payment-service/pom.xml clean test
./mvnw -f meshpay-saga-service/pom.xml clean test
./mvnw -f meshpay-api-gateway/pom.xml clean test
./mvnw -f meshpay-eureka-server/pom.xml clean test
```

### Recorded results — 2026-10-21

- Payment `clean test`: build passed; all 4 `PacketCryptographyTest` tests passed (round trip, invalid signature, altered ciphertext, expiry). `ConcurrentPacketSyncIntegrationTest` was **skipped** because the Java test process could not access the Docker Desktop socket.
- Saga, Gateway, and Eureka `clean test`: builds passed; no automated test sources ran in these modules.
- Dashboard `npm ci`, production build, and `npm audit`: passed; npm reported zero vulnerabilities.
- Docker Compose configuration was validated; Payment, Saga, dashboard images were rebuilt, and the full local stack started. Payment and Saga became healthy. Read-only health checks returned `UP` for Payment/Saga/Gateway; dashboard and its proxied Payment overview returned HTTP 200.
- In a separate manual live Compose check, one valid signed/encrypted packet was submitted 24 times concurrently: 1 `SYNCHRONIZED` (201), 7 `DUPLICATE` (200), 16 `IN_PROGRESS` (202). PostgreSQL showed one durable payment and one synchronization outbox event; Kafka advanced one persisted Saga to `SAGA_COMPLETED`, and the local Payment state reached `SETTLED`.

The manual run is not a benchmark and has no committed packet-generator script or repeatable E2E test harness. The Testcontainers result remains skipped. `test-microservices.sh` is a legacy smoke script; it does not provision packet keys or exercise signed packet synchronization.

## Known limitations and future work

- No bundled offline wallet/client; only delayed server-side packet ingestion is implemented.
- No real payment rail, external settlement adapter, or monetary ledger.
- No production key-management service, secure hardware storage, signer onboarding, or automated rotation.
- Saga command publication lacks a transactional outbox or automatic recovery mechanism; DLT recovery is manual.
- Testcontainers concurrency execution, broader DB/Kafka/Redis failure injection, Saga tests, and dashboard component tests remain incomplete.
- OpenTelemetry export and trace propagation are not verified end to end; no collector is included in Compose.
- Hibernate `ddl-auto: update` remains enabled. Payment lifecycle migrations exist, but the full schema is not migration-managed and validate-only mode is not enabled.
- The dashboard has no production authentication integration and does not yet show transaction lifecycle detail/history or authorized recovery controls.
- Compose development defaults, plaintext local broker connections, sample credentials, and exposed service ports are not production-hardened.

## Repository structure

```text
meshpay-api-gateway/       HTTP routing and fallbacks
meshpay-eureka-server/     Service registry
meshpay-payment-service/   Packet security, idempotency, payment DB, outbox and consumers
meshpay-saga-service/      Persisted Saga state, transitions, consumers and commands
meshpay-dashboard/         React + TypeScript operational dashboard
monitoring/                Prometheus scrape config and Grafana provisioning/dashboard
docker-compose.yml         Local application and infrastructure stack
test-microservices.sh      Legacy smoke script (not a packet-sync E2E test)
```

## License status

No `LICENSE` file is present, so no license has been specified. Select and add a license before redistributing or accepting reuse of this project.
