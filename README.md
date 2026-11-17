# Shoptila – Website thương mại điện tử tích hợp thanh toán VNPAY

Website bán đồ công nghệ với luồng mua sắm khép kín: chọn sản phẩm → giỏ hàng → thông tin giao hàng → thanh toán qua **VNPAY Sandbox** → cập nhật trạng thái đơn và trừ tồn kho.

Dự án xây dựng theo hướng **modular monolith** (Spring Boot), chia module theo nghiệp vụ và giữ ranh giới rõ ràng để về sau mở rộng WebSocket (chat, thông báo realtime) và tách thành microservices mà không phải viết lại nghiệp vụ.

> **Trạng thái:** Giai đoạn 1 (bản tối thiểu). Các phần đánh dấu *[GĐ2]*, *[GĐ3]*, *[GĐ4]* là kế hoạch, xem mục [Lộ trình](#lộ-trình).

---

## Mục lục

1. [Tính năng](#tính-năng)
2. [Công nghệ](#công-nghệ)
3. [Kiến trúc](#kiến-trúc)
4. [Bắt đầu nhanh](#bắt-đầu-nhanh)
5. [Cấu hình](#cấu-hình)
6. [Tích hợp VNPAY Sandbox](#tích-hợp-vnpay-sandbox)
7. [API](#api)
8. [Kiểm thử](#kiểm-thử)
9. [Lộ trình](#lộ-trình)
10. [Quy ước phát triển](#quy-ước-phát-triển)
11. [Tổ chức thư mục dự án](#tổ-chức-thư-mục-dự-án)

---

## Tính năng

### Khách hàng

- Đăng ký tài khoản khách hàng (**đã triển khai — US-01**): email duy nhất được chuẩn hóa, mật khẩu băm BCrypt strength 12, mặc định `ROLE_CUSTOMER` và trạng thái `ACTIVE`; lỗi trả theo `ProblemDetail`.
- Đăng nhập (JWT), xem thông tin tài khoản.
- Xem danh sách sản phẩm, **lọc theo giá, hãng, RAM, CPU**, phân trang, xem chi tiết.
- Giỏ hàng: thêm, sửa số lượng, xóa.
- Đặt hàng, nhập thông tin giao hàng, thanh toán qua VNPAY Sandbox.
- Xem lịch sử đơn hàng và trạng thái; hủy đơn chưa thanh toán.

### Quản trị (ADMIN)

- CRUD sản phẩm, danh mục, thương hiệu.
- Quản lý tồn kho.
- Xem danh sách đơn hàng, cập nhật trạng thái đơn.

### Yêu cầu kỹ thuật đã đáp ứng

| Yêu cầu | Cách thực hiện |
|---|---|
| Xác thực an toàn | Mật khẩu băm BCrypt, JWT có thời hạn |
| Phân quyền | Role `CUSTOMER`, `ADMIN`, kiểm tra ở backend bằng Spring Security |
| Luồng thanh toán đúng | Xác nhận thanh toán dựa trên **IPN** (server-to-server), không tin tham số trên Return URL |
| IPN idempotent | Ràng buộc `UNIQUE` trên mã giao dịch và cập nhật có điều kiện, IPN gọi nhiều lần chỉ xử lý một lần |
| Không bán vượt tồn kho | Cập nhật có điều kiện (`UPDATE ... WHERE quantity >= :qty`), có test nhiều luồng |
| Xử lý dữ liệu sai | Validate ở cả frontend và backend (chặn số lượng âm, ký tự chữ), trả lỗi rõ ràng |
| Lọc sản phẩm nhanh | Cột có index cho giá, hãng, RAM, CPU |
| Responsive | Giao diện hiển thị tốt trên desktop, tablet, mobile |

---

## Công nghệ

| Lớp | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 3, Spring Security, Spring Data JPA, Bean Validation |
| CSDL | PostgreSQL, Flyway (migration) |
| Xác thực | JWT (jjwt), BCrypt |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Frontend | React, Vite, TypeScript, Axios, React Router |
| Kiểm thử | JUnit 5, Testcontainers |
| Đóng gói | Docker, Docker Compose, nginx (phục vụ frontend) |
| Thanh toán | VNPAY Sandbox |

Dự kiến bổ sung ở các giai đoạn sau: WebSocket/STOMP, Redis, Kafka hoặc RabbitMQ, Prometheus và Grafana.

---

## Kiến trúc

### Modular monolith

Backend là một ứng dụng Spring Boot, chia thành các module theo nghiệp vụ (package-by-feature):

| Module | Trách nhiệm |
|---|---|
| `common` | Thành phần kỹ thuật dùng chung: bảo mật (xác minh JWT), xử lý lỗi, DTO, cấu hình |
| `auth` | Người dùng, vai trò, đăng ký, đăng nhập, phát hành token |
| `catalog` | Sản phẩm, danh mục, thương hiệu, bộ lọc |
| `cart` | Giỏ hàng |
| `inventory` | Tồn kho, giữ hàng, nhả hàng |
| `order` | Đơn hàng, trạng thái đơn, job hủy đơn quá hạn |
| `payment` | Tích hợp VNPAY, giao dịch thanh toán, xử lý IPN |
| `chat` *[GĐ2]* | Hội thoại khách và shop qua WebSocket |
| `notification` *[GĐ2]* | Thông báo trạng thái đơn, email |

### Quy tắc giữ ranh giới giữa các module

- Module chỉ gọi nhau qua package `api/` (interface và event công khai), không import `domain`, `repository`, `service` của module khác.
- Không dùng quan hệ JPA chéo module, chỉ lưu ID.
- Tên bảng có tiền tố module (`order_orders`, `inventory_stocks`...) để chuẩn bị tách database.
- `common` không phụ thuộc module nghiệp vụ. Việc **phát token** nằm ở `auth`, việc **xác minh token** nằm ở `common`.

### Luồng đặt hàng và thanh toán

```
Khách bấm đặt hàng
  → OrderService (một transaction):
      1. Lấy giá hiện tại từ catalog, tạo đơn (PENDING_PAYMENT) và các dòng hàng (lưu snapshot tên, giá)
      2. InventoryApi.reserve(): trừ tồn kho có điều kiện; thiếu hàng thì dừng và rollback
  → PaymentApi.createPaymentUrl(): tạo giao dịch PENDING, ký URL, trả về cho client
  → Client chuyển sang cổng VNPAY và thanh toán

VNPAY gọi IPN (nguồn xác nhận chính thức)
  → PaymentService (một transaction):
      1. Kiểm tra chữ ký, đối chiếu mã giao dịch và số tiền
      2. Cập nhật PENDING → SUCCESS có điều kiện (gọi lại nhiều lần chỉ xử lý một lần)
      3. OrderApi.markPaid(): đơn chuyển PAID

Return URL: chỉ để khách xem kết quả. Frontend lấy trạng thái thật từ API đơn hàng.

OrderExpiryJob (định kỳ): hủy đơn PENDING_PAYMENT quá hạn và hoàn lại tồn kho.
```

Hạn của URL thanh toán VNPAY phải **nhỏ hơn** hạn hủy đơn, để không xảy ra việc đơn đã hủy mà khách vẫn thanh toán thành công.

### Trạng thái đơn hàng

```
PENDING_PAYMENT → PAID → SHIPPING → COMPLETED
       └──→ CANCELLED
```

---

## Bắt đầu nhanh

### Yêu cầu

- Docker và Docker Compose
- (Chạy không dùng Docker) JDK 17+, Maven, Node.js 18+, PostgreSQL

### Chạy bằng Docker Compose

```bash
# 1. Lấy mã nguồn
git clone <repo-url> shop
cd shop

# 2. Tạo file cấu hình từ mẫu rồi điền giá trị
cp .env.example .env

# 3. Khởi động toàn bộ hệ thống
docker compose up --build
```

Sau khi khởi động (cổng mặc định, có thể đổi trong `docker-compose.yml`):

| Thành phần | Địa chỉ |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

### Tài khoản demo

Dữ liệu mẫu (tài khoản admin, danh mục, vài sản phẩm) nằm trong `backend/src/main/resources/db/migration/V2__seed_data.sql`. Xem file này để biết thông tin đăng nhập demo, và **đổi mật khẩu trước khi triển khai thật**.

### Chạy môi trường phát triển (không dùng Docker cho app)

```bash
# Chỉ chạy PostgreSQL bằng Docker (đọc .env)
docker compose up -d postgres

# Backend — tự nạp .env ở thư mục gốc repo
cd backend
./mvnw spring-boot:run

# Frontend (terminal khác)
cd frontend
npm install
npm run dev
```

Backend tự đọc `.env` ở thư mục gốc (biến môi trường thật vẫn có độ ưu tiên cao hơn). Nếu cổng
`8080` hoặc `5432` đã bị chiếm, đổi `SERVER_PORT` và `DB_PORT`/`DB_URL` trong `.env`, rồi đặt
`VITE_API_PROXY_TARGET` trong `frontend/.env.local` cho khớp cổng backend.

Vite đã cấu hình proxy `/api` về backend nên không gặp lỗi CORS khi phát triển.

---

## Cấu hình

Mọi giá trị nhạy cảm đọc từ biến môi trường. **Không commit file `.env` thật** (chỉ commit `.env.example`).

| Biến | Ý nghĩa |
|---|---|
| `DB_URL` | JDBC URL của PostgreSQL |
| `DB_USER`, `DB_PASSWORD` | Tài khoản CSDL |
| `JWT_SECRET` | Khóa ký JWT (chuỗi dài, ngẫu nhiên) |
| `JWT_ACCESS_TTL_MINUTES` | Thời hạn access token |
| `VNPAY_TMN_CODE` | Mã website do VNPAY cấp (Sandbox) |
| `VNPAY_HASH_SECRET` | Khóa bí mật dùng để ký |
| `VNPAY_PAY_URL` | URL cổng thanh toán Sandbox theo tài liệu VNPAY |
| `VNPAY_RETURN_URL` | URL VNPAY chuyển khách về sau khi thanh toán (trang kết quả của frontend) |

---

## Tích hợp VNPAY Sandbox

1. Đăng ký tài khoản Sandbox của VNPAY để nhận `TMN_CODE` và `HASH_SECRET`, điền vào `.env`.
2. **IPN URL** phải truy cập được từ internet. Khi chạy local, dùng công cụ tạo đường hầm (ví dụ ngrok) rồi đăng ký URL dạng `https://<địa-chỉ-công-khai>/api/v1/payments/vnpay/ipn` trên cổng quản trị Sandbox.
3. Return URL có thể dùng localhost.
4. Dùng thẻ test do VNPAY cung cấp để thử luồng thanh toán.

Lưu ý khi làm việc với VNPAY:

- Chữ ký tạo và kiểm tra bằng HMAC-SHA512 trên các tham số đã sắp xếp. Kiểm tra chữ ký ở cả Return và IPN.
- Số tiền gửi sang VNPAY nhân 100.
- Mã tham chiếu giao dịch (`vnp_TxnRef`) phải duy nhất cho mỗi lần thanh toán.
- Phản hồi IPN phải đúng định dạng VNPAY yêu cầu, nếu không VNPAY sẽ gọi lại.

Tham số, mã phản hồi và định dạng cụ thể cần được đối chiếu với **tài liệu VNPAY Sandbox phiên bản mới nhất**.

---

## API

### Swagger UI / OpenAPI

Khi backend đang chạy (dev mặc định `http://localhost:8080`):

| Mục | Địa chỉ |
|---|---|
| Swagger UI (bấm **Try it out** để gọi API) | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml |

- Tiêu đề, phiên bản, tag và mô tả chung cấu hình tại
  `backend/src/main/java/com/example/shop/common/config/OpenApiConfig.java`; schema DTO và ví dụ
  payload lấy từ `@Schema`/`@Operation`/`@ApiResponse` trong controller và DTO.
- Lỗi `400/409/500` dùng schema `ApiProblemDetail` (RFC 7807 kèm mảng `errors[]` khi validate),
  khớp với `specs/001-user-registration/contracts/auth-register.openapi.yaml`.
- `tryItOutEnabled`, sắp xếp operation theo HTTP method, hiển thị thời lượng request và ô lọc được
  bật trong `application.yml`.
- Thử nhanh US-01: mở Swagger UI → `POST /api/v1/auth/register` → **Try it out** → chọn ví dụ
  `minimal` → **Execute**. Lần đầu nhận `201`; gửi lại cùng email nhận `409`.

Tài liệu đầy đủ ở Swagger UI (`/swagger-ui.html`). Tổng quan các nhóm endpoint (tiền tố `/api/v1`):

| Nhóm | Endpoint | Quyền |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` | Công khai (`me`: đã đăng nhập) |
| Sản phẩm | `GET /products` (lọc, phân trang), `GET /products/{id}` | Công khai |
| Sản phẩm (quản trị) | `POST/PUT/DELETE /admin/products` | ADMIN |
| Giỏ hàng | `GET /cart`, `POST /cart/items`, `PUT/DELETE /cart/items/{id}` | CUSTOMER |
| Đơn hàng | `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | CUSTOMER (đơn của mình) |
| Đơn hàng (quản trị) | `GET /admin/orders`, `PUT /admin/orders/{id}/status` | ADMIN |
| Tồn kho | `PUT /admin/inventory/{productId}` | ADMIN |
| Thanh toán | `POST /payments/vnpay` | CUSTOMER |
| Callback VNPAY | `GET /payments/vnpay/return`, `GET /payments/vnpay/ipn` | Công khai, xác thực bằng chữ ký |

Lỗi trả về theo định dạng `ProblemDetail` (RFC 7807).

### Đăng ký tài khoản (US-01 — đã triển khai)

`POST /api/v1/auth/register` — công khai, không cấp phiên đăng nhập.

```json
{
  "email": "khachhang@example.com",
  "password": "MatKhau123",
  "fullName": "Nguyễn Văn A",
  "phone": "0987654321"
}
```

`phone` là tùy chọn. Kết quả:

| Mã | Nội dung |
|---|---|
| `201 Created` | `id`, `email` (đã trim + lowercase), `fullName`, `phone`, `role`, `status`, `createdAt`. Không chứa mật khẩu hay hash. |
| `400 Bad Request` | `errors[]` liệt kê **mọi** trường vi phạm (email, mật khẩu, họ tên, số điện thoại). |
| `409 Conflict` | `type=https://api.shoptila.com/errors/email-already-exists` khi email đã tồn tại, kể cả khác hoa/thường hoặc có khoảng trắng. |

Quy tắc đã áp dụng:

- Mật khẩu 8–72 ký tự, có ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và **không vượt 72 byte UTF-8**; lưu dưới dạng BCrypt strength 12.
- Server luôn gán `ROLE_CUSTOMER` và `ACTIVE`; thuộc tính `role`/`isActive` do client gửi bị bỏ qua.
- Email là duy nhất ở CSDL (`uq_auth_users_email`): hai đăng ký đồng thời cùng email tạo đúng một tài khoản, yêu cầu còn lại nhận `409`, không có `5xx`.
- Đăng ký thành công không tự đăng nhập; giao diện chuyển người dùng sang `/login`.

---

## Kiểm thử

```bash
cd backend && ./mvnw test          # Testcontainers cần Docker đang chạy
cd frontend && npm test -- --run   # Vitest (CI mode)
cd frontend && npm run build       # tsc --noEmit && vite build
```

Các nhóm test quan trọng:

| Test | Mục đích |
|---|---|
| `InventoryConcurrencyTest` | Nhiều luồng cùng mua, tồn kho không bị âm |
| `VnpaySignatureTest` | Tạo và kiểm tra chữ ký VNPAY |
| `IpnIdempotencyTest` | Gọi IPN nhiều lần chỉ xử lý một lần |
| `OrderFlowTest` | Luồng đặt hàng, thanh toán, hủy đơn |

Test của US-01 (đã có):

| Test | Mục đích |
|---|---|
| `AuthServiceTest` | Chuẩn hóa email, băm mật khẩu, gán quyền, chặn trùng email |
| `AuthRegisterIntegrationTest` | HTTP 201/409, body không lộ hash, bỏ qua `role` do client gửi |
| `RegistrationConcurrencyTest` | Hai yêu cầu đồng thời cùng email → một `201`, một `409`, một dòng dữ liệu |
| `AuthValidationIntegrationTest` | `400` trả đầy đủ lỗi từng trường, không lộ chi tiết nội bộ |
| `PasswordByteLimitTest` | Mật khẩu 72 byte hợp lệ, 73 byte và emoji đa byte bị từ chối với `400` |
| `RegisterForm.test.tsx`, `validation.test.ts` | Validate client, ánh xạ `400`/`409` vào trường, loading/redirect |

Integration test dùng Testcontainers (PostgreSQL), cần Docker đang chạy.

---

## Lộ trình

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| **1** | Modular monolith: auth, catalog, cart, inventory, order, payment, VNPAY Sandbox, Docker Compose | Đang làm — US-01 (đăng ký) hoàn thành |
| **2** | WebSocket/STOMP: trạng thái đơn realtime, chat khách và shop, thông báo, email. Job đối soát giao dịch với VNPAY. Refresh token | Kế hoạch |
| **3** | Tách microservices (ưu tiên `payment`, `inventory`), API Gateway, Kafka, outbox, Redis. Mỗi module một schema riêng | Kế hoạch |
| **4** | Vận hành: Resilience4j, Prometheus và Grafana, CI/CD, đo tải với JMeter | Kế hoạch |

---

## Quy ước phát triển

| Chủ đề | Quy ước |
|---|---|
| Đặt tên | `XxxController`, `XxxService`, `XxxRepository`; DTO dạng `CreateOrderRequest`, `OrderResponse`; event dạng `OrderPaidEvent` |
| API | Tiền tố `/api/v1`, không trả entity ra ngoài, luôn dùng DTO |
| Tiền tệ | `BigDecimal` hoặc số nguyên VND |
| Bí mật | Chỉ nằm trong biến môi trường |
| Ranh giới module | Gọi nhau qua `api/` hoặc event, không dùng quan hệ JPA chéo module |
| Migration | Mọi thay đổi CSDL đi qua Flyway, không sửa script đã chạy |
| Git | Nhánh `main`, `develop`, `feature/<module>-<mô-tả>`; commit theo module |

---

## Tổ chức thư mục dự án

Chú thích: các mục không đánh dấu thuộc **Giai đoạn 1**. *[GĐ2]*, *[GĐ3]*, *[GĐ4]* là phần bổ sung ở các giai đoạn sau.

```
shop/
├── README.md
├── .env.example                  # DB_*, JWT_*, VNPAY_* (chỉ chứa tên biến, không chứa giá trị thật)
├── .gitignore
├── docker-compose.yml            # postgres, backend, frontend (+ redis, broker ở GĐ3)
├── docker-compose.override.yml   # cấu hình dev
├── .gitlab-ci.yml                # [GĐ4] hoặc .github/workflows/
├── docs/
│   ├── architecture.md           # kiến trúc tổng thể, quy tắc module
│   ├── erd/                      # ERD theo module
│   ├── sequence/                 # sequence diagram: đặt hàng, VNPAY, IPN
│   ├── adr/                      # các quyết định kiến trúc
│   └── api/                      # OpenAPI export
│
├── backend/                      # Spring Boot (modular monolith)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/example/shop/
│       │   │   ├── ShopApplication.java
│       │   │   │
│       │   │   ├── common/                  # thuần kỹ thuật, không chứa nghiệp vụ
│       │   │   │   ├── config/              # OpenApi, Cors, Async/Scheduling, Jackson
│       │   │   │   │                        #   Redis [GĐ3], WebSocket [GĐ2]
│       │   │   │   ├── security/            # SecurityConfig, JwtVerifier, JwtAuthFilter,
│       │   │   │   │                        #   CurrentUser; StompAuthInterceptor [GĐ2]
│       │   │   │   ├── exception/           # ErrorCode, BusinessException, NotFoundException,
│       │   │   │   │                        #   GlobalExceptionHandler (ProblemDetail)
│       │   │   │   ├── dto/                 # ApiResponse, PageResponse
│       │   │   │   ├── persistence/         # BaseEntity
│       │   │   │   ├── outbox/              # [GĐ3] phát event bền vững (OutboxWriter, OutboxRelayJob)
│       │   │   │   ├── web/                 # TraceIdFilter [GĐ4]
│       │   │   │   └── util/                # MoneyUtils
│       │   │   │
│       │   │   ├── auth/
│       │   │   │   ├── web/                 # AuthController (register, login, me), dto/
│       │   │   │   ├── service/             # AuthService, TokenService
│       │   │   │   ├── domain/              # User, Role (CUSTOMER, ADMIN)
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── catalog/
│       │   │   │   ├── api/                 # CatalogApi (snapshot sản phẩm cho cart, order)
│       │   │   │   ├── web/                 # ProductController, AdminProductController, dto/
│       │   │   │   ├── service/             # ProductService, ProductSearchSpec
│       │   │   │   ├── domain/              # Product, Category, Brand
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── cart/
│       │   │   │   ├── web/                 # CartController, dto/
│       │   │   │   ├── service/             # CartService
│       │   │   │   ├── domain/              # Cart, CartItem
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── inventory/
│       │   │   │   ├── api/                 # InventoryApi (reserve, release)
│       │   │   │   ├── web/                 # AdminInventoryController, dto/
│       │   │   │   ├── service/             # InventoryService
│       │   │   │   ├── domain/              # StockItem (StockReservation, StockMovement ở GĐ3)
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── order/
│       │   │   │   ├── api/                 # OrderApi (markPaid, markPaymentFailed)
│       │   │   │   ├── web/                 # OrderController, AdminOrderController, dto/
│       │   │   │   ├── service/             # OrderService, OrderStateMachine, OrderExpiryJob
│       │   │   │   ├── domain/              # Order, OrderItem, OrderStatus, ShippingInfo
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── payment/
│       │   │   │   ├── api/                 # PaymentApi (createPaymentUrl)
│       │   │   │   ├── web/                 # PaymentController, VnpayCallbackController, dto/
│       │   │   │   ├── service/             # PaymentService (IPN idempotent)
│       │   │   │   ├── vnpay/               # VnpayProperties, VnpayUrlBuilder,
│       │   │   │   │                        #   VnpaySignatureVerifier; VnpayQueryClient [GĐ2]
│       │   │   │   ├── domain/              # PaymentTransaction, PaymentStatus
│       │   │   │   ├── job/                 # PaymentReconciliationJob [GĐ2]
│       │   │   │   └── repository/
│       │   │   │
│       │   │   ├── chat/                    # [GĐ2]
│       │   │   │   ├── web/                 # ChatRestController, ChatWsController, dto/
│       │   │   │   ├── service/             # ChatService
│       │   │   │   ├── domain/              # Conversation, ChatMessage
│       │   │   │   └── repository/
│       │   │   │
│       │   │   └── notification/            # [GĐ2]
│       │   │       ├── web/                 # NotificationController, dto/
│       │   │       ├── service/             # NotificationService, EmailService
│       │   │       ├── domain/              # Notification
│       │   │       ├── listener/            # nghe event của order
│       │   │       └── repository/
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── logback-spring.xml       # [GĐ4] log JSON có traceId
│       │       └── db/migration/
│       │           ├── V1__init.sql         # GĐ1: bảng của auth, catalog, cart, inventory, order, payment
│       │           └── V2__seed_data.sql    # dữ liệu mẫu để demo
│       │                                    # GĐ3: tách thư mục và schema riêng theo module
│       │
│       └── test/java/com/example/shop/
│           ├── support/                     # AbstractIntegrationTest (Testcontainers)
│           ├── architecture/                # ArchitectureTest (ArchUnit) [GĐ2]
│           ├── inventory/                   # InventoryConcurrencyTest
│           ├── payment/                     # VnpaySignatureTest, IpnIdempotencyTest
│           └── order/                       # OrderFlowTest
│
├── frontend/                     # React + Vite + TypeScript
│   ├── Dockerfile                # build Vite, phục vụ bằng nginx
│   ├── nginx.conf                # proxy /api (và /ws ở GĐ2) sang backend
│   ├── vite.config.ts            # proxy khi chạy dev
│   ├── package.json
│   └── src/
│       ├── main.tsx
│       ├── app/
│       │   ├── router.tsx        # nhóm route: công khai, khách, admin
│       │   └── providers.tsx     # QueryClient, Auth
│       ├── shared/
│       │   ├── api/              # axiosClient.ts (gắn JWT); stompClient.ts [GĐ2]
│       │   ├── components/       # Button, Modal, Pagination, Spinner
│       │   ├── hooks/
│       │   └── utils/            # định dạng tiền VND, ngày
│       └── features/             # mỗi feature: pages/, components/, api.ts, types.ts
│           ├── auth/             # login, register, authStore, ProtectedRoute, RoleGuard
│           ├── catalog/          # danh sách, bộ lọc, chi tiết
│           ├── cart/
│           ├── checkout/         # thông tin giao hàng, chuyển sang VNPAY
│           ├── order/            # lịch sử đơn, chi tiết đơn
│           ├── payment/          # trang kết quả: lấy trạng thái thật từ API
│           ├── admin/            # CRUD sản phẩm, quản lý đơn, tồn kho
│           ├── chat/             # [GĐ2]
│           └── notification/     # [GĐ2]
│
└── services/                     # [GĐ3] các microservice tách từ backend
    ├── api-gateway/
    ├── auth-service/
    ├── catalog-service/
    ├── order-service/
    ├── inventory-service/
    ├── payment-service/
    ├── chat-service/
    ├── notification-service/
    └── shared-contracts/         # chỉ chứa định nghĩa event, không chứa logic
```