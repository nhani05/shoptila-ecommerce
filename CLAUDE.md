# CLAUDE.md — Shoptila Workflow & Skill Orchestration

Read and enforce all architectural and domain rules in `@AGENTS.md`. This file governs how Claude Code orchestrates the **6-Stage End-to-End Lifecycle** for **Shoptila** using **Spec-Kit** and `.claude/skills/`.

## 1. Mandatory 6-Stage Lifecycle Routing

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
  - `git-workflow-and-versioning`: Create clean module-scoped commits (`feat(order): ...`, `test(payment): ...`) on `feature/<module>-<desc>`.
  - `ci-cd-and-automation`: Verify `docker compose up --build`, Flyway clean startup (`V1__init.sql`, `V2__seed_data.sql`), Nginx `/api` proxy, and `.env.example` completeness.
  - `documentation-and-adrs`: Sync `README.md`, Swagger UI annotations, sequence diagrams (`docs/sequence/`), and `docs/adr/` if architectural trade-offs were made.
  - `shipping-and-launch`: Run final pre-release verification checklist (including VNPAY IPN tunnel readiness and demo credential warnings).

---

## 2. Context & Execution Hygiene
- **Stage Checkpoints:** Upon completing **Stage 2 (Plan/Tasks)** or **Stage 3 (Implement)**, persist all progress into Spec-Kit markdown files and instruct the user to run `/clear` before starting the next stage.
- **Mandatory Test Gate:** Never mark any `inventory`, `order`, or `payment` task complete until `InventoryConcurrencyTest`, `VnpaySignatureTest`, `IpnIdempotencyTest`, and `OrderFlowTest` pass via `./mvnw test`.