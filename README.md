# OrderHub — Order Management System (Microservices + Kafka)

A Spring Boot microservices system for e-commerce order management, rebuilt on
**Java 17 / Spring Boot 3.3.13 / Spring Cloud 2023.0.6** and extended with
**event-driven communication using Apache Kafka**.

This version keeps the reliable **synchronous** flows where correctness demands them,
and moves the genuinely **fire-and-forget** work (notifications, stock signals) onto
Kafka topics.

---

## Services

| Service                | Port | Responsibility                                                        |
|------------------------|------|----------------------------------------------------------------------|
| `eureka-server`        | 8761 | Service discovery registry                                           |
| `api-gateway`          | 8080 | Routing, JWT validation, role header injection, CORS                 |
| `product-service`      | 8081 | Product catalog, inventory, Redis cache, **stock-events producer**   |
| `order-service`        | 8082 | Users, orders, orchestration, **order-events producer**              |
| `notification-service` | 8083 | In-app + email notifications, **Kafka consumer** (leaf node)         |

Infrastructure: **PostgreSQL 16** (one database per service), **Redis 7**
(product cache), **Apache Kafka** (KRaft mode — no Zookeeper).

---

## What is synchronous vs. asynchronous — and why

The rebuild did **not** replace all communication with Kafka. It replaced only what
should be async. This distinction is the whole point of the exercise.

### Stayed SYNCHRONOUS (Feign, request/response)
- **`order-service -> product-service : check-and-reserve`**
  Placing an order needs an *immediate* yes/no on stock. The caller cannot proceed
  without the answer, and stock must be decremented atomically (all-or-nothing) to
  prevent overselling. A fire-and-forget event cannot give a synchronous decision.
- **`order-service -> product-service : restore-stock`** (cancellation / compensation)
  The consistency guarantee ("stock is restored *before* the order is marked
  cancelled; if restore fails, nothing changes") depends on a synchronous result.
- **`order-service -> product-service : low-stock`** (admin dashboard read)
  A simple read for a screen the admin is looking at right now.

### Moved to KAFKA (async, event-driven)
- **Order lifecycle notifications** — `order-service` publishes `order-events`;
  `notification-service` consumes them and creates the in-app notification + email.
  Previously this was a synchronous Feign call; if notification-service was down, the
  notification was lost. Now the event is durable — it waits in the topic and is
  processed when the consumer is back.
- **Stock signals** — `product-service` publishes `stock-events` (reserved, restored,
  updated, low-stock). `notification-service` monitors them and raises low-stock alerts.
  New consumers (analytics, audit) could subscribe later **without touching
  product-service** — the core benefit of event-driven design.

---

## Kafka topics

| Topic          | Producer          | Key         | Partitions | Event types                                                             |
|----------------|-------------------|-------------|------------|------------------------------------------------------------------------|
| `order-events` | order-service     | `userId`    | 3          | `ORDER_PLACED`, `ORDER_CONFIRMED`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED` |
| `stock-events` | product-service   | `productId` | 3          | `STOCK_RESERVED`, `STOCK_RESTORED`, `STOCK_UPDATED`, `LOW_STOCK_ALERT`  |

**Why a partition key?** Kafka guarantees message order *within a partition*, and all
records with the same key go to the same partition. Keying `order-events` by `userId`
means one user's events are always consumed in the order they happened. Keying
`stock-events` by `productId` keeps each product's history ordered.

## Consumer groups (in notification-service)

| Group                 | Topic          | What it does                                                     |
|-----------------------|----------------|-----------------------------------------------------------------|
| `notification-group`  | `order-events` | Saves the in-app notification and sends the email               |
| `stock-monitor-group` | `stock-events` | On `LOW_STOCK_ALERT`, records a broadcast admin notification    |

Two **independent** groups: they track their own offsets and never interfere. Scale a
group horizontally by running more instances — Kafka rebalances the 3 partitions across
them automatically, and each message is handled by exactly one member of the group.

---

## Event flow example — placing an order

```
Client ──POST /api/orders──> api-gateway ──> order-service
                                                 │
                    (SYNC Feign) check-and-reserve │ ──> product-service  [atomic stock decrement]
                                                 │ <── reservation OK
                                                 │
                                          save Order (DB)
                                                 │
                    (ASYNC Kafka) publish ORDER_PLACED ──> [order-events topic]
                                                 │
                                    HTTP 200 returned to client   ← client is NOT blocked on notify
                                                 
[order-events topic] ──> notification-service (notification-group)
                             ├── save in-app notification
                             └── send email

Meanwhile product-service also published STOCK_RESERVED (and LOW_STOCK_ALERT if the
reservation pushed the product to/below its threshold) ──> [stock-events topic]
                             └──> notification-service (stock-monitor-group)
```

Key point: the client's HTTP response no longer waits on notification-service, and a
notification-service outage cannot fail an order or lose a notification.

---

## Kafka concepts demonstrated in the code

- **Producer / `KafkaTemplate`** — `KafkaProducerConfig` + `OrderEventPublisher` /
  `StockEventPublisher`.
- **Topics, partitions, replicas** — `KafkaTopicConfig` (`NewTopic` beans, auto-created
  on startup by the producer service that owns the topic).
- **Partition keys & ordering** — publishing with `userId` / `productId` as the key.
- **Consumers, `@KafkaListener`, consumer groups** — `OrderEventListener`,
  `StockEventListener`.
- **Offsets & `auto-offset-reset=earliest`** — new groups start from the beginning.
- **JSON serialization across services without a shared library** — type headers are
  turned off on the producer; the consumer deserializes into its own mirror class of
  the same JSON shape.
- **Idempotent consumers** — each event carries an `eventId`; the consumer skips one it
  has already processed (Kafka is at-least-once, so duplicates can happen).
- **Poison-pill safety** — `ErrorHandlingDeserializer` wraps the JSON deserializer so a
  malformed message doesn't crash the consumer.

A production hardening noted in the code (`StockEventPublisher`): use the
**Transactional Outbox** pattern so an event is never published for a transaction that
later rolls back.

---

## Running it

### Prerequisites
- JDK 21, Maven 3.9+ (only if running services outside Docker)
- Docker + Docker Compose

### With Docker Compose (recommended)
```bash
cp .env .env      # then edit .env and set real values
docker compose up --build
```
Startup order is handled by health checks: Postgres, Redis, and **Kafka** come up first,
then Eureka, the gateway, and the services. First build downloads dependencies and can
take a few minutes.

Once healthy:
- Eureka dashboard: http://localhost:8761
- API gateway:      http://localhost:8080

### Verifying Kafka is working
Watch the logs — you should see the producer log lines
(`Published ORDER_PLACED ... -> partition N, offset M`) and the consumer log lines
(`Consuming ORDER_PLACED ...`) after you place an order.

Optional — inspect topics from inside the broker container:
```bash
docker exec -it oms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it oms-kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic order-events --from-beginning
```

### Running a single service in IntelliJ
Each service reads `KAFKA_BOOTSTRAP` (default `localhost:9092`), `DB_*`, `EUREKA_URL`,
etc. from environment variables with sensible localhost defaults. You'll need Postgres,
Redis, and Kafka reachable on localhost (e.g. `docker compose up postgres redis kafka`).

---

## Version notes

- **Spring Boot 3.3.13** (final OSS patch of the 3.3 line), **Java 17**,
  **Spring Cloud 2023.0.6** ("Leyton"). This Spring Cloud train is built and tested
  against Spring Boot 3.3.x, so the pairing is the officially supported one.
- **Spring Cloud Gateway** uses the classic `spring-cloud-starter-gateway` starter and
  the classic `spring.cloud.gateway.routes[...]` route configuration.
- **spring-kafka** version is managed by the Spring Boot 3.3.13 parent (resolves to the
  Spring Kafka 3.2.x line) — intentionally not pinned.
- **JJWT** pinned at 0.12.5.
- Dockerfiles use `maven:3.9-eclipse-temurin-17` (build) and
  `eclipse-temurin:17-jre-alpine` (runtime).

---

## Project layout

```
orderManagementSystem/
├── docker-compose.yml          # all infra + services (incl. kafka in KRaft mode)
├── .env.example                # copy to .env and fill in
├── db-init/                    # creates the 3 per-service databases
├── eureka-server/
├── api-gateway/
├── product-service/            # + event/  config/ (Kafka producer)
├── order-service/              # + event/  config/ (Kafka producer)
└── notification-service/       # + event/ (Kafka consumers) config/
```
