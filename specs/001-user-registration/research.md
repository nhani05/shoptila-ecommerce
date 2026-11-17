# Research: US-01 — Đăng ký tài khoản khách hàng

**Feature**: `specs/001-user-registration` | **Date**: 2026-11-16

Mọi điểm chưa xác định trong Technical Context đã được giải quyết dưới đây. Không còn
`NEEDS CLARIFICATION`.

---

## R-01. Mô hình vai trò: một cột `role` thay vì bảng nối

**Decision**: Lưu vai trò trực tiếp trong `auth_users.role` (kiểu chuỗi, ràng buộc CHECK
`ROLE_CUSTOMER`/`ROLE_ADMIN`), ánh xạ sang enum Java `Role`.

**Rationale**:
- Giai đoạn 1 chỉ có hai vai trò cố định và mỗi tài khoản chỉ cần một vai trò. Bảng
  `auth_roles` + `auth_user_roles` của Sprint Backlog 1.1 là mô hình nhiều-nhiều chưa cần thiết,
  làm tăng số JOIN và mã quản lý mà không mang lại giá trị MVP.
- Hướng kỹ thuật của người dùng cho US-01 chỉ định rõ `role='ROLE_CUSTOMER'` trên `auth_users`.
- Khả năng nâng cấp: khi cần nhiều vai trò, thêm migration mới (`V2__auth_roles.sql`) tạo bảng nối,
  sao chép dữ liệu từ cột `role`, rồi bỏ cột — không phá vỡ API vì response vẫn trả danh sách/giá
  trị vai trò được ánh xạ ở lớp DTO.

**Alternatives considered**:
- *Bảng `auth_roles` + `auth_user_roles` ngay từ đầu*: đúng chuẩn RBAC mở rộng, nhưng thêm 2 bảng,
  2 repository và logic gán vai trò cho một nhu cầu chưa tồn tại. Bị loại theo nguyên tắc tối giản
  MVP.
- *Cột kiểu PostgreSQL enum*: khó tiến hóa (thêm giá trị cần `ALTER TYPE`), không cần thiết; dùng
  `VARCHAR(30)` + CHECK linh hoạt hơn.

**Consequence**: Response vẫn biểu diễn `role` dưới dạng chuỗi `ROLE_CUSTOMER`; nếu sau này hỗ trợ
nhiều vai trò, có thể bổ sung trường `roles: string[]` theo kiểu cộng thêm (additive), giữ nguyên
`role` để tương thích.

---

## R-02. Chuẩn hóa và duy nhất của email

**Decision**:
- Chuẩn hóa email ở biên đầu vào bằng compact constructor của `RegisterRequest`
  (`trim()` + `toLowerCase(Locale.ROOT)`), trước khi Bean Validation chạy.
- CSDL: `email VARCHAR(150) NOT NULL`, `UNIQUE (email)`, và `CHECK (email = lower(email))`.
- Service: `existsByEmail()` để báo lỗi sớm; bắt `DataIntegrityViolationException` khi ghi để xử lý
  tranh chấp đồng thời và trả 409.

**Rationale**:
- Email là định danh đăng nhập; `A@x.com` và `a@x.com` phải là cùng một tài khoản.
- Ràng buộc UNIQUE tạo unique index trên `email`, phục vụ luôn truy vấn `existsByEmail` — không cần
  thêm index trùng lặp như Sprint Backlog đề xuất.
- `CHECK (email = lower(email))` là lớp phòng thủ thứ hai: nếu một đường ghi trong tương lai quên
  chuẩn hóa, CSDL từ chối thay vì tạo bản ghi trùng ngữ nghĩa.
- Kiểm tra trước rồi ghi là TOCTOU; ràng buộc UNIQUE ở CSDL mới là cơ chế phân xử nguyên tử.

**Alternatives considered**:
- *Unique index hàm `lower(email)`*: mạnh nhưng `existsByEmail` trên cột thô sẽ không dùng index
  hàm; cần thêm index thứ hai. Cách đã chọn (chuẩn hóa khi ghi + CHECK) đơn giản hơn và vẫn kín.
- *CITEXT*: phụ thuộc extension PostgreSQL, thêm yêu cầu vận hành; không cần.

---

## R-03. Băm mật khẩu: BCrypt strength 12 + giới hạn 72 byte UTF-8

**Decision**: Dùng `BCryptPasswordEncoder(12)`, kiểm tra độ dài mật khẩu ≤ 72 **byte UTF-8** bằng
Bean Validation tùy biến (`@MaxUtf8Bytes(72)`) trước khi băm; ánh xạ mọi
`IllegalArgumentException` từ encoder thành lỗi 400 thay vì 500.

**Rationale**:
- `constitution.md` và Sprint Backlog yêu cầu BCrypt; strength 12 là mức cân bằng do dự án chọn
  (thời gian băm mục tiêu ~0,2–0,5 giây trên phần cứng phát triển).
- BCrypt chỉ sử dụng tối đa 72 byte đầu vào. Từ Spring Security 6.3.8/6.4.4/6.5.x (bản vá
  **CVE-2025-22228**), `BCrypt.hashpw` **ném `IllegalArgumentException`** khi mật khẩu > 72 byte
  thay vì âm thầm cắt bớt. Nếu không chặn trước, người dùng nhận lỗi 500 khó hiểu và log đầy stack
  trace.
- Giới hạn phải tính theo **byte**, không theo ký tự: tiếng Việt có dấu dùng 2–3 byte/ký tự, nên
  72 ký tự có thể vượt 72 byte. Đây chính là lỗi mà Spring Security đã sửa trong issue #18133.
- Từ chối thay vì cắt bớt tuân thủ FR-008: cắt bớt khiến người dùng không thể đăng nhập lại bằng
  mật khẩu đã nhập.

**Alternatives considered**:
- *Tiền băm HMAC-SHA256 rồi Base64 trước BCrypt*: cho phép mật khẩu dài tùy ý, nhưng thêm khóa bí
  mật và độ phức tạp; chưa cần cho MVP.
- *Argon2id*: mạnh hơn nhưng lệch ràng buộc BCrypt đã được phê chuẩn của dự án.
- *Chỉ `@Size(max=72)` theo ký tự*: không bảo vệ được trường hợp đa byte — bị loại.

**Reference**: Spring Security Password Storage docs; CVE-2025-22228; Spring Security issues
#16802, #18133.

---

## R-04. Lỗi theo RFC 7807 `ProblemDetail`

**Decision**: `GlobalExceptionHandler` trong `com.example.shop.common.web` kế thừa
`ResponseEntityExceptionHandler`, trả `ProblemDetail` cho:
- `MethodArgumentNotValidException` → 400, mở rộng `errors: [{field, message}]` (giữ **tất cả** lỗi
  trường, không dừng ở lỗi đầu).
- `EmailAlreadyExistsException` → 409, `type` = `https://api.shoptila.com/errors/email-already-exists`.
- `DataIntegrityViolationException` (vi phạm UNIQUE khi đua) → 409 cùng `type`.
- `HttpMessageNotReadableException` (JSON hỏng) → 400, thông điệp chung.
- Mọi `Exception` khác → 500 `type` = `.../errors/internal-error`, **không** lộ stack trace hay
  chi tiết nội bộ; chi tiết chỉ ghi server log.

**Rationale**:
- Spring Framework 6 (Boot 3) hỗ trợ sẵn `ProblemDetail`; `ResponseEntityExceptionHandler` đã tự
  gắn `instance` từ request URI và xử lý các ngoại lệ chuẩn.
- Một định dạng lỗi duy nhất trên mọi endpoint (yêu cầu của hiến chương và skill
  api-and-interface-design). Danh sách `errors` có tên trường khớp JSON để frontend ánh xạ trực
  tiếp vào form.
- Mã `type` ổn định, máy đọc được, đóng vai trò mã lỗi phiên bản hóa.

**Alternatives considered**:
- *Bật `spring.mvc.problemdetails.enabled=true`*: chỉ xử lý các ngoại lệ framework, không có danh
  sách lỗi trường và không có lỗi nghiệp vụ 409 tùy biến — không đủ.
- *Trả `{ error: { code, message } }` tự chế*: trái ràng buộc RFC 7807 của dự án.

---

## R-05. Cấu hình bảo mật và phạm vi

**Decision**:
- `SecurityConfig` (module `common`): stateless, CSRF tắt (API JSON không dùng cookie phiên), tắt
  form login/basic auth, phân quyền fail-closed:
  - `permitAll`: `POST /api/v1/auth/register`, `/v3/api-docs/**`, `/swagger-ui/**`,
    `/swagger-ui.html`, `/actuator/health`, `/error`, và dispatch type `ERROR`.
  - `anyRequest().denyAll()` — các module sau phải khai báo công khai tường minh khi được thêm.
- `PasswordEncoder` bean = `BCryptPasswordEncoder(12)`.
- Không cấu hình CORS: phát triển dùng proxy `/api` của Vite, production cùng origin qua Nginx.
- **Không** triển khai giới hạn tần suất trong US-01 (spec Assumptions đã loại trừ khỏi Giai đoạn 1);
  rủi ro brute-force được ghi nhận cho giai đoạn hardening sau.
- Không dùng JWT ở US-01: phát hành token thuộc US-02; đăng ký không tạo phiên.

**Rationale**:
- `spring-boot-starter-security` bật filter chain mặc định sẽ chặn mọi request; cần cấu hình tường
  minh ngay khi thêm dependency.
- Fail-closed (`denyAll`) an toàn hơn `permitAll` mặc định và buộc mỗi module mới phải quyết định
  quyền truy cập.
- `/error` và dispatch `ERROR` phải được permit, nếu không phản hồi ProblemDetail sẽ bị biến thành
  403 khi container chuyển sang error dispatch.
- Không có CORS wildcard; token không được lưu ở `localStorage` (quyết định lưu token sẽ thuộc
  US-02, ưu tiên httpOnly cookie hoặc bộ nhớ trong).

**Alternatives considered**:
- *`anyRequest().permitAll()` tạm thời*: tiện nhưng tạo thói quen fail-open; bị loại.
- *Bật CORS cho `localhost:5173`*: không cần vì Vite proxy; thêm cấu hình thừa và dễ sai khi lên
  production.

---

## R-06. Chuẩn hóa đầu vào trong DTO

**Decision**: `RegisterRequest` là Java `record` với compact constructor:
- `email`: trim + lowercase (Locale.ROOT), `null` giữ nguyên để `@NotBlank` báo lỗi.
- `fullName`: trim; `null` giữ nguyên.
- `phone`: trim; chuỗi rỗng → `null` (trường tùy chọn).
- `password`: **không** trim, không biến đổi.
- DTO **không có** trường `role`, `isActive`, `status`, `id`.

**Rationale**: Jackson gọi canonical constructor trước khi Bean Validation; chuẩn hóa ở đây bảo đảm
mọi kiểm tra (`@Email`, `@Size`, pattern) chạy trên giá trị đã chuẩn hóa, đúng FR-002 và FR-012.
Việc không có trường `role`/`isActive` khiến hành vi "gửi role=ROLE_ADMIN bị bỏ qua" trở thành mặc
định của kiểu dữ liệu chứ không phải một `if` dễ quên. Spring Boot mặc định bỏ qua thuộc tính JSON
không xác định.

**Alternatives considered**:
- *Chuẩn hóa trong service*: validation đã chạy trước đó, nên email có khoảng trắng sẽ bị `@Email`
  từ chối oan — sai spec.
- *Custom Jackson deserializer*: nhiều mã hơn cho cùng kết quả.

---

## R-07. Hợp đồng HTTP và quy ước đặt tên

**Decision**:
- `POST /api/v1/auth/register`, không yêu cầu xác thực, `Content-Type: application/json`.
- Thành công: `201 Created`, body `RegisterResponse` gồm `id`, `email`, `fullName`, `phone`,
  `role`, `status`, `createdAt`; trường camelCase; enum UPPER_SNAKE; thời gian ISO-8601 UTC
  (`Instant` ↔ `timestamptz`).
- Không trả `Location` vì chưa có endpoint `GET` cho tài khoản.
- Lỗi: 400/409/500 theo R-04.
- Swagger: `@Tag`, `@Operation`, `@ApiResponse` mô tả 201/400/409; springdoc tự sinh từ OpenAPI
  contract.

**Rationale**: Thống nhất quy ước toàn dự án (skill api-and-interface-design), tránh trả liên kết
chết, và cho phép frontend ánh xạ lỗi theo trường.

---

## R-08. Lựa chọn phiên bản và khởi tạo dự án

**Decision**:

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Java | 17 (JDK cài sẵn 17.0.21) | Ràng buộc hiến chương |
| Spring Boot | **3.5.16** | Bản 3.x mới nhất tại 2026-11; hiến chương yêu cầu Spring Boot 3 |
| springdoc-openapi | **2.8.16** | Dòng 2.x tương thích Boot 3 |
| Flyway | Boot-managed | Kèm `flyway-database-postgresql` |
| PostgreSQL driver | Boot-managed | |
| Testcontainers | Boot-managed (`spring-boot-testcontainers`) | PostgreSQL module |
| React / React DOM | **19.3.1** | |
| React Router DOM | **7.18.5** | |
| Axios | **1.21.1** | |
| Vite | **8.3.8** | Node 24 trong môi trường dev |
| @vitejs/plugin-react | **6.3.0** | |
| TypeScript | **5.9.3** | Chọn dòng 5.x ổn định thay vì 6/7 mới |
| Vitest | **5.1.0** + `@testing-library/react` 16.3.3 + `jsdom` | |

**Rationale**:
- Không có `pom.xml` hay `package.json` trong repo — cả hai phía phải được khởi tạo trong feature
  này. Phiên bản trên là bản ổn định mới nhất trong các dòng mà hiến chương cho phép, đã đối chiếu
  Maven Central/npm tại 2026-11-16.
- JWT (`jjwt`) **không** được thêm ở US-01 vì chưa phát hành/ xác minh token; tránh dependency
  không dùng.
- Lombok không được dùng: DTO là record, entity viết tay — giảm phụ thuộc build.

**Alternatives considered**:
- *Spring Boot 4.x*: mới nhất nhưng trái ràng buộc "Spring Boot 3" của hiến chương; muốn nâng cần
  sửa hiến chương bằng quy trình riêng.
- *TypeScript 7*: là bản viết lại native, hệ sinh thái công cụ chưa ổn định bằng 5.x tại thời điểm
  lập kế hoạch.
- *Thêm react-hook-form + zod*: mạnh nhưng vượt nhu cầu; validation thuần TypeScript dễ kiểm thử
  bằng Vitest và không thêm dependency.

---

## R-09. Chiến lược kiểm thử

**Decision**:
- **Unit** (`AuthServiceTest`, Mockito): chuẩn hóa email, băm BCrypt, gán `ROLE_CUSTOMER`, active,
  ném lỗi trùng email, không trả mật khẩu.
- **Integration** (`AuthRegisterIntegrationTest`, Testcontainers PostgreSQL + MockMvc):
  - 201 + body không chứa mật khẩu/băm,
  - 400 + `errors[]` cho từng trường,
  - 409 cho email trùng (kể cả khác hoa/thường),
  - bỏ qua `role` do client gửi,
  - Flyway chạy sạch trên CSDL rỗng.
- **Concurrency** (`RegistrationConcurrencyTest`): 2 luồng cùng email → đúng 1 tài khoản, một 201 và
  một 409, không 5xx.
- **Frontend** (Vitest + RTL): validation thuần, ánh xạ ProblemDetail theo trường, trạng thái
  loading, redirect sau thành công.
- Cổng bắt buộc của hiến chương (`InventoryConcurrencyTest`, `VnpaySignatureTest`,
  `IpnIdempotencyTest`, `OrderFlowTest`) không thuộc phạm vi module auth; vẫn phải chạy
  `./mvnw test` toàn bộ trước khi kết thúc Stage 4.

**Rationale**: Auth không thuộc Critical Tier về tiền/tồn kho, nhưng là bề mặt bảo mật; kiểm thử
tích hợp với PostgreSQL thật bắt được khác biệt giữa H2 và PostgreSQL (ràng buộc, kiểu, lower()).

**Alternatives considered**:
- *H2 cho integration test*: nhanh nhưng không tái hiện ràng buộc/CHECK PostgreSQL; bị loại.
- *Chỉ unit test*: không chứng minh được migration, security chain và hợp đồng HTTP; bị loại.

---

## R-10. Quan sát và log

**Decision**: Log sự kiện có cấu trúc bằng SLF4J:
- Thành công: `event=auth.registration.succeeded userId={id}`.
- Thất bại: `event=auth.registration.failed reason=EMAIL_ALREADY_EXISTS` (không log email, mật
  khẩu, hash, hay payload thô).
- Không log request body.

**Rationale**: FR-016 và mục Observability của hiến chương; email là PII nên không đưa vào log khi
không cần thiết.

---

## Tổng hợp: các điểm từng là ẩn số

| Chủ đề | Kết luận |
|---|---|
| Mô hình vai trò | Một cột `role` + enum (R-01) |
| Duy nhất email | Chuẩn hóa + UNIQUE + CHECK lower + bắt DataIntegrityViolation (R-02) |
| Giới hạn BCrypt | ≤72 byte UTF-8, từ chối trước khi băm (R-03) |
| Định dạng lỗi | ProblemDetail + `errors[]` (R-04) |
| Bảo mật filter chain | Stateless, fail-closed, permit `/error` (R-05) |
| Chuẩn hóa input | Compact constructor record (R-06) |
| Hợp đồng HTTP | 201/400/409, camelCase, Instant (R-07) |
| Phiên bản | Bảng R-08 |
| Kiểm thử | Unit + Testcontainers + concurrency + Vitest (R-09) |
| Log | Có cấu trúc, không PII (R-10) |
