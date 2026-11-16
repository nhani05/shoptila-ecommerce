# AGENTS.md — Shoptila Engineering Standards & Modular Monolith Rules

This file defines the non-negotiable architectural boundaries, domain invariants, and verification rules for all AI agents working on **Shoptila** (Tech E-Commerce with VNPAY Sandbox integration).

## 1. Tech Stack & Repository Layout
- **Current Phase:** Phase 1 (MVP Modular Monolith). Do not implement `[GĐ2]`, `[GĐ3]`, or `[GĐ4]` features (WebSocket, Kafka, Outbox, Microservices split) unless explicitly instructed.
- **Backend (`backend/`):** Java 17+, Spring Boot 3, Spring Security (JWT `jjwt`, BCrypt), Spring Data JPA, Bean Validation, `springdoc-openapi`, RFC 7807 `ProblemDetail`.
- **Database (`backend/src/main/resources/db/migration/`):** PostgreSQL + Flyway (`V1__init.sql`, `V2__seed_data.sql`). Never edit an already-applied Flyway migration; always create a new versioned script (`V3__...sql`).
- **Frontend (`frontend/`):** React, Vite, TypeScript, Axios, React Router, feature-sliced structure under `src/features/`.
- **Testing & Infra:** JUnit 5, Testcontainers (PostgreSQL), Docker Compose (`postgres`, `backend`, `frontend` via Nginx).
- **Spec-Kit Artifacts:** `.specify/memory/constitution.md`, `specs/<feature>/spec.md`, `plan.md`, `tasks.md`.

## 2. Modular Monolith Boundary Rules (Strict)
Backend packages under `com.example.shop` are divided by business domain: `common`, `auth`, `catalog`, `cart`, `inventory`, `order`, `payment`.
1. **Public API Only:** Cross-module communication MUST go exclusively through the target module's `api/` package (e.g., `CatalogApi`, `InventoryApi`, `OrderApi`, `PaymentApi`) or domain events. Never import `domain`, `repository`, or `service` classes from another module.
2. **Zero Cross-Module JPA Relations:** Never use `@ManyToOne` / `@OneToMany` across module boundaries; store foreign IDs only (e.g., `Long productId`, `Long orderId`).
3. **Table Prefixes:** Prefix all PostgreSQL tables by module (`auth_users`, `catalog_products`, `cart_items`, `inventory_stocks`, `order_orders`, `payment_transactions`) to preserve future microservice extraction.
4. **Common Isolation:** `common` must never depend on any business module. Token issuance lives in `auth`; token verification (`JwtVerifier`, `JwtAuthFilter`) lives in `common`.
5. **DTO Boundary:** Never expose JPA `@Entity` objects from controllers. Prefix all endpoints with `/api/v1` and return `ProblemDetail` (RFC 7807) for errors.

## 3. Critical Domain Invariants: Money, VNPAY & Inventory
Any code in `payment`, `inventory`, `order`, or `cart` is **Critical Tier** and must enforce these invariants:
- **Exact Currency Math:** Represent VND amounts strictly using `BigDecimal` (or `long` integer VND) via `common.util.MoneyUtils`. Never use `double` or `float`. Remember VNPAY requires amount multiplied by `100` (`amount * 100`).
- **Anti-Overselling Inventory:** Never read-then-write stock in Java memory. Use atomic conditional SQL updates (`UPDATE inventory_stocks SET quantity = quantity - :qty WHERE product_id = :id AND quantity >= :qty`) inside `@Transactional` when `OrderService` calls `InventoryApi.reserve()`. Roll back immediately if updated row count is `0`.
- **VNPAY Security & Truth Source:**
  - Verify `HMAC-SHA512` signatures using `VnpaySignatureVerifier` on sorted parameters for **both** Return URL and IPN.
  - **IPN (`/api/v1/payments/vnpay/ipn`) is the sole authoritative source** to mark an order `PAID`. Return URL is display-only; frontend must poll/fetch true order status from `GET /api/v1/orders/{id}`.
  - **IPN Idempotency:** Enforce `UNIQUE` constraint on `vnp_TxnRef` and conditional state transition (`PENDING` → `SUCCESS`) so duplicate VNPAY IPN retries execute `OrderApi.markPaid()` exactly once.
- **Expiry Ordering:** VNPAY payment URL expiration MUST be strictly shorter than `OrderExpiryJob` timeout so expired orders cannot be paid late.
- **Order State Machine:** Enforce valid transitions only via `OrderStateMachine`: `PENDING_PAYMENT → PAID → SHIPPING → COMPLETED`, or `PENDING_PAYMENT → CANCELLED` (which triggers `InventoryApi.release()`).

## 4. API, Validation & Git Conventions
- **Validation:** Enforce Bean Validation (`@Valid`, `@Positive`, `@NotBlank`) on backend request DTOs and client-side validation on frontend forms (reject negative quantities or non-numeric inputs).
- **RBAC:** Enforce `CUSTOMER` and `ADMIN` roles at the backend via Spring Security (`SecurityConfig` / `@PreAuthorize`), never relying solely on React `RoleGuard`.
- **Secrets:** Read `DB_*`, `JWT_*`, `VNPAY_*` strictly from environment variables. Never commit `.env` or hardcode `VNPAY_HASH_SECRET` / `JWT_SECRET`.
- **Git Workflow:** Branch naming `feature/<module>-<short-desc>` off `develop`. Commit messages scoped by module (e.g., `feat(payment): enforce vnpay ipn idempotency`).