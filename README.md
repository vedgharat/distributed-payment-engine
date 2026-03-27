# Distributed Payment Engine

A fault-tolerant, highly concurrent payment gateway API built with Java 21 and Spring Boot 3. Implements the core mechanics of a financial ledger system — similar to the transaction engine behind Venmo or Cash App — with strict data integrity guarantees and four independent safety layers preventing race conditions and duplicate charges.

---

## Architecture

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────────┐
│           Spring Boot 3 App             │
│                                         │
│  ┌─────────────┐   ┌─────────────────┐  │
│  │ AOP Aspect  │──▶│ TransferService │  │
│  │ @Idempotent │   │  (Redis Lock)   │  │
│  └─────────────┘   └────────┬────────┘  │
│                             │           │
│                   ┌─────────▼────────┐  │
│                   │ AtomicTransfer   │  │
│                   │ Executor         │  │
│                   │ (@Transactional) │  │
│                   └─────────┬────────┘  │
└─────────────────────────────┼───────────┘
                              │
          ┌───────────────────┼──────────────────┐
          ▼                   ▼                  ▼
    ┌───────────┐      ┌────────────┐    ┌──────────────┐
    │   Redis   │      │ PostgreSQL │    │ Apache Kafka │
    │Idempotency│      │   ACID DB  │    │ payments-    │
    │ Dist. Lock│      │ FOR UPDATE │    │    topic     │
    └───────────┘      └────────────┘    └──────────────┘
```

---

## Safety Layers

Every transfer passes through four independent safety layers. Each one catches what the previous misses.

| Layer | Technology | Problem Solved |
|---|---|---|
| Idempotency cache | Redis (AOP) | Duplicate network retries charging twice |
| Distributed lock | Redis Redisson `RLock` | Concurrent API requests racing to debit same wallet |
| Pessimistic lock | PostgreSQL `SELECT FOR UPDATE` | Two DB transactions reading stale balance simultaneously |
| DB constraint | `CHECK (balance >= 0)` | Overdraft even if application logic has a bug |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 (ACID, double-entry ledger) |
| Cache & Locks | Redis 7 via Redisson 3.29 |
| Messaging | Apache Kafka 3.7 |
| DB Migrations | Flyway 10 |
| Infrastructure | Docker & Docker Compose |

---

## Key Design Decisions

**Double-entry bookkeeping** — every transfer creates two immutable ledger records: a `DEBIT` for the sender and a `CREDIT` for the receiver, tied by a shared `correlation_id`. Balances are derived from the ledger. Records are never updated or deleted.

**Self-invocation fix** — `@Transactional` works through Spring's proxy. Calling a `@Transactional` method on `this` bypasses the proxy silently. `AtomicTransferExecutor` is a separate `@Service` bean so the proxy is always honoured and the transaction always opens.

**Ordered UUID locking** — wallets are always locked in ascending UUID order regardless of who is sender and who is receiver. This prevents deadlocks when two concurrent transfers involve the same pair of wallets in opposite directions.

**BigDecimal for money** — `float` and `double` cannot represent 0.1 exactly in binary. All balances and amounts use `NUMERIC(19,4)` in PostgreSQL and `BigDecimal` in Java.

**Kafka outside the transaction** — the Kafka publish happens after the database commits, not inside the transaction. A Kafka failure will not roll back a completed transfer. For guaranteed delivery, the next step is the Transactional Outbox Pattern.

---

## API Endpoints

### Create wallet
```
POST /api/v1/wallets
Content-Type: application/json

{
  "ownerName": "Alice",
  "initialBalance": 1000.00,
  "currency": "USD"
}
```

### Get wallet
```
GET /api/v1/wallets/{id}
```

### Transfer funds
```
POST /api/v1/transfers
Content-Type: application/json
Idempotency-Key: <unique-key>

{
  "senderWalletId": "uuid",
  "receiverWalletId": "uuid",
  "amount": 250.00,
  "currency": "USD"
}
```

The `Idempotency-Key` header is required. Sending the same key twice returns the cached response without moving money again.

---

## Running Locally

**Prerequisites:** Docker Desktop, Java 21, Maven

```bash
# 1. Clone the repo
git clone https://github.com/vedgharat/distributed-payment-engine.git
cd distributed-payment-engine

# 2. Start infrastructure
docker-compose up -d

# 3. Run the app
mvn spring-boot:run

# 4. Create two wallets
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Alice","initialBalance":1000.00,"currency":"USD"}'

curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Bob","initialBalance":500.00,"currency":"USD"}'

# 5. Transfer funds
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: txn-001" \
  -d '{
    "senderWalletId": "<alice-id>",
    "receiverWalletId": "<bob-id>",
    "amount": 250.00,
    "currency": "USD"
  }'
```

---

## Project Structure

```
src/main/java/com/paymentgateway/
├── config/               # Redisson and Kafka configuration
├── controller/           # REST endpoints
├── domain/
│   ├── entity/           # Wallet, Transaction (JPA entities)
│   └── enums/            # TransactionType, TransactionStatus
├── dto/                  # Request and response DTOs
├── exception/            # Custom exceptions + global handler
├── idempotency/          # @Idempotent annotation + AOP aspect
├── kafka/
│   ├── consumer/         # PaymentEventConsumer
│   ├── event/            # PaymentCompletedEvent
│   └── producer/         # PaymentEventProducer
├── repository/           # Spring Data JPA repositories
└── service/              # TransferService, AtomicTransferExecutor

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__initial_schema.sql
```

---

