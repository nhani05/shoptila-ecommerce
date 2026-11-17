# Tasks: US-01 — Đăng ký tài khoản khách hàng mới

**Input**: Design documents from `/specs/001-user-registration/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Có. `plan.md` (R-09) và hiến chương yêu cầu unit + integration Testcontainers + concurrency cho backend, Vitest cho frontend; TDD được áp dụng (test đỏ trước khi hiện thực).

**Organization**: Task được nhóm theo từng phần ưu tiên của US-01 (US-01.1 → US-01.3) để mỗi phần có thể hiện thực, kiểm thử và demo độc lập.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Có thể chạy song song (file khác nhau, không phụ thuộc task chưa hoàn thành)
- **[Story]**: Phần ưu tiên của US-01 (`US-01.1`, `US-01.2`, `US-01.3`)
- Mọi task đều có đường dẫn file chính xác

## Path Conventions

- Backend: `backend/src/main/java/com/example/shop/`, `backend/src/test/java/com/example/shop/`
- Frontend: `frontend/src/`
- Tài nguyên: `backend/src/main/resources/`
- Tài liệu tính năng: `specs/001-user-registration/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Khởi tạo hai dự án và hạ tầng chạy dev. Repo hiện chỉ có khung `.gitkeep`.

- [ ] T001 Create backend Maven project in `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`: Spring Boot **3.5.16** parent, Java **17**, dependencies `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `springdoc-openapi-starter-webmvc-ui:2.8.16`, `flyway-core` + `flyway-database-postgresql`, `postgresql` (runtime), test scope `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`
- [ ] T002 Create Spring Boot entrypoint and configuration in `backend/src/main/java/com/example/shop/ShopApplication.java` and `backend/src/main/resources/application.yml`: datasource từ `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`; `jpa.hibernate.ddl-auto=validate`, `jpa.open-in-view=false`; Flyway bật; `server.error.include-message=never`, `include-stacktrace=never`, `include-binding-errors=never`
- [ ] T003 [P] Create local infrastructure in `compose.yaml` (PostgreSQL 16, `env_file: .env`, healthcheck, named volume) and `.env.example` (DB_NAME, DB_USER, DB_PASSWORD, DB_URL, JWT_* và VNPAY_* placeholders; ghi chú không commit `.env` và không điền secret thật)
- [ ] T004 [P] Create frontend scaffold in `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/tsconfig.node.json`, `frontend/index.html`, `frontend/src/main.tsx`, `frontend/src/app/App.tsx`: React **19.3.1**, React DOM **19.3.1**, React Router DOM **7.18.5**, Axios **1.21.1**, Vite **8.3.8**, `@vitejs/plugin-react` **6.3.0**, TypeScript **5.9.3**, Vitest **5.1.0**, `@testing-library/react` **16.3.3**, `@testing-library/user-event` **14.6.12**, jsdom; scripts `dev`, `build`, `preview`, `test`; proxy `/api` → `http://localhost:8080`
- [ ] T005 [P] Update `.gitignore` to ignore `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.env`, IDE files, while keeping `.env.example` tracked

**Checkpoint**: `./mvnw -version` chạy được; `npm install && npm run build` chạy được với App rỗng; `docker compose config` hợp lệ.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Hạ tầng dùng chung mà cả 3 phần của US-01 đều phụ thuộc: schema, entity, bảo mật, lỗi RFC 7807, validation kỹ thuật, HTTP client.

**⚠️ CRITICAL**: Không bắt đầu phần nào của US-01 trước khi hoàn thành phase này.

- [ ] T006 Create Flyway migration `backend/src/main/resources/db/migration/V1__init.sql` with table `auth_users` exactly as [data-model.md](./data-model.md): `id BIGSERIAL PK`, `email VARCHAR(150) NOT NULL`, `password_hash VARCHAR(255) NOT NULL`, `full_name VARCHAR(100) NOT NULL`, `phone VARCHAR(20)`, `role VARCHAR(30) NOT NULL DEFAULT 'ROLE_CUSTOMER'`, `is_active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`; constraints `uq_auth_users_email UNIQUE (email)`, `ck_auth_users_email_lower CHECK (email = lower(email))`, `ck_auth_users_role CHECK (role IN ('ROLE_CUSTOMER','ROLE_ADMIN'))`; index `idx_auth_users_created_at`
- [ ] T007 [P] Create `Role` enum in `backend/src/main/java/com/example/shop/auth/domain/Role.java` with exactly `ROLE_CUSTOMER`, `ROLE_ADMIN`
- [ ] T008 Create `User` JPA entity in `backend/src/main/java/com/example/shop/auth/domain/User.java`: `@Table(name = "auth_users")`, fields mapping exactly to T006 columns (`passwordHash` → `password_hash`, `active` → `is_active`, `createdAt` → `created_at`), `role` as `@Enumerated(EnumType.STRING)`, static factory `User.createCustomer(email, passwordHash, fullName, phone)` that hard-codes `ROLE_CUSTOMER` and `active = true`; no getter exposing raw password and no `passwordHash` in `toString()`
- [ ] T009 Create `UserRepository` in `backend/src/main/java/com/example/shop/auth/repository/UserRepository.java` extending `JpaRepository<User, Long>` with `boolean existsByEmail(String email)`; no cross-module JPA relations
- [ ] T010 [P] Create `SecurityConfig` in `backend/src/main/java/com/example/shop/common/config/SecurityConfig.java`: stateless session, CSRF disabled, form login/basic auth disabled, permit `POST /api/v1/auth/register`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health`, `/error`, and `DispatcherType.ERROR`; `anyRequest().denyAll()`; `@Bean PasswordEncoder` = `new BCryptPasswordEncoder(12)`
- [ ] T011 [P] Create `@MaxUtf8Bytes` annotation in `backend/src/main/java/com/example/shop/common/validation/MaxUtf8Bytes.java` and `Utf8BytesValidator` in `backend/src/main/java/com/example/shop/common/validation/Utf8BytesValidator.java` measuring `value.getBytes(StandardCharsets.UTF_8).length`; null values are valid (let `@NotBlank` handle them); message mặc định tiếng Việt "Mật khẩu không được vượt quá 72 byte"
- [ ] T012 [P] Create `EmailAlreadyExistsException` in `backend/src/main/java/com/example/shop/common/exception/EmailAlreadyExistsException.java` and `ProblemDetailsExceptionHandler` in `backend/src/main/java/com/example/shop/common/exception/ProblemDetailsExceptionHandler.java` extending `ResponseEntityExceptionHandler`: 400 cho `MethodArgumentNotValidException` với `errors: [{field, message}]` (tất cả lỗi) và `HttpMessageNotReadableException`; 409 `type=https://api.shoptila.com/errors/email-already-exists` cho `EmailAlreadyExistsException` và `DataIntegrityViolationException` chỉ khi constraint là `uq_auth_users_email`; 500 `type=https://api.shoptila.com/errors/internal-error` chung, không lộ message/stack trace; giữ `instance` do framework gắn
- [ ] T013 [P] Create frontend HTTP layer in `frontend/src/shared/api/axiosClient.ts` (axios instance `baseURL: '/api/v1'`, timeout, JSON headers; không đọc/ghi token ở `localStorage`) and `frontend/src/shared/api/problemDetails.ts` (types `ProblemDetail`, `FieldError`; `isProblemDetail`, `extractFieldErrors(problem): Record<string,string>`, `extractDetail`)
- [ ] T014 [P] Create Testcontainers support in `backend/src/test/java/com/example/shop/support/PostgresTestSupport.java`: PostgreSQL container `postgres:16-alpine` with `@ServiceConnection`, reusable base for integration tests

**Checkpoint**: `./mvnw test` biên dịch được (chưa có test của phần nào); `GET /v3/api-docs` mở được khi chạy app; migration V1 áp dụng thành công trên PostgreSQL.

---

## Phase 3: US-01.1 — Khách vãng lai đăng ký thành công (Priority: P1) 🎯 MVP

**Goal**: Endpoint `POST /api/v1/auth/register` tạo tài khoản `ROLE_CUSTOMER`/`ACTIVE` với mật khẩu BCrypt, trả `201` không chứa hash; giao diện đăng ký gửi form và chuyển `/login` sau thành công.

**Independent Test**: Gửi payload hợp lệ mới → nhận `201` với `id`, `email` lowercase, `role=ROLE_CUSTOMER`, `status=ACTIVE`, `createdAt`; dòng `auth_users` có `password_hash` bắt đầu `$2a$12$`; đăng nhập bằng mật khẩu gốc sẽ thành công ở US-02. Trên UI: điền form hợp lệ → thấy thông báo thành công và được chuyển `/login` sau ~1,5 giây.

### Tests for US-01.1 ⚠️

> Viết test trước, chạy và xác nhận **FAIL** trước khi hiện thực.

- [ ] T015 [P] [US-01.1] Write unit test `AuthServiceTest` in `backend/src/test/java/com/example/shop/auth/service/AuthServiceTest.java`: email được trim + lowercase; `passwordEncoder.encode` được gọi và hash khác mật khẩu gốc; `User` có `ROLE_CUSTOMER`, `active=true`, `createdAt` được gán; `RegisterResponse` không chứa hash; `saveAndFlush` được gọi
- [ ] T016 [P] [US-01.1] Write integration test `AuthRegisterIntegrationTest` in `backend/src/test/java/com/example/shop/auth/web/AuthRegisterIntegrationTest.java` using `PostgresTestSupport`: `POST /api/v1/auth/register` payload hợp lệ → `201`; body có `id`, `email`, `fullName`, `phone`, `role=ROLE_CUSTOMER`, `status=ACTIVE`, `createdAt`; body không chứa `password`, `passwordHash`, `hash`; dòng CSDL có hash BCrypt cost 12; gửi kèm `phone` và không gửi `phone` đều thành công
- [ ] T017 [P] [US-01.1] Write frontend happy-path test in `frontend/src/features/auth/__tests__/RegisterForm.test.tsx`: mock `registerUser`, điền form hợp lệ, submit → gọi API một lần, khóa input khi loading, hiển thị thông báo thành công, gọi điều hướng `/login` sau timer 1,5 giây

### Implementation for US-01.1

- [ ] T018 [P] [US-01.1] Create `RegisterRequest` record in `backend/src/main/java/com/example/shop/auth/web/dto/RegisterRequest.java` with compact constructor normalization (email trim + `toLowerCase(Locale.ROOT)`; fullName/phone trim; blank phone → `null`; password untouched) and constraints: email `@NotBlank @Email @Size(max=150)`; password `@NotBlank @Size(min=8,max=72) @Pattern("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$") @MaxUtf8Bytes(72)`; fullName `@NotBlank @Size(min=2,max=100)`; phone nullable `@Pattern("^$|^(0|\\+84)[35789][0-9]{8}$") @Size(max=20)`; **no** `role`, `isActive`, `status`, `id` fields
- [ ] T019 [P] [US-01.1] Create `RegisterResponse` record in `backend/src/main/java/com/example/shop/auth/web/dto/RegisterResponse.java` with `id`, `email`, `fullName`, `phone`, `role`, `status`, `createdAt`; no password/hash field
- [ ] T020 [US-01.1] Implement `AuthService.register(RegisterRequest)` in `backend/src/main/java/com/example/shop/auth/service/AuthService.java`: `@Transactional`, encode password with injected `PasswordEncoder`, build via `User.createCustomer`, set `createdAt`, `saveAndFlush`, map to `RegisterResponse` (`status = ACTIVE` when `active`), log `event=auth.registration.succeeded userId={id}` without email/password; no duplicate pre-check yet (US-01.2)
- [ ] T021 [US-01.1] Create `AuthController` in `backend/src/main/java/com/example/shop/auth/web/AuthController.java`: `@RestController @RequestMapping("/api/v1/auth")`, `@PostMapping("/register")`, `@Valid @RequestBody RegisterRequest`, trả `ResponseEntity.status(201).body(RegisterResponse)`; `@Tag(name="Auth")`, `@Operation`, `@ApiResponse` cho 201/400/409
- [ ] T022 [P] [US-01.1] Create frontend contract types in `frontend/src/features/auth/types.ts`: `RegisterRequest` (`email`, `password`, `fullName`, `phone?`), `RegisterResponse`, `RegisterFormValues`, `FieldErrors`, `RegisterStatus`
- [ ] T023 [US-01.1] Implement API call in `frontend/src/features/auth/api/registerUser.ts` calling `axiosClient.post<RegisterResponse>('/auth/register', payload)`; normalize network errors into a typed result the hook can consume
- [ ] T024 [P] [US-01.1] Create password strength helper in `frontend/src/features/auth/passwordStrength.ts`: levels `weak` (<8 ký tự hoặc thiếu hoa/thường/số), `medium` (đủ 8 + số nhưng thiếu hoa/thường), `strong` (≥8 + hoa + thường + số); also reject byte length > 72 via `new TextEncoder().encode(value).length`
- [ ] T025 [US-01.1] Implement `useRegister` hook in `frontend/src/features/auth/hooks/useRegister.ts`: states `idle/loading/success/error`, expose `submit`, `fieldErrors`, `generalError`, and success flag for redirect; ignore duplicate submit while loading
- [ ] T026 [P] [US-01.1] Create `PasswordStrengthMeter` in `frontend/src/features/auth/components/PasswordStrengthMeter.tsx`: visual weak/medium/strong with text label (not color only), `aria-live="polite"`
- [ ] T027 [US-01.1] Create `RegisterForm` in `frontend/src/features/auth/components/RegisterForm.tsx`: controlled inputs with labels (Họ và tên, Email, Số điện thoại (tùy chọn), Mật khẩu, Xác nhận mật khẩu), show/hide password, terms checkbox, submit button with loading state disabling all inputs, success message in `aria-live` region, redirect `/login` after 1.5 seconds, link "Đăng nhập ngay"; use `PasswordStrengthMeter` and `useRegister`
- [ ] T028 [US-01.1] Create `RegisterPage` in `frontend/src/features/auth/pages/RegisterPage.tsx`, placeholder `LoginPage` in `frontend/src/features/auth/pages/LoginPage.tsx` (thông báo đăng nhập thuộc US-02), and router in `frontend/src/app/router.tsx`: routes `/register` and `/login`; mount router in `frontend/src/app/App.tsx` and `frontend/src/main.tsx`

**Checkpoint**: US-01.1 chạy độc lập — `201` + tài khoản CUSTOMER/ACTIVE + UI chuyển `/login`; toàn bộ test US-01.1 xanh.

---

## Phase 4: US-01.2 — Ngăn đăng ký trùng email (Priority: P2)

**Goal**: Email đã tồn tại (kể cả khác hoa/thường hoặc có khoảng trắng) bị từ chối với `409 ProblemDetail`; tranh chấp đồng thời tạo đúng một tài khoản; UI đánh dấu lỗi ô email.

**Independent Test**: Tạo tài khoản với `a@example.com`; đăng ký lại bằng `  A@Example.COM ` → `409`, `type` kết thúc `/errors/email-already-exists`, số dòng `auth_users` không đổi. Chạy 2 request song song cùng email → một `201`, một `409`, một dòng CSDL, không `5xx`.

### Tests for US-01.2 ⚠️

- [ ] T029 [P] [US-01.2] Add unit tests to `backend/src/test/java/com/example/shop/auth/service/AuthServiceTest.java`: `existsByEmail` trả `true` → ném `EmailAlreadyExistsException`, không gọi `saveAndFlush`; email được kiểm tra ở dạng đã chuẩn hóa
- [ ] T030 [P] [US-01.2] Add integration tests to `backend/src/test/java/com/example/shop/auth/web/AuthRegisterIntegrationTest.java`: email trùng chính xác và email trùng khác hoa/thường/khoảng trắng → `409`, `application/problem+json`, `type=https://api.shoptila.com/errors/email-already-exists`, `instance=/api/v1/auth/register`; không tạo thêm dòng
- [ ] T031 [P] [US-01.2] Create concurrency test `RegistrationConcurrencyTest` in `backend/src/test/java/com/example/shop/auth/web/RegistrationConcurrencyTest.java`: 2 luồng với `CountDownLatch` gửi cùng email; assert đúng một `201` và một `409`, `userRepository.count()` tăng đúng 1, không có `5xx`
- [ ] T032 [P] [US-01.2] Add frontend test in `frontend/src/features/auth/__tests__/RegisterForm.test.tsx`: mock API trả `409` ProblemDetail → lỗi hiển thị tại ô email, `aria-invalid=true`, các giá trị khác được giữ, không điều hướng

### Implementation for US-01.2

- [ ] T033 [US-01.2] Add duplicate handling to `backend/src/main/java/com/example/shop/auth/service/AuthService.java`: sau chuẩn hóa của DTO, gọi `userRepository.existsByEmail(email)`; nếu `true` ném `EmailAlreadyExistsException(email)` **trước** khi băm; bọc `saveAndFlush` bắt `DataIntegrityViolationException` và ném `EmailAlreadyExistsException` để tranh chấp đồng thời trả `409` thay vì `500`; log `event=auth.registration.failed reason=EMAIL_ALREADY_EXISTS` không chứa email
- [ ] T034 [US-01.2] Handle 409 in frontend `frontend/src/features/auth/hooks/useRegister.ts` and `frontend/src/features/auth/components/RegisterForm.tsx`: khi `status=409`, gán `detail` vào `fieldErrors.email`, giữ dữ liệu form, hiển thị gợi ý "đăng nhập hoặc dùng email khác"; không điều hướng

**Checkpoint**: US-01.1 + US-01.2 cùng hoạt động; đăng ký trùng luôn `409`, dữ liệu không bị nhân đôi.

---

## Phase 5: US-01.3 — Từ chối dữ liệu không hợp lệ theo từng trường (Priority: P3)

**Goal**: Mọi vi phạm định dạng trả `400 ProblemDetail` với `errors[]` đầy đủ; frontend kiểm tra trước khi gửi và ánh xạ lỗi server vào đúng trường.

**Independent Test**: Gửi payload sai đồng thời email/password/fullName/phone → `400` với đủ 4 lỗi trường, không tạo tài khoản. Mật khẩu > 72 byte UTF-8 → `400` ở `password`, không `500`. Trên UI: lỗi client hiển thị ngay, lỗi server 400 hiển thị đúng trường sau khi submit.

### Tests for US-01.3 ⚠️

- [ ] T035 [P] [US-01.3] Create validation integration test `AuthValidationIntegrationTest` in `backend/src/test/java/com/example/shop/auth/web/AuthValidationIntegrationTest.java`: một payload vi phạm nhiều trường → `400`, `errors[]` chứa **tất cả** `email`, `password`, `fullName`, `phone`; message tiếng Việt; body không có stack trace/class name; không tạo dòng `auth_users`
- [ ] T036 [P] [US-01.3] Create byte-limit test `PasswordByteLimitTest` in `backend/src/test/java/com/example/shop/auth/web/PasswordByteLimitTest.java`: mật khẩu 73 byte ASCII → `400` field `password`; mật khẩu 72 byte ASCII hợp lệ → `201`; mật khẩu 40 ký tự emoji (vượt 72 byte) → `400`, không `500` và không có `IllegalArgumentException` trong body
- [ ] T037 [P] [US-01.3] Create frontend validation tests in `frontend/src/features/auth/__tests__/validation.test.ts`: email sai, mật khẩu <8/thiếu hoa/thiếu thường/thiếu số/>72 byte UTF-8, fullName <2/>100, phone sai định dạng, confirm mismatch, terms chưa chọn
- [ ] T038 [P] [US-01.3] Add form tests in `frontend/src/features/auth/__tests__/RegisterForm.test.tsx`: submit với dữ liệu sai → không gọi API, lỗi tại từng trường; server trả `400` + `errors[]` → ánh xạ đúng trường, focus trường lỗi đầu tiên

### Implementation for US-01.3

- [ ] T039 [US-01.3] Create pure validators in `frontend/src/features/auth/validation.ts`: `validateRegisterForm(values)` trả `FieldErrors` cho email, password (8–72 ký tự, hoa/thường/số, ≤72 byte qua `TextEncoder`), fullName (2–100 sau trim), phone (rỗng hoặc `^(0|\+84)[35789][0-9]{8}$`), confirmPassword, terms; import `passwordStrength` from T024
- [ ] T040 [US-01.3] Wire client validation into `frontend/src/features/auth/components/RegisterForm.tsx`: validate on blur and on submit, block submit when invalid, set `aria-invalid` and `aria-describedby` for each field, show error messages under fields, keep values, focus first invalid field
- [ ] T041 [US-01.3] Wire server `400` handling into `frontend/src/features/auth/hooks/useRegister.ts` and `RegisterForm.tsx` using `extractFieldErrors` from `frontend/src/shared/api/problemDetails.ts`: map `errors[]` to fields, show `detail` as general message when no field matches, preserve form state; ensure all messages are Vietnamese and accessible (`role="alert"` or `aria-live`)
- [ ] T042 [US-01.3] Add accessibility and UX polish in `frontend/src/features/auth/components/RegisterForm.tsx` and `PasswordStrengthMeter.tsx`: labels linked with `htmlFor`/`id`, `autoComplete` (`name`, `email`, `tel`, `new-password`), error summary in `aria-live`, password toggle with `aria-label`, keyboard-only submit works, responsive layout at mobile width

**Checkpoint**: Cả 3 phần (US-01.1, US-01.2, US-01.3) hoạt động; `400`/`409`/`500` đều là `ProblemDetail`; không có mật khẩu/hash trong response hay log.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Tài liệu, bảo mật, cổng kiểm thử và xác minh đầu-cuối.

- [ ] T043 [P] Sync `README.md` (API Auth, hành vi mật khẩu/role, trạng thái US-01) and verify Swagger output: `@Operation`/`@ApiResponse` hiển thị đúng tại `/swagger-ui.html`, contract khớp `specs/001-user-registration/contracts/auth-register.openapi.yaml`
- [ ] T044 [P] Write ADR `docs/adr/0001-auth-role-model.md`: quyết định một cột `role` thay vì bảng `auth_roles`/`auth_user_roles`, lý do MVP, và đường nâng cấp nhiều vai trò bằng migration mới
- [ ] T045 [P] Verify `compose.yaml`, `.env.example`, `.gitignore`: không có secret thật, đủ biến DB_*/JWT_*/VNPAY_*, `docker compose config` hợp lệ, `.env` không được track
- [ ] T046 Run scenarios in `specs/001-user-registration/quickstart.md`: `201` hợp lệ, `409` trùng (kể cả khác hoa/thường), `400` nhiều lỗi, `400` mật khẩu >72 byte, gửi kèm `role=ROLE_ADMIN` vẫn là `ROLE_CUSTOMER`, kiểm tra hash `$2a$12$` trong CSDL; ghi lại kết quả
- [ ] T047 Run quality gates with test sources in `backend/src/test/java/` and `frontend/src/features/auth/__tests__/`: `cd backend && ./mvnw test`, `cd frontend && npm test -- --run`, `cd frontend && npm run build`; tất cả phải xanh, không có test bị skip ngoài lý do đã ghi
- [ ] T048 Security review against `security-and-hardening` for `backend/src/main/java/com/example/shop/common/config/SecurityConfig.java`, `backend/src/main/java/com/example/shop/common/exception/ProblemDetailsExceptionHandler.java`, `backend/src/main/java/com/example/shop/auth/service/AuthService.java`, `frontend/src/shared/api/axiosClient.ts`: response/log không chứa mật khẩu/hash/PII; security headers hiện diện; không CORS wildcard; `anyRequest().denyAll()` còn hiệu lực; role injection bị bỏ qua; chạy `npm audit --omit=dev` và `./mvnw dependency:tree` để rà soát dependency
- [ ] T049 Code cleanup and boundary audit in `backend/src/main/java/com/example/shop/` and `frontend/src/`: loại import/biến không dùng, không controller nào trả `@Entity`, `common` không import package `auth`, không quan hệ JPA chéo module, không secret hardcode; chạy lại toàn bộ test

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Không phụ thuộc; bắt đầu ngay.
- **Foundational (Phase 2)**: Phụ thuộc Setup; **chặn toàn bộ các phần của US-01**.
- **User Stories (Phase 3–5)**: Phụ thuộc Foundational. Có thể chạy song song nếu đủ người, hoặc tuần tự P1 → P2 → P3.
- **Polish (Phase 6)**: Phụ thuộc tất cả phần mong muốn của US-01 hoàn thành.

### Phụ thuộc giữa các phần của US-01

- **US-01.1 (P1)**: Sau Foundational; không phụ thuộc phần khác; là MVP.
- **US-01.2 (P2)**: Sau Foundational; bổ sung kiểm tra trùng email vào `AuthService` và UI; phụ thuộc các file US-01.1 đã tạo (`AuthService`, `RegisterForm`, `useRegister`), nhưng hành vi trùng email vẫn kiểm thử độc lập.
- **US-01.3 (P3)**: Sau Foundational; dùng `RegisterForm`/`useRegister` của US-01.1 và `problemDetails` của Phase 2; kiểm thử độc lập các nhánh lỗi.

### Trong mỗi phần của US-01

- Test phải viết trước và **FAIL** trước khi hiện thực.
- Backend: DTO → service → controller.
- Frontend: types/API → hook → component → page/router.
- Hoàn tất và xác minh từng phần trước khi chuyển priority tiếp theo.

### Parallel Opportunities

- Setup: T003, T004, T005 song song sau T001/T002.
- Foundational: T007, T010, T011, T012, T013, T014 song song sau T001; T008 phụ thuộc T007; T009 phụ thuộc T008.
- US-01.1: T015, T016, T017 (test) song song; T018, T019, T022, T024 song song; sau đó T020 → T021; T023 → T025 → T027 → T028.
- US-01.2: T029, T030, T031, T032 song song; T033 → T034.
- US-01.3: T035, T036, T037, T038 song song; T039 → T040 → T041 → T042.
- Polish: T043, T044, T045 song song; T046 → T047 → T048 → T049.

---

## Parallel Example: US-01.1

```bash
# Nhóm test US-01.1 (viết trước, chạy để thấy đỏ):
Task: "AuthServiceTest happy path in backend/src/test/java/com/example/shop/auth/service/AuthServiceTest.java"
Task: "AuthRegisterIntegrationTest happy path in backend/src/test/java/com/example/shop/auth/web/AuthRegisterIntegrationTest.java"
Task: "RegisterForm happy path test in frontend/src/features/auth/__tests__/RegisterForm.test.tsx"

# Nhóm model/DTO/helper US-01.1:
Task: "RegisterRequest record in backend/src/main/java/com/example/shop/auth/web/dto/RegisterRequest.java"
Task: "RegisterResponse record in backend/src/main/java/com/example/shop/auth/web/dto/RegisterResponse.java"
Task: "frontend auth types in frontend/src/features/auth/types.ts"
Task: "password strength helper in frontend/src/features/auth/passwordStrength.ts"
```

## Parallel Example: US-01.2

```bash
Task: "duplicate unit tests in .../AuthServiceTest.java"
Task: "duplicate integration tests in .../AuthRegisterIntegrationTest.java"
Task: "concurrency test in .../RegistrationConcurrencyTest.java"
Task: "409 form test in frontend/src/features/auth/__tests__/RegisterForm.test.tsx"
```

## Parallel Example: US-01.3

```bash
Task: "multi-field 400 test in .../AuthValidationIntegrationTest.java"
Task: "72-byte test in .../PasswordByteLimitTest.java"
Task: "validation unit tests in frontend/src/features/auth/__tests__/validation.test.ts"
Task: "field-error form tests in frontend/src/features/auth/__tests__/RegisterForm.test.tsx"
```

---

## Implementation Strategy

### MVP First (US-01.1 Only)

1. Hoàn thành Phase 1 (Setup).
2. Hoàn thành Phase 2 (Foundational) — bắt buộc.
3. Hoàn thành Phase 3 (US-01.1).
4. **DỪNG và xác minh**: chạy test US-01.1 + `quickstart.md` mục 5.1 và 5.4.
5. Demo được luồng đăng ký thành công; chưa cần nhánh lỗi chi tiết.

### Incremental Delivery

1. Setup + Foundational → nền sẵn sàng.
2. US-01.1 → xác minh độc lập → demo (MVP).
3. US-01.2 → xác minh trùng email/đồng thời → demo.
4. US-01.3 → xác minh lỗi từng trường + 72 byte → demo.
5. Polish → chạy toàn bộ quickstart + quality gates.

### Parallel Team Strategy

1. Cả nhóm hoàn thành Setup + Foundational.
2. Sau Foundational:
   - Developer A: US-01.1 (backend endpoint + UI happy path).
   - Developer B: US-01.2 (duplicate/concurrency + UI 409) sau khi US-01.1 tạo `AuthService`/`RegisterForm`.
   - Developer C: US-01.3 (validation + UI field errors) sau khi US-01.1 tạo `RegisterForm`.
3. Tích hợp và chạy lại toàn bộ test sau mỗi phần.

---

## Notes

- `[P]` = file khác nhau, không phụ thuộc task chưa xong.
- Nhãn `[Story]` dùng để truy vết task → phần ưu tiên của US-01.
- Mỗi phần (US-01.1…US-01.3) phải hoàn tất và kiểm thử được độc lập trước khi chuyển phần tiếp theo.
- Test phải đỏ trước khi hiện thực (TDD) theo `plan.md` R-09.
- Không commit `.env`, secret, mật khẩu, hash hay token.
- Không đưa công nghệ Giai đoạn 2–4 (WebSocket, Kafka, Redis, outbox) vào feature này.
- Commit theo module sau mỗi nhóm task hợp lý (`feat(auth): ...`, `test(auth): ...`) — Stage 6 của lifecycle.
