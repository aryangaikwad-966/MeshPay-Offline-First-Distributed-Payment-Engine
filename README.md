<div align="center">

# SyncPay

### An offline-first, distributed payment engine

Cryptographically secured packet sync · durable idempotency · Kafka sagas · simulated ledger · ops dashboard

[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-3.5-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/license-unspecified-lightgrey)](#license)

Safely accepts payment packets created on disconnected clients, synchronizes them **exactly once** on reconnect, and drives them through a resilient settlement workflow.

> SyncPay is a local engineering demonstration — settlement and balances are **simulated**. No real money moves.

</div>

---

## Overview

Payment clients are unreliable: they go offline, retry after timeouts, and resubmit the same packet concurrently. Naive ingestion double-posts or silently loses payments. SyncPay solves this with layered defenses:

| Layer | Mechanism |
| --- | --- |
| **Client** | Browser Web Crypto builds a signed, encrypted envelope (AES-256-GCM payload · RSA-OAEP key wrap · RSA-PSS signature) using a device-managed, non-exportable signing key, and persists it in IndexedDB until acknowledged |
| **Ingestion** | Payment Service verifies signature → unwraps key → decrypts with AAD → validates fields, time window, and nonce replay, then commits payment + lifecycle + outbox in **one PostgreSQL transaction** |
| **Idempotency** | Redis `SET NX` claims coordinate fast-path duplicates; PostgreSQL unique constraints (`packet_hash`, `packet_id`, `(sender_vpa, nonce)`) are the authoritative barrier |
| **Settlement** | Kafka lifecycle events flow through transactional outboxes; a Saga service orchestrates *validate → settle → complete* with persisted state, transition history, deduplication, and stuck-saga recovery |
| **Financials** | Atomic debit/credit under `SELECT … FOR UPDATE`, append-only `ledger_entries`, idempotent full refunds with compensating entries |

## Architecture

SyncPay is four Spring Boot services plus their data stores, all wired together by Docker Compose. Requests enter through one gateway; services talk to each other **only through Kafka** — never by direct HTTP calls.

![SyncPay architecture diagram](docs/architecture.svg)

| Component | Port | Responsibility |
| --- | ---: | --- |
| API Gateway | 8080 | Single entry point — routes `/api/payment/**` and `/api/saga/**`, CORS, JWT auth, circuit breakers |
| Eureka Server | 8761 | Service registry for gateway discovery |
| Payment Service | 8081 | Packet crypto/verify, idempotent sync, simulated ledger + refunds, payment outbox, command consumer |
| Saga Service | 8082 | Persisted saga state machine, transition history, command outbox, event consumer, recovery sweeper |
| Dashboard | 3001 | React ops UI + offline packet client (Nginx; proxies API calls to the gateway) |
| PostgreSQL ×2 | 5433 / 5434 | One database per service — no shared schema |
| Redis | 6379 | Expiring idempotency claims (PostgreSQL is the durable guard) |
| Kafka | 9092 | All inter-service events and commands |
| Prometheus / Grafana | 9090 / 3000 | Metrics scraping and dashboards |

All published ports bind to `127.0.0.1` in Compose.

## Payment lifecycle

![SyncPay payment lifecycle diagram](docs/payment-lifecycle.svg)

States are deliberately distinct — a queued or synchronized packet is **not** a settled payment:

| Stage | Meaning |
| --- | --- |
| `LOCAL_QUEUED` / `RETRY_PENDING` | Client-side IndexedDB state; not yet accepted by the server |
| `SYNCHRONIZED` | Durably committed in PostgreSQL; Redis played no correctness role |
| `VALIDATED` → `SETTLEMENT_REQUESTED` → `SETTLED` | Server lifecycle driven by saga commands |
| `REJECTED` / `REFUNDED` | Terminal failure / compensated-by-refund states |
| `SAGA_COMPLETED` | Local demo workflow finished — **not** a real-world settlement |

## Quick start

**Prerequisites:** Docker Desktop with Compose (full stack) · Java 17 (Maven tests) · Node.js 20+ (dashboard/scripts only) · k6 and Terraform optional.

```bash
cp .env.example .env        # local dev profile — never commit .env
docker compose up --build -d
docker compose ps           # wait for healthy
```

| URL | Purpose |
| --- | --- |
| http://localhost:3001 | Dashboard |
| http://localhost:8080 | API Gateway |
| http://localhost:8081/swagger-ui.html | Payment API docs |
| http://localhost:8082/swagger-ui.html | Saga API docs |
| http://localhost:9090 · :3000 | Prometheus · Grafana (`admin` / `GRAFANA_ADMIN_PASSWORD`) |
| http://localhost:8761 | Eureka |
| http://localhost:9000 | SonarQube — only with `--profile sonar` |

- Stop without losing data: `docker compose down`
- Wipe local volumes (PostgreSQL, Redis, Kafka, monitoring): `docker compose down -v` — destructive
- Payment and Saga apply Flyway migrations at startup (Hibernate `validate` mode); unversioned legacy databases are baselined and migrated forward. **Back up both databases before upgrading** — Flyway DDL rollback is not automatic.

### Packet key setup (local demo)

Packet sync always requires a server RSA keypair (`SYNCPAY_PACKET_RSA_PRIVATE_KEY` / `SYNCPAY_PACKET_RSA_PUBLIC_KEY`, both or neither). Sender authentication has two paths:

- **Device registration (default).** The dashboard's Offline Client generates a non-exportable RSA-PSS key pair on first use, persists it in IndexedDB across browser sessions, and registers the public key per sender VPA via `POST /api/payment/devices/register`. Registered keys are verified from the `device_keys` table; `POST /api/payment/devices/{vpa}/revoke` disables them without deleting audit history. Re-registration rotates key material.
- **Static trusted signers (CLI/dev fallback).** `SYNCPAY_PACKET_TRUSTED_SIGNERS=senderVpa=base64(DER SPKI)` entries (`;`-separated) are used when a VPA has no registered device keys — this is how `scripts/create-payment-packet.js` and `.local-keys/` demo identities authenticate.

To enable packet sync locally, generate the recipient pair into the git-ignored `.local-keys/` directory:

```bash
mkdir -m 700 .local-keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local-keys/recipient-private.pem
openssl pkey -in .local-keys/recipient-private.pem -pubout -out .local-keys/recipient-public.pem
openssl pkcs8 -topk8 -nocrypt -in .local-keys/recipient-private.pem -outform DER | base64 | tr -d '\n' > .local-keys/recipient-private.pkcs8.b64
openssl pkey -in .local-keys/recipient-public.pem -pubin -outform DER | base64 | tr -d '\n' > .local-keys/recipient-public.spki.b64
```

Then either put `SYNCPAY_PACKET_RSA_PRIVATE_KEY` / `SYNCPAY_PACKET_RSA_PUBLIC_KEY` into `.env`, or run `scripts/compose-with-demo-packet-keys.sh` — it exports the keys without editing `.env`. For CLI packet generation, also generate a signer pair and add it to `SYNCPAY_PACKET_TRUSTED_SIGNERS` (the script can do this for `alice@syncpay.local`).

In the dashboard's **Offline Client** tab: click **Register device** (or just create a packet while online — registration happens automatically), and payments are signed by the device key. A developer can still import `signer-private.pkcs8.b64` per session to override the device key. A CLI generator is also available: `node scripts/create-payment-packet.js` (writes `/tmp/syncpay-packet-<uuid>.json`).

These are demo credentials — no KMS, HSM, or key-rotation automation exists.

## Environment variables

Compose reads the root `.env` (git-ignored). `.env.example` selects `dev`, which permits unauthenticated access — **local use only**. Compose defaults to `prod` when `SPRING_PROFILES_ACTIVE` is unset, which **fails closed**: Payment, Saga, and Gateway refuse to start without `SYNCPAY_JWKS_URI`, `SYNCPAY_JWT_ISSUER`, and `SYNCPAY_JWT_AUDIENCE`.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (permit-all) or anything else (JWT required). Compose default: `prod` |
| `DB_USER` / `DB_PASSWORD` | PostgreSQL credentials (local defaults are placeholders) |
| `REDIS_PASSWORD` / `REDIS_PORT` | Redis auth and host port |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password |
| `SYNCPAY_PACKET_RSA_PRIVATE_KEY` / `SYNCPAY_PACKET_RSA_PUBLIC_KEY` | Base64 DER PKCS#8 / X.509 recipient key pair — both or neither |
| `SYNCPAY_PACKET_TRUSTED_SIGNERS` | `senderVpa=base64(DER SPKI)` entries, `;`-separated — fallback for senders without registered device keys |
| `SYNCPAY_PACKET_MAX_OFFLINE_AGE` | Accepted offline window (default `30d`) |
| `SYNCPAY_OUTBOX_MAX_RETRIES` / `SYNCPAY_OUTBOX_ENABLED` | Payment outbox retry bound / toggle |
| `SAGA_RECOVERY_TIMEOUT` / `SAGA_RECOVERY_INTERVAL_MS` / `SAGA_RECOVERY_MAX_ATTEMPTS` | Stuck-saga sweeper tuning (defaults `PT10M` / `60000` / `3`) |
| `SYNCPAY_JWKS_URI` / `SYNCPAY_JWT_ISSUER` / `SYNCPAY_JWT_AUDIENCE` | Required outside `dev` |

**Dev JWT tooling:** `node scripts/mint-dev-token.js` runs a throwaway JWKS issuer and mints RS256 tokens for exercising the secured profile locally:

```bash
node scripts/mint-dev-token.js serve            # JWKS on :8799
node scripts/mint-dev-token.js mint --role ADMIN
# then set SYNCPAY_JWKS_URI=http://localhost:8799/.well-known/jwks.json
#      SYNCPAY_JWT_ISSUER=syncpay-dev  SYNCPAY_JWT_AUDIENCE=syncpay
```

Dev-only — no production IdP is configured.

## API surface

Full request schemas: Swagger UI at `:8081/swagger-ui.html` and `:8082/swagger-ui.html` (open in `dev`; JWT-protected otherwise). Key endpoints via the gateway (`:8080`) or directly:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/payment/packets/sync` | Verify + ingest an encrypted packet → `201 SYNCHRONIZED`, `200 DUPLICATE`, `202 IN_PROGRESS` (+`Retry-After`); rate-limited at 40 req/s |
| `GET /api/payment/packets/recipient-key` | Server RSA public key for packet encryption |
| `POST /api/payment/devices/register` · `GET /api/payment/devices/{vpa}` · `POST /api/payment/devices/{vpa}/revoke` | Device signing-key registration, status, and revocation |
| `GET /api/payment/ops/{overview,transactions,transactions/{id},attempts,outbox}` | Read-only operational views backed by PostgreSQL |
| `GET /api/payment/ops/outbox/dead` · `POST …/replay/{id}` | Dead-letter inspection and audited replay to the original topic |
| `POST /api/payment/payments/{id}/refund` · `GET …/ledger` · `GET /api/payment/refunds` | Full idempotent refund, immutable ledger, refund list |
| `POST /api/payment/{validate,settlement/request,settlement/complete,failure}` | Saga command handlers (Kafka-consumed; directly callable in dev) |
| `GET /api/saga/ops/{overview,sagas,sagas/{id}/history,outbox,outbox/overview}` | Saga state, transition history, command outbox health |
| `POST /api/saga/{start,validate,settlement/request,settlement/complete}` | Manual saga drivers |
| `GET /actuator/health` · `/actuator/prometheus` | Health and metrics (scope-gated outside `dev`) |

## Engineering decisions and reliability mechanisms

- **Idempotency is layered, not single-point.** Redis claims are a fast-path optimization with 60-second leases; a Redis outage falls back to PostgreSQL, and unique constraints remain authoritative. Nonce replay is rejected (`409 NONCE_REPLAY`) even when packet content differs.
- **Transactional outbox on both sides.** Payment lifecycle events and Saga commands commit in the same DB transaction as the state change, then publish via schedulers that wait for Kafka acknowledgment. Delivery is **at-least-once**; consumer-side dedup (`processed_events`, `processed_commands`, stable command IDs) makes effects effectively-once within the local transaction.
- **Dead-letter with operator replay.** Exhausted retries publish to `<topic>.DLT`; ops endpoints list metadata only (never payloads) and replay preserving `event_id`, so replayed events remain subject to consumer dedup.
- **Conservative saga recovery.** `SagaRecoveryScheduler` requeues `DEAD` commands once per sweep (bounded by `recovery_attempts`) or parks the saga in `MANUAL_REVIEW` — it never auto-settles or auto-compensates, because a timeout cannot prove the payment outcome.
- **Append-only financial records.** `ledger_entries` is insert-only with a unique `(packet_hash, account_vpa, direction, kind)` constraint — a payment leg can never post twice. Refunds write `REFUND` compensating entries instead of mutating settlement history.
- **Fail-closed security.** Outside `dev`, missing JWT configuration prevents startup; ops/metrics scopes are enforced per route at both gateway and service level. All published ports bind to loopback.
- **Correlation everywhere.** `X-Correlation-Id` propagates through MDC in HTTP and per-record in Kafka consumers via `RecordInterceptor`.

## Technology stack

| Layer | Technology |
| --- | --- |
| Services | Java 17 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 (Gateway, Eureka) · Maven Wrapper |
| Data | PostgreSQL 16 (one DB per service) · Flyway migrations · Hibernate `validate` |
| Coordination | Redis 7 — `SET NX` claims with Lua release/complete scripts |
| Messaging | Apache Kafka 3 (Confluent 7.5 images, ZooKeeper) |
| Packet crypto | AES-256-GCM · RSA-OAEP (SHA-256/MGF1) · RSA-PSS (SHA-256) · Web Crypto on the client |
| Resilience | Resilience4j circuit breakers, rate limiter, retry |
| Security | Spring Security OAuth2 resource server, JWKS JWT validation |
| Frontend | React 18 · TypeScript · Vite 6 · Tailwind CSS 4 · Nginx |
| Observability | Micrometer · Prometheus · Grafana (provisioned) · OpenTelemetry deps (no collector configured) |
| Testing | JUnit 5 · Mockito · Testcontainers · Node test runner · k6 |
| Analysis / IaC | SonarQube CE (opt-in profile) · Terraform (single AWS EC2 + cloud-init) |

## Testing & verification

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # macOS; set JAVA_HOME elsewhere
./mvnw -f syncpay-payment-service/pom.xml clean test   # Testcontainers migration + concurrency tests
./mvnw -f syncpay-saga-service/pom.xml clean test
./mvnw -f syncpay-api-gateway/pom.xml clean test
./mvnw -f syncpay-eureka-server/pom.xml clean test
cd syncpay-dashboard && npm ci && npm test && npm run build
```

CI (`.github/workflows/ci-cd.yml`) runs dependency-check, Maven build + test against Postgres/Kafka services, SpotBugs/PMD, JaCoCo, and Docker image builds on `main`.

### Recorded verification — 2026-11-06 (local Compose, Apple Silicon)

| Suite | Result |
| --- | --- |
| Payment Service | 25/25 tests — incl. financial-correctness integration tests (atomic debit/credit + ledger, concurrent settlement, insufficient funds, idempotent refund, DLT replay), device signing-key registration/revocation, and Flyway migrations through V7 |
| Saga Service | 17/17 tests — incl. recovery sweeper (requeue, `MANUAL_REVIEW`, attempt bounds, failure isolation) |
| Gateway / Eureka | Signed-JWT/JWKS integration tests pass in all secured services |
| Live E2E | Signed packet → `201 SYNCHRONIZED` → `SAGA_COMPLETED` → `SETTLED` with correct ledger entries; refund retry returned the same `refundId` (idempotent) |
| Browser E2E | Headless Chromium: device key registered → payment queued offline (`LOCAL_QUEUED`) → reconnect auto-synced (`201 SYNCHRONIZED`) → `SETTLED` with exactly 2 ledger entries; identical re-POST returned `DUPLICATE` with no second financial effect |
| k6 (local, single machine) | Health ~2.5 ms p50 · ops reads ~10 ms p50 · packet sync ~20 req/s sustained, p95 ≈ 12 ms; above the 40 req/s rate limit requests correctly returned `429` |

Load tests: `k6 run load-tests/health.js` · `load-tests/ops-overview.js` · `load-tests/payment-packet-sync.js` (packet files from `node scripts/create-payment-packet.js`).

## Dashboard

Four views — **Overview, Transactions, Reliability, Offline Client** — poll live ops APIs every 15 s. The service worker caches only the static shell; `/api/*` and health responses are never cached. The Offline Client implements the full packet lifecycle client-side: a non-exportable device signing key (generated once, persisted in IndexedDB, registered per sender VPA) → Web Crypto encrypt/sign → IndexedDB queue → sync with retry (`LOCAL_QUEUED → SYNCING → SYNCHRONIZED | DUPLICATE | IN_PROGRESS | REJECTED | RETRY_PENDING | FAILED`), reusing the stored envelope on every retry. Manual PKCS#8 signer import remains as a developer override.

<table>
<tr>
<td width="50%" valign="top">

**Overview** — service health, settlement pipeline, outbox backlog

<img src="docs/screenshots/overview.png" alt="SyncPay dashboard Overview tab" width="100%">

</td>
<td width="50%" valign="top">

**Transactions** — persisted payment records with packet search

<img src="docs/screenshots/transactions.png" alt="SyncPay dashboard Transactions tab" width="100%">

</td>
</tr>
<tr>
<td width="50%" valign="top">

**Reliability** — Saga states, command outbox, event publication health

<img src="docs/screenshots/reliability.png" alt="SyncPay dashboard Reliability tab" width="100%">

</td>
<td width="50%" valign="top">

**Offline Client** — packet creation and IndexedDB queue with retry

<img src="docs/screenshots/offline-client.png" alt="SyncPay dashboard Offline Client tab" width="100%">

</td>
</tr>
</table>

## Static analysis and deployment

```bash
docker compose --profile sonar up -d sonarqube   # http://localhost:9000
SONAR_TOKEN=<token> ./scripts/sonar-scan.sh
```

Terraform (`terraform/`) provisions one Ubuntu 24.04 EC2 instance that installs Docker, clones the repo, writes `.env`, and runs `docker compose up -d --build` via cloud-init. It exposes SSH (CIDR-restricted), `:3001`, and `:8080`. This is a single-VM deployment — not Kubernetes.

## Known limitations

- **Simulation only** — no real payment rail, settlement adapter, or money movement; balances and ledger entries are demo fixtures.
- **Key management is demo-grade** — device keys are registered/revoked per VPA in PostgreSQL, but there is no KMS/HSM, attestation, or rotation automation; the server recipient key is still env-configured and the packet format has no key ID.
- **At-least-once, not exactly-once** — correct under the implemented dedup layers, but no global exactly-once guarantee exists; crash windows between Kafka ack and DB commit can redeliver.
- **No manifest / not installable** — the service worker caches the app shell for offline reopen, but there is no web app manifest, icons, or standalone display mode; iOS Safari may also purge IndexedDB after ~7 days of inactivity.
- **Single-node everything** — one Kafka broker, one replica per service, in-process schedulers; the saga sweeper can race across replicas (transitions are idempotent, but there is no ShedLock-style coordination).
- **Refunds are synchronous and full-amount only** — no partial refunds, refund saga, or webhooks.
- **OpenTelemetry is not wired end-to-end** — tracing deps and sampling config exist, but Compose has no collector.
- **Ops maturity** — dead-letter replay is integration-tested, but a live Kafka-outage→DLT→replay drill has not been re-run; no runbooks or alerting rules are included.
- **CI caveat** — the workflow's Docker job builds from the repo root with no root `Dockerfile`; treat it as a template pending fixes.

## Repository layout

```text
syncpay-api-gateway/       Spring Cloud Gateway: Eureka routing, JWT enforcement, Resilience4j CB + fallbacks
syncpay-eureka-server/     Service registry
syncpay-payment-service/   Packet crypto/verify, idempotent sync, lifecycle, ledger, refunds, outbox, consumers
syncpay-saga-service/      Persisted saga state machine, command outbox, event consumer, recovery sweeper
syncpay-dashboard/         React + TS ops dashboard + offline packet client (IndexedDB), Nginx + service worker
monitoring/                prometheus.yml + Grafana provisioning and dashboard JSON
load-tests/                k6 scripts: health, ops reads, packet sync
scripts/                   demo packet-key compose helper, packet generator, dev JWT issuer, sonar scan
terraform/                 AWS EC2 deployment via cloud-init + docker compose
docs/screenshots/          Dashboard screenshots captured from the live Compose stack
```

## License

No `LICENSE` file is present; no license has been granted. Add one before redistributing.
