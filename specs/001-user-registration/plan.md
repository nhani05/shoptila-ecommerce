# Implementation Plan: US-01 — Đăng ký tài khoản khách hàng mới

**Branch**: `feature/us-01-auth-register` (thư mục thiết kế: `001-user-registration`) | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-user-registration/spec.md`

## Summary

Cho phép khách vãng lai tạo tài khoản khách hàng qua `POST /api/v1/auth/register`. Backend xử lý
chuẩn hóa email, Bean Validation, băm BCrypt strength 12, gán `ROLE_CUSTOMER` và trạng thái
`ACTIVE`, lưu vào bảng `auth_users` bằng Flyway `V1__init.sql`, và trả `201 Created` với DTO không
chứa mật khẩu/hash. Lỗi trả về RFC 7807 `ProblemDetail` (`400` có `errors[]` theo trường, `409` khi
email trùng, `500` không lộ nội bộ). Frontend React + Vite + TypeScript cung cấp form đăng ký có
validation tức thời, gọi `axiosClient`, ánh xạ lỗi ProblemDetail vào từng trường, và chuyển hướng
sang `/login` sau thành công.

Repo hiện chỉ có khung thư mục (`.gitkeep`), **chưa có** `pom.xml`, `package.json`, migration hay
`compose.yaml`; kế hoạch này bao gồm cả việc khởi tạo tối thiểu hai dự án và PostgreSQL dev để
tính năng chạy được đầu-cuối.

## Technical Context

**Language/Version**: Java 17 (JDK 17.0.21), TypeScript 5.9.3, Node.js 24 (dev)

**Primary Dependencies**: Spring Boot 3.5.16 (Web MVC, Data JPA, Validation, Security), springdoc-openapi 2.8.16, Flyway (Boot-managed, kèm `flyway-database-postgresql`), PostgreSQL driver; React 19.3.1, React Router DOM 7.18.5, Axios 1.21.1, Vite 8.3.8, `@vitejs/plugin-react` 6.3.0

**Storage**: PostgreSQL 16 qua Docker Compose; Flyway `V1__init.sql` tạo `auth_users`

**Testing**: JUnit 5 + Mockito + MockMvc + Testcontainers PostgreSQL (`spring-boot-testcontainers`); Vitest 5.1.0 + `@testing-library/react` 16.3.3 + jsdom

**Target Platform**: Linux server; trình duyệt hiện đại (desktop/tablet/mobile)

**Project Type**: Web application — backend modular monolith (`backend/`) + frontend SPA (`frontend/`)

**Performance Goals**: P95 phản hồi đăng ký < 2 giây (SC-005); BCrypt strength 12 có thể tốn ~0,2–0,5 giây CPU/hash — chấp nhận được để chống brute-force

**Constraints**: Không có secret mới; cấu hình đọc từ biến môi trường; API stateless, không cookie phiên; lỗi theo RFC 7807; không log PII/mật khẩu; không dùng công nghệ Giai đoạn 2–4

**Scale/Scope**: MVP Giai đoạn 1; 1 endpoint công khai, 1 trang UI, 1 bảng dữ liệu; tải mục tiêu hàng trăm đăng ký/ngày, không cần cache/queue

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Nguyên tắc / ràng buộc | Đánh giá | Bằng chứng thiết kế |
|---|---|---|---|
| I | Modular monolith & ranh giới Giai đoạn 1 | **PASS** | Chỉ module `auth` + hạ tầng `common`; không WebSocket/Kafka/Redis/outbox/microservice. `services/*` không bị chạm. |
| II.1 | Giao tiếp chéo module chỉ qua `api/` | **PASS** | `auth` → `common` (được phép); `common` không import `auth`. Không có nhu cầu gọi module khác ở US-01. |
| II.2 | Không quan hệ JPA chéo module | **PASS** | `User` là entity duy nhất; không `@ManyToOne`/`@OneToMany`/`@JoinColumn`; vai trò là enum, không bảng ngoài. |
| II.3 | Tiền tố bảng theo module | **PASS** | Bảng duy nhất: `auth_users`. |
| II.4 | Cách ly `common` | **PASS** | `GlobalExceptionHandler`, `SecurityConfig`, validation ở `common`; không phụ thuộc nghiệp vụ. |
| II.5 | DTO + `/api/v1` + ProblemDetail | **PASS** | `RegisterRequest`/`RegisterResponse` record; endpoint `/api/v1/auth/register`; lỗi RFC 7807. |
| III | BigDecimal/số nguyên VND | **N/A** | Không xử lý tiền. |
| IV | VNPAY IPN là nguồn xác nhận duy nhất | **N/A** | Không chạm payment. |
| V | Chống bán vượt tồn kho | **N/A** | Không chạm inventory. |
| VI | React + Vite + TS, feature-sliced, `axiosClient` | **PASS** | `frontend/src/features/auth/**`; gọi API duy nhất qua `shared/api/axiosClient.ts`; validation client song song backend; responsive + nhãn/aria. |
| Tech | Stack cố định; Flyway; secret từ env; RBAC backend; Swagger; tài liệu | **PASS** | Boot 3/Java 17/PG/Flyway; không hardcode secret; `ROLE_CUSTOMER` gán server-side; `@Operation`/`@Tag`; cập nhật README khi hoàn tất. |
| Process | Spec → Clarify → Plan → Tasks → Implement; TDD Critical Tier; cổng test | **PASS** | `spec.md` không còn marker; auth không thuộc Critical Tier tiền/tồn kho nhưng vẫn có unit + integration + concurrency; `./mvnw test` là cổng. |
| Security | Không log/lộ mật khẩu; fail-closed; validate biên | **PASS** | BCrypt 12; DTO không có hash; log không PII; filter chain `denyAll` mặc định; kiểm tra 72 byte. |

**Kết luận gate**: Không có vi phạm; **Complexity Tracking để trống**. Hai điểm được ghi nhận có
chủ đích nhưng không vi phạm:
1. **Không có rate limiting** trên endpoint đăng ký — spec Assumptions đã loại khỏi Giai đoạn 1;
   rủi ro brute-force được ghi vào mục Risks, xử lý khi hardening.
2. **Một cột `role` thay vì bảng vai trò chuẩn hóa** — quyết định R-01, đáp ứng MVP một vai
   trò/tài khoản và có đường nâng cấp bằng migration mới.

### Re-check sau Phase 1 (thiết kế chi tiết)

Thiết kế trong `data-model.md` và `contracts/auth-register.openapi.yaml` không làm thay đổi kết
luận: không phát sinh module mới, không có quan hệ JPA chéo module, không thêm công nghệ ngoài
stack, không có secret trong mã, và hợp đồng lỗi thống nhất RFC 7807. **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-registration/
├── spec.md                                # Đặc tả tính năng (Stage 1)
├── plan.md                                # File này (Stage 2)
├── research.md                            # Quyết định kỹ thuật R-01…R-10
├── data-model.md                          # Lược đồ, entity, ma trận validation
├── quickstart.md                          # Kịch bản xác minh đầu-cuối
├── checklists/requirements.md             # Checklist chất lượng spec
├── contracts/auth-register.openapi.yaml   # Hợp đồng HTTP + ProblemDetail
└── tasks.md                               # Sẽ tạo bởi /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/                 # Maven wrapper
└── src/
    ├── main/
    │   ├── java/com/example/shop/
    │   │   ├── ShopApplication.java
    │   │   ├── auth/
    │   │   │   ├── domain/
    │   │   │   │   ├── User.java                 # entity auth_users
    │   │   │   │   └── Role.java                 # enum ROLE_CUSTOMER/ROLE_ADMIN
    │   │   │   ├── repository/
    │   │   │   │   └── UserRepository.java       # existsByEmail
    │   │   │   ├── service/
    │   │   │   │   └── AuthService.java          # register(): normalize/hash/persist/map
    │   │   │   ├── web/
    │   │   │   │   ├── AuthController.java       # POST /api/v1/auth/register
    │   │   │   │   └── dto/
    │   │   │   │       ├── RegisterRequest.java  # record + compact constructor
    │   │   │   │       └── RegisterResponse.java # không có hash
    │   │   │   └── api/                          # trống ở US-01 (AuthApi cho module khác sau này)
    │   │   └── common/
    │   │       ├── config/SecurityConfig.java    # stateless, fail-closed, BCrypt(12)
    │   │       ├── exception/
    │   │       │   ├── EmailAlreadyExistsException.java
    │   │       │   └── ProblemDetailsExceptionHandler.java  # @RestControllerAdvice
    │   │       └── validation/
    │   │           ├── MaxUtf8Bytes.java         # annotation
    │   │           └── Utf8BytesValidator.java   # validator
    │   └── resources/
    │       ├── application.yml                   # env-first, không secret
    │       └── db/migration/V1__init.sql         # auth_users
    └── test/java/com/example/shop/
        ├── auth/
        │   ├── service/AuthServiceTest.java               # unit, Mockito
        │   ├── web/AuthRegisterIntegrationTest.java       # Testcontainers + MockMvc
        │   └── web/RegistrationConcurrencyTest.java       # 2 luồng cùng email
        └── support/PostgresTestSupport.java               # container + @ServiceConnection

frontend/
├── package.json, vite.config.ts, tsconfig.json, tsconfig.node.json, index.html
└── src/
    ├── main.tsx
    ├── app/
    │   ├── App.tsx
    │   └── router.tsx                            # /register, /login (placeholder)
    ├── shared/
    │   └── api/
    │       ├── axiosClient.ts                    # baseURL /api/v1, chỗ nối JWT ở US-02
    │       └── problemDetails.ts                 # type + hàm ánh xạ lỗi theo trường
    └── features/auth/
        ├── api/registerUser.ts                   # POST /auth/register
        ├── types.ts                              # RegisterRequest/RegisterResponse/ProblemDetail
        ├── validation.ts                         # pure validators + password strength
        ├── hooks/useRegister.ts                  # idle/loading/success/error
        ├── components/
        │   ├── RegisterForm.tsx
        │   └── PasswordStrengthMeter.tsx
        ├── pages/
        │   ├── RegisterPage.tsx
        │   └── LoginPage.tsx                     # placeholder cho chuyển hướng
        └── __tests__/
            ├── validation.test.ts
            └── RegisterForm.test.tsx

compose.yaml                 # PostgreSQL dev cho US-01 (mở rộng backend/frontend ở Stage 6)
.env.example                 # DB_* + placeholder JWT_*/VNPAY_* (không secret thật)
```

**Structure Decision**: Giữ nguyên cấu trúc package-by-feature đã được hiến chương và `AGENTS.md`
quy định. Module `auth` sở hữu toàn bộ `domain/`, `repository/`, `service/`, `web/`. Các thành phần
dùng chung (bảo mật, xử lý lỗi, validation kỹ thuật) nằm ở `common` và không phụ thuộc `auth`.
Frontend theo feature-sliced: `features/auth` chứa toàn bộ UI/logic đăng ký; `shared/api` là cổng
gọi HTTP duy nhất.

## Phase 0 — Research (tóm tắt)

Đầy đủ trong [research.md](./research.md). Các quyết định chính:

| ID | Quyết định | Ảnh hưởng |
|---|---|---|
| R-01 | Một cột `auth_users.role` + enum, không bảng vai trò | Lược đồ & entity đơn giản; nâng cấp nhiều vai trò bằng migration sau |
| R-02 | Email chuẩn hóa trim+lowercase; `UNIQUE` + `CHECK (email = lower(email))`; bắt `DataIntegrityViolationException` | Chống trùng do hoa/thường và đua đồng thời |
| R-03 | BCrypt(12) + từ chối mật khẩu > 72 **byte** UTF-8 | Tránh `IllegalArgumentException`/500 theo CVE-2025-22228 |
| R-04 | `ProblemDetail` + `errors[]` qua `ResponseEntityExceptionHandler` | Hợp đồng lỗi thống nhất, frontend ánh xạ theo trường |
| R-05 | Stateless, CSRF off, fail-closed; permit register/docs/`/error` | Không chặn nhầm; không fail-open |
| R-06 | Chuẩn hóa trong compact constructor record; DTO không có `role`/`isActive` | Validation chạy trên giá trị đã chuẩn hóa; chống tự nâng quyền |
| R-07 | `POST /api/v1/auth/register`; 201/400/409; camelCase; `Instant` | Hợp đồng ổn định, không trả `Location` giả |
| R-08 | Boot 3.5.16, springdoc 2.8.16, React 19.3.1, Vite 8.3.8, TS 5.9.3 | Khởi tạo dự án đúng dòng hiến chương |
| R-09 | Unit + Testcontainers integration + concurrency + Vitest | Chứng minh cả hành vi lẫn ràng buộc CSDL |
| R-10 | Log có cấu trúc, không PII | FR-016, không rò rỉ email/mật khẩu |

Không còn `NEEDS CLARIFICATION`.

## Phase 1 — Design & Contracts

Đầu ra:

- **[data-model.md](./data-model.md)** — DDL `V1__init.sql`, ánh xạ entity/cột, ma trận validation
  (email/mật khẩu/họ tên/điện thoại), ánh xạ response, bất biến, ghi chú ngoài phạm vi seed.
- **[contracts/auth-register.openapi.yaml](./contracts/auth-register.openapi.yaml)** — OpenAPI 3.1:
  request/response, 201/400/409/415/500, `ProblemDetail`, `ValidationProblemDetail`, ví dụ thực tế.
  Đây là nguồn cho frontend types và Swagger.
- **[quickstart.md](./quickstart.md)** — kịch bản chạy và xác minh: 201, 409, 400 nhiều lỗi, 72 byte,
  chống gán `ROLE_ADMIN`, kiểm tra CSDL, luồng trình duyệt, lệnh test/build.

## Thiết kế bảo mật (áp dụng security-and-hardening)

### Ranh giới tin cậy & tài sản

| Ranh giới | Dữ liệu không tin cậy | Kiểm soát |
|---|---|---|
| HTTP `POST /api/v1/auth/register` | email, mật khẩu, họ tên, điện thoại, thuộc tính lạ (`role`, `isActive`) | Bean Validation, chuẩn hóa DTO, allowlist trường, bỏ qua thuộc tính lạ, kiểm tra 72 byte |
| Tầng lưu trữ | Giá trị đã kiểm tra | Tham số hóa JPA, `UNIQUE`, `CHECK`, `@Transactional` |
| Phản hồi HTTP | — | DTO allowlist, không hash/PII, lỗi 500 chung |
| Log | — | Chỉ event + userId; không email/mật khẩu/payload |

Tài sản: thông tin đăng nhập (email + mật khẩu), quyền tài khoản, PII (họ tên, điện thoại).

### STRIDE (rút gọn)

| Mối đe dọa | Kịch bản | Giảm thiểu |
|---|---|---|
| Spoofing | Kẻ tấn công tạo tài khoản với email người khác | Giới hạn ở Phase 1: chưa xác minh email (đã ghi Assumptions); không cấp phiên tự động |
| Tampering | Client gửi `role=ROLE_ADMIN`, `isActive=false` | DTO không khai báo các trường này; server gán cứng `ROLE_CUSTOMER`, `ACTIVE`; test hồi quy |
| Repudiation | Không có dấu vết đăng ký | Log sự kiện thành công/thất bại (không PII) |
| Information disclosure | Hash/mật khẩu lọt response hoặc log; lỗi 500 lộ stack trace | DTO riêng, logging policy, handler 500 chung |
| Denial of service | Spam đăng ký, mật khẩu cực dài gây CPU/exception | Giới hạn 72 byte trước khi băm; **rate limiting hoãn có ghi nhận** |
| Elevation of privilege | Tự gán role admin | Như Tampering; RBAC backend khi có endpoint quản trị |

### Kiểm soát bắt buộc khi triển khai

- Mật khẩu chỉ đi vào `passwordEncoder.encode()`; không `toString`, không log, không trả DTO.
- `SecurityFilterChain` fail-closed; permit `/error` và dispatch `ERROR` để ProblemDetail không bị
  403.
- Không CORS wildcard; không token ở `localStorage` (quyết định lưu token thuộc US-02).
- `GlobalExceptionHandler` không bao giờ trả `ex.getMessage()` của lỗi không xác định cho client.
- Response header bảo mật mặc định của Spring Security được giữ nguyên (verify ở Stage 4 bằng
  DevTools).

## Hợp đồng giữa các tầng

```text
React RegisterForm
  → validation.ts (chặn sớm, UX)
  → registerUser()  [axiosClient, baseURL /api/v1]
  → POST /api/v1/auth/register (JSON)
  → AuthController (@Valid RegisterRequest)
  → AuthService (@Transactional: existsByEmail → encode → User.createCustomer → saveAndFlush)
  → UserRepository → PostgreSQL auth_users
  ← RegisterResponse (201)  |  ProblemDetail (400/409/500)
  → problemDetails.ts ánh xạ errors[] → lỗi từng trường
  → thông báo thành công + chuyển /login sau 1,5s
```

## Rủi ro & theo dõi

| Rủi ro | Mức | Xử lý |
|---|---|---|
| Không có rate limiting/anti-bot trên đăng ký | Trung bình | Đã ghi Assumptions; theo dõi ở giai đoạn hardening, dùng shared store khi >1 instance |
| Chưa xác minh email → tài khoản giả | Trung bình | Giai đoạn 2; hiện không cấp quyền cao hay giao dịch tiền cho tài khoản chưa xác minh |
| BCrypt strength 12 chậm trên CI yếu | Thấp | Tune strength nếu p95 vượt SC-005; ghi lại quyết định |
| Testcontainers cần Docker trong CI | Thấp | Tài liệu hóa điều kiện; CI cấu hình Docker (Stage 6) |
| `denyAll` làm endpoint tương lai trả 403 | Thấp (có chủ đích) | Mỗi tính năng mới phải khai báo quyền tường minh — fail-closed |

## Complexity Tracking

Không có vi phạm hiến chương cần biện minh.

## Bước tiếp theo

Chạy `/speckit-tasks` để sinh `tasks.md` theo thứ tự phụ thuộc. Sau khi hoàn tất Stage 2, lưu tiến
độ vào các file Spec-Kit và `/clear` trước khi sang Stage 3 (Implement).
