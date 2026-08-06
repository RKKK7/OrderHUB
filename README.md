# 📦 OrderHub — Microservices Order Management System

A distributed order management platform built with **Spring Boot microservices**, featuring Redis caching with versioned cache-busting, atomic stock reservation, Docker Compose orchestration, and 71 automated tests.

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                      React Frontend                     │
│                    (localhost:5173)                      │
└────────────────────────┬───────────────────────────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │    API Gateway      │
              │     (port 8080)     │
              │  JWT + Role Headers │
              └──────────┬──────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
  ┌──────────────┐┌─────────────┐┌──────────────────┐
  │   Product    ││   Order     ││  Notification    │
  │   Service    ││   Service   ││  Service         │
  │  (port 8081) ││ (port 8082) ││  (port 8083)     │
  │              ││             ││                  │
  │  Products    ││  Users      ││  Notifications   │
  │  Inventory   ││  Orders     ││  Email (SMTP)    │
  │  Redis Cache ││  OrderItems ││                  │
  └──────┬───────┘└──────┬──────┘└────────┬─────────┘
         │               │                │
         ▼               ▼                ▼
  ┌──────────┐    ┌──────────┐     ┌──────────┐
  │  Redis   │    │ PostgreSQL│     │ PostgreSQL│
  │          │    │ (order_db)│     │(notif_db) │
  └──────────┘    └──────────┘     └──────────┘
         │
  ┌──────────┐
  │PostgreSQL│
  │(product_ │
  │   db)    │
  └──────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.3, Spring Cloud 2023.0.2 |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Inter-Service Communication | Spring Cloud OpenFeign |
| Authentication | JWT (jjwt 0.12.5) with role-based claims |
| Database | PostgreSQL 16 (database-per-service pattern) |
| Caching | Redis 7 with versioned cache-busting |
| Testing | JUnit 5 + Mockito (71 tests) |
| Containerization | Docker + Docker Compose |
| Frontend | React 18, Vite, Tailwind CSS, React Router v6 |

## Services Overview

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| `eureka-server` | 8761 | — | Service discovery registry |
| `api-gateway` | 8080 | — | JWT validation, role extraction, routing, CORS |
| `product-service` | 8081 | `product_db` | Product catalog, inventory, Redis caching |
| `order-service` | 8082 | `order_db` | Users, orders, auth (JWT signing), Feign orchestrator |
| `notification-service` | 8083 | `notification_db` | In-app + email notifications (leaf node) |

## API Endpoints

### Product Service — Public (No Auth Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/products` | List products (supports `?category=`, `?search=`, `?sort=`, `?page=`, `?limit=`) |
| `GET` | `/api/products/{id}` | Product detail |
| `GET` | `/api/products/categories` | List all categories |

### Product Service — Admin Only

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/products/manage` | All products with exact stock (admin view) |
| `GET` | `/api/products/low-stock` | Products below stock threshold |
| `POST` | `/api/products` | Create product |
| `PUT` | `/api/products/{id}` | Update product |
| `PUT` | `/api/products/{id}/stock` | Update stock quantity |
| `PUT` | `/api/products/{id}/status` | Activate/deactivate product |

### Product Service — Internal (Feign Only, Not Gateway-Routed)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/internal/products/check-and-reserve` | Atomic stock check + reservation |
| `POST` | `/internal/products/restore-stock` | Restore stock (cancellation/compensation) |
| `GET` | `/internal/products/{id}` | Product lookup by ID |
| `GET` | `/internal/products/batch?ids=` | Batch product lookup |
| `GET` | `/internal/products/low-stock` | Low stock products (for admin dashboard) |

### Order Service — Auth (No Auth Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/users/register` | Register (user or admin with secret) |
| `POST` | `/api/users/login` | Login (returns JWT with role claim) |

### Order Service — User

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/users/me` | Current user profile |
| `PUT` | `/api/users/me` | Update profile (name, phone, address) |
| `POST` | `/api/orders` | Place order |
| `GET` | `/api/orders/my` | User's order history |
| `GET` | `/api/orders/{id}` | Order detail (own orders only) |
| `PUT` | `/api/orders/{id}/cancel` | Cancel order (PENDING/CONFIRMED only) |

### Order Service — Admin Only

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/orders` | All orders (filterable by `?status=`) |
| `PUT` | `/api/admin/orders/{id}/status` | Update order status |
| `PUT` | `/api/admin/orders/{id}/cancel` | Cancel any order |
| `GET` | `/api/admin/stats` | Dashboard stats (revenue, top products, low stock) |

### Notification Service — Authenticated Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/notifications` | List notifications with unread count |
| `PUT` | `/api/notifications/{id}/read` | Mark one as read |
| `PUT` | `/api/notifications/read-all` | Mark all as read |

### Notification Service — Internal (Feign Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/internal/notifications/send` | Create notification + send email |

## Order Status State Machine

```
PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED
   │              │
   └──────────────┴────────► CANCELLED
                              (only from PENDING or CONFIRMED)
```

Transitions are enforced in code — invalid state changes return `400 Bad Request`.

## Redis Caching Strategy

Caching is implemented in `product-service` only using a **versioned cache-busting** pattern:

| Cached Data | Key Pattern | Invalidation | TTL |
|------------|-------------|-------------|-----|
| Single product | `products:id:{productId}` | Direct `DEL` on update | 10 min |
| Product list | `products:list:v{n}:{category}:{sort}:{page}` | Version bump | 5 min |
| Search results | `products:search:v{n}:{query}:{sort}:{page}` | Version bump | 2 min |
| Category list | `products:categories:v{n}` | Version bump | 10 min |
| Stock levels | **Never cached** | N/A | N/A |

**How version-busting works:** A global counter (`products:cache-version`) is incremented on every product write. All list/search cache keys include this version — old keys become unreachable instantly. O(1) invalidation regardless of cache size.

**Local dev:** Caching is disabled by default (`CACHE_ENABLED=false`). The app uses `NoOpProductCacheService` which skips Redis entirely. Docker Compose sets `CACHE_ENABLED=true`.

## Testing

**71 tests total**, all using JUnit 5 + Mockito with zero infrastructure dependencies:

| Service | Tests | Coverage |
|---------|-------|----------|
| `api-gateway` | 18 | JWT validation, public/protected paths, role extraction |
| `product-service` | 18 | CRUD, admin enforcement, atomic stock reservation, rollback |
| `order-service` | 23 | Register, login, place order, cancel, compensation, state machine |
| `notification-service` | 12 | Send notification, email trigger, mark read, auth |

Run all tests:
```bash
cd <service-folder>
mvn test
```

Tests also run during Docker image builds — a failing test blocks the image from being created.

## Running the Project

### Prerequisites
- Java 17
- Maven
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL (for local dev only)

### Option 1 — Docker Compose (Recommended)

**Start all 7 containers with one command:**

```bash
docker-compose up --build
```

This automatically:
- Creates 3 PostgreSQL databases (`product_db`, `order_db`, `notification_db`)
- Starts Redis
- Starts Eureka, waits for healthy
- Starts all 3 services in dependency order
- Runs all 71 tests during build — broken code never reaches a container

**Verify:** Open `http://localhost:8761` — all 4 services should show `UP`.

**Start the frontend:**

Copy the frontend from the OrderHUB-Frontend repository
```bash
cd oms-frontend
npm install
npm run dev
```

Open `http://localhost:5173`

**Stop everything:**
```bash
docker-compose down        # stop containers
docker-compose down -v     # also wipe database data
```

### Option 2 — Local Development (IntelliJ)

**1. Create databases:**
```sql
CREATE DATABASE product_db;
CREATE DATABASE order_db;
CREATE DATABASE notification_db;
```

**2. Start services in order:**
```
eureka-server    → no env vars needed
api-gateway      → JWT_SECRET, CLIENT_URL
product-service  → DB_PASSWORD, DB_URL, DB_USER
order-service    → DB_PASSWORD, DB_URL, DB_USER, JWT_SECRET, ADMIN_SECRET
notification-service → DB_PASSWORD, DB_URL, DB_USER, EMAIL_USER, EMAIL_PASS
```

**3. Start frontend:**
```bash
cd oms-frontend
npm install
npm run dev
```

## Environment Variables

### Required for Docker Compose (`.env` file)

| Variable | Description | Example |
|----------|-------------|---------|
| `DB_PASSWORD` | PostgreSQL password | `yourpassword` |
| `JWT_SECRET` | JWT signing key (min 32 chars) | `mK9pX3vL7n...` |
| `ADMIN_SECRET` | Admin registration secret | `OmsAdmin2026Secret` |
| `CLIENT_URL` | Frontend URL (for CORS) | `http://localhost:5173` |
| `EMAIL_USER` | Gmail address (for notifications) | `you@gmail.com` |
| `EMAIL_PASS` | Gmail app password | `abcdefghijklmnop` |

### Auto-Injected by Docker Compose (Not in `.env`)

| Variable | Value | Used By |
|----------|-------|---------|
| `DB_URL` | `jdbc:postgresql://postgres:5432/{db}` | All 3 services |
| `DB_USER` | `postgres` | All 3 services |
| `REDIS_HOST` | `redis` | product-service |
| `CACHE_ENABLED` | `true` | product-service |
| `EUREKA_URL` | `http://eureka-server:8761/eureka/` | All services |

## Project Structure

```
OrderHUB/
├── docker-compose.yml
├── .env                          (not committed — in .gitignore)
├── .gitignore
├── .dockerignore
├── db-init/
│   └── init-databases.sh
├── eureka-server/
├── api-gateway/
├── product-service/
├── order-service/
├── notification-service/
└── oms-frontend/
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| No shared library | Eliminates `.m2` sync issues — each service is fully self-contained |
| Users table in order-service | No split entity ownership — prevents "registered but profile missing" bugs |
| JWT role claim + gateway header injection | Zero Feign calls for authorization — role check is a header read |
| Atomic `UPDATE WHERE stock >= qty` | Prevents overselling at the database level — no application locking |
| Stock restore before status change | If restore fails, order stays unchanged — no inconsistent state |
| Compensation on failed order save | Auto-restores stock if order DB write fails after reservation |
| All DTO timestamps as String | Prevents Jackson/Redis `Instant` serialization failures |
| `CACHE_ENABLED=false` default | App runs without Redis locally — caching is opt-in via Docker |
| Notification stores `userEmail` | Leaf node never calls back — no circular service dependencies |
