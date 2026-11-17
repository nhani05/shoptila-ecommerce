# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Read and enforce all architectural and domain rules in `@AGENTS.md`. This file governs how Claude Code orchestrates the **6-Stage End-to-End Lifecycle** for **Shoptila** using **Spec-Kit** and `.claude/skills/`.

## Authoritative documents

Read these before changing behavior — they override this file on conflict:

- `AGENTS.md` — architecture boundaries, domain invariants, validation, git conventions.
- `.specify/memory/constitution.md` (v1.0.0) — binding project principles and quality gates.
- `README.md` (Vietnamese) — product scope, order/payment flow, planned API surface.

## Current state

This repository is at the **start of Phase 1 (MVP)**. Most backend packages and all frontend feature
folders exist only as `.gitkeep` scaffolds. Implemented so far: the Spring Boot entrypoint, Maven and
Vite/React/TypeScript scaffolds, `application.yml`, `.env.example`, and a PostgreSQL-only
`compose.yaml`. Production feature work is driven by `specs/001-user-registration/` (US-01: customer
registration), the active Spec-Kit feature recorded in `.specify/feature.json`.

Do **not** implement Phase 2–4 items already stubbed in the tree (`chat`, `notification`,
`common/outbox`, `payment/job`, `services/*`) unless the user explicitly asks and the constitution /
`plan.md` permits it. `services/*` is an empty placeholder for the future microservice split.

## Commands

### Infrastructure

```bash
cp .env.example .env                 # required: application.yml has no defaults for DB_*
docker compose up -d postgres        # compose.yaml currently defines only PostgreSQL 16
```

`compose.yaml` does not yet build backend/frontend images, so run the apps natively. Testcontainers
tests need a running Docker daemon.

### Backend (`backend/`, Java 17, Spring Boot 3.5.16)

```bash
cd backend
./mvnw test                                           # all tests
./mvnw test -Dtest=AuthRegisterIntegrationTest         # one test class
./mvnw test -Dtest='AuthServiceTest#methodName'        # one method (Surefire pattern)
./mvnw spring-boot:run                                # requires DB_URL/DB_USER/DB_PASSWORD in env
./mvnw package
```

To load the local env file before running:

```bash
set -a && source ../.env && set +a && ./mvnw spring-boot:run
```

There is no `application-dev.yml` / `application-prod.yml` despite the README's aspirational layout;
only `application.yml` exists. The README's `-Dspring-boot.run.profiles=dev` does not change
anything yet.

Key config: `spring.jpa.hibernate.ddl-auto=validate` (entities must match Flyway DDL),
`open-in-view=false`, Flyway migrations in `backend/src/main/resources/db/migration/`.

### Frontend (`frontend/`, React 19, Vite 8, TypeScript 5.9)

```bash
cd frontend
npm install
npm run dev                                          # http://localhost:5173, proxies /api -> :8080
npm test -- --run                                    # Vitest CI mode; plain `npm test` is watch mode
npm test -- --run src/features/auth/__tests__/validation.test.ts
npm run build                                        # tsc --noEmit && vite build
```

Vitest config lives inside `vite.config.ts` (jsdom, setup file `src/test/setup.ts`). No ESLint or
Prettier config exists, so `npm run build` (type-check) is the lint gate.

## Architecture

### Backend: modular monolith, package-by-feature

All code lives under `com.example.shop`, divided by business domain:
`common`, `auth`, `catalog`, `cart`, `inventory`, `order`, `payment` (plus Phase-2 stubs `chat`,
`notification`). Each business module owns its own `domain/`, `repository/`, `service/`, `web/`
(controllers + `web/dto/`), and possibly `api/`, `vnpay/`, `job/`.

Hard boundaries (enforced by review/ArchUnit later):

- Cross-module calls go **only** through the target module's `api/` package (`CatalogApi`,
  `InventoryApi`, `OrderApi`, `PaymentApi`) or domain events. Never import another module's
  `domain`, `repository`, `service`, `web`, or `job`.
- No cross-module JPA associations (`@ManyToOne`, `@OneToMany`, `@JoinColumn`); store foreign IDs
  (`Long productId`, `Long orderId`).
- Table names are prefixed by module: `auth_users`, `catalog_products`, `cart_items`,
  `inventory_stocks`, `order_orders`, `payment_transactions`.
- `common` is technical only and must not depend on business modules. Token **issuance** belongs to
  `auth`; token **verification** (`JwtVerifier`, `JwtAuthFilter`) belongs to `common`.
- Controllers return DTOs, never JPA entities. All endpoints are under `/api/v1`. Errors are RFC 7807
  `ProblemDetail`.
- Every schema change is a new Flyway version (`V3__...sql`); never edit an applied migration.

### Critical domain flow (order + VNPAY)

`OrderService` creates an order (`PENDING_PAYMENT`), snapshots product name/price into order lines,
then calls `InventoryApi.reserve()` in the same transaction using an atomic conditional update
(`UPDATE inventory_stocks SET quantity = quantity - :qty WHERE product_id = :id AND quantity >= :qty`);
zero rows updated means rollback. Only a valid **IPN** (`/api/v1/payments/vnpay/ipn`) may mark an
order `PAID`; the Return URL is display-only and the frontend re-fetches order status. IPN handling
must be idempotent via a unique `vnp_TxnRef` plus a conditional `PENDING → SUCCESS` transition so
`OrderApi.markPaid()` runs once. VNPAY payment URL expiry must be shorter than `OrderExpiryJob`'s
timeout.

Order state machine: `PENDING_PAYMENT → PAID → SHIPPING → COMPLETED`, or
`PENDING_PAYMENT → CANCELLED` (which calls `InventoryApi.release()`).

Money is always `BigDecimal`/integer VND via `common.util.MoneyUtils`; never `double`/`float`.
VNPAY amounts are multiplied by `100` in both directions. Order totals are recomputed server-side —
never trust client-supplied prices.

### Frontend: feature-sliced React SPA

`src/features/<domain>/` holds pages/components per feature; `src/shared/` holds cross-cutting code.
All API calls must go through `src/shared/api/axiosClient.ts` (baseURL `/api/v1`, JWT attached in
US-02). Routing belongs in `src/app/router.tsx` (planned), grouped public/customer/admin. Vite proxies
`/api` to `localhost:8080` in dev. Client-side validation is UX only; backend enforces RBAC and
validation.

## Spec-Kit workflow (mandatory for features)

The constitution requires Specify → Clarify → Plan → Tasks → Implement. **Do not write Java or
TypeScript for a feature before `plan.md` and `tasks.md` are approved.** Skills are invocable as
`/speckit-specify`, `/speckit-clarify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-implement`,
`/speckit-analyze`, `/speckit-checklist`, `/speckit-converge`, `/speckit-constitution`. Helper scripts
live in `.specify/scripts/bash/`; templates in `.specify/templates/`; features are numbered
sequentially under `specs/NNN-feature-name/`. `.specify/feature.json` points at the active feature.

Critical-tier code (`inventory`, `order`, `payment`, `cart`) is test-first. The constitution's gates
are `InventoryConcurrencyTest`, `VnpaySignatureTest`, `IpnIdempotencyTest`, `OrderFlowTest`, plus a
successful Vite build; these test classes are not written yet.

## Git

Branch from `develop` as `feature/<module>-<short-desc>`; commit messages are module-scoped, e.g.
`feat(payment): enforce vnpay ipn idempotency`, `test(auth): cover duplicate email`. The current
working branch is `feature/us-01-auth-register`. Never commit `.env`; only `.env.example` is tracked.

## Secrets and environment variables

`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT`, `DB_URL`, `JWT_*`, and `VNPAY_*` are read from the
environment. `spring.datasource` uses `${DB_URL}` etc. with no fallbacks, so the backend will not
start without them. VNPAY fields are placeholders until the payment feature; do not invent values.


## Mandatory 6-Stage Lifecycle Routing

Identify the active stage of the user's request and execute the exact Spec-Kit commands and skills below:

### Stage 1: Constitution / Specify / Clarify
- **Commands:** `/speckit.constitution` → `/speckit.specify` → `/speckit.clarify`
- **Skills:**
  - `idea-refine`: Invoke if a feature request drifts into Phase 2–4 scope (e.g., WebSocket chat, Kafka, microservices) to ruthlessly trim back to Phase 1 MVP.
- **Exit Gate:** `constitution.md` and `spec.md` are finalized with zero `[NEEDS CLARIFICATION]` markers and align with `README.md` Phase 1 boundaries.

### Stage 2: Plan / Tasks
- **Commands:** `/speckit.plan` → `/speckit.tasks`
- **Skills:**
  - `api-and-interface-design`: Define `/api/v1` REST contracts, `ProblemDetail` error codes, cross-module `api/` interfaces (`CatalogApi`, `InventoryApi`, `OrderApi`, `PaymentApi`), and Flyway schema changes in `plan.md`.
  - `security-and-hardening`: Threat-model JWT verification, `CUSTOMER`/`ADMIN` RBAC boundaries, and VNPAY HMAC-SHA512 signature/amount validation during design.
- **Exit Gate:** `plan.md` and dependency-ordered `tasks.md` are approved before writing any Java/TypeScript code.

### Stage 3: Implement
- **Commands:** `/speckit.implement`
- **Skills (Mandatory Baseline):**
  - `incremental-implementation`: Implement one task slice at a time from `tasks.md`; verify Maven compile and Vite build after each slice.
  - `test-driven-development`: Write failing JUnit 5 / Testcontainers tests (Red) before implementing service logic (Green), then refactor.
  - `source-driven-development`: Verify exact Spring Boot 3, `jjwt`, `springdoc`, and VNPAY Sandbox parameter specs from project dependencies/docs—never guess APIs.
  - `observability-and-instrumentation`: Log structured context (`orderId`, `vnp_TxnRef`, `userId`) for checkout, IPN callbacks, and expiry jobs without leaking secrets.
- **Skills (Conditional by Domain):**
  - `frontend-ui-engineering`: Activate when building `frontend/src/features/*` (catalog filters for price/brand/RAM/CPU, cart, checkout redirect, payment result polling, admin CRUD) ensuring responsive layouts and strict input validation.
  - `doubt-driven-development`: **MANDATORY** for all tasks in `inventory`, `order`, and `payment`. Adversarially verify race conditions on last-item stock, duplicate VNPAY IPN retries, tampered `vnp_SecureHash` / `vnp_Amount`, and `OrderExpiryJob` timing edge cases.

### Stage 4: Verify
- **Skills:**
  - `browser-testing-with-devtools`: Start the stack (`docker compose up` or dev servers) and verify end-to-end flows in a real browser (filter products → cart → checkout → VNPAY Sandbox redirect → return page polling order status → admin stock/order management) with zero console or 5xx network errors.
  - `debugging-and-error-recovery`: When any Testcontainers test (`InventoryConcurrencyTest`, `IpnIdempotencyTest`, `VnpaySignatureTest`, `OrderFlowTest`) or browser flow fails, isolate root cause systematically before patching.
- **Exit Gate:** `./mvnw test` passes completely and browser verification succeeds cleanly.

### Stage 5: Review
- **Skills:**
  - `code-review-and-quality`: Audit code against `spec.md`, `plan.md`, and `@AGENTS.md` (especially modular monolith `api/` boundaries, zero cross-module JPA relations, DTO usage).
  - `security-and-hardening`: Audit for IDOR on `/api/v1/orders/{id}`, missing `ADMIN` role checks, SQL/XSS injection, and `.env` / signature leaks.
  - `performance-optimization`: Verify PostgreSQL indexes on `catalog_products` (price, brand, RAM, CPU), eliminate N+1 JPA queries, and check frontend bundle/re-renders.
  - `code-simplification`: Simplify complex service methods and state transitions while keeping all tests green.

### Stage 6: Ship
- **Skills:**
  - `git-workflow-and-versioning`: CrThis file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
eate clean module-scoped commits (`feat(order): ...`, `test(payment): ...`) on `feature/<module>-<desc>`.
  - `ci-cd-and-automation`: Verify `docker compose up --build`, Flyway clean startup (`V1__init.sql`, `V2__seed_data.sql`), Nginx `/api` proxy, and `.env.example` completeness.
  - `documentation-and-adrs`: Sync `README.md`, Swagger UI annotations, sequence diagrams (`docs/sequence/`), and `docs/adr/` if architectural trade-offs were made.
  - `shipping-and-launch`: Run final pre-release verification checklist (including VNPAY IPN tunnel readiness and demo credential warnings).

---

## Context & Execution Hygiene
- **Stage Checkpoints:** Upon completing **Stage 2 (Plan/Tasks)** or **Stage 3 (Implement)**, persist all progress into Spec-Kit markdown files and instruct the user to run `/clear` before starting the next stage.
- **Mandatory Test Gate:** Never mark any `inventory`, `order`, or `payment` task complete until `InventoryConcurrencyTest`, `VnpaySignatureTest`, `IpnIdempotencyTest`, and `OrderFlowTest` pass via `./mvnw test`.