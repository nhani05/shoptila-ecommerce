# Quickstart Validation: US-01 — Đăng ký tài khoản khách hàng

**Feature**: `specs/001-user-registration` | **Date**: 2026-11-16

Hướng dẫn này chứng minh tính năng hoạt động đầu-cuối sau khi triển khai. Hợp đồng chi tiết ở
[`contracts/auth-register.openapi.yaml`](./contracts/auth-register.openapi.yaml), lược đồ và quy
tắc ở [`data-model.md`](./data-model.md). Không chép mã triển khai vào tài liệu này.

## 0. Điều kiện tiên quyết

- JDK 17+, Maven (hoặc `./mvnw` sau khi được tạo)
- Docker Engine đang chạy (PostgreSQL dev + Testcontainers)
- Node.js 20.19+ và npm
- Cổng trống: `5432` (PostgreSQL), `8080` (backend), `5173` (Vite dev)

## 1. Cấu hình môi trường

```bash
cd /home/nhanlx05/projects/shoptila-ecommerce
cp .env.example .env     # chỉnh nếu cổng/tài khoản khác
```

`.env` chứa `DB_*`; các biến `JWT_*`, `VNPAY_*` chỉ là placeholder ở giai đoạn này và không được
dùng bởi US-01. Không commit `.env`.

## 2. Khởi động PostgreSQL

```bash
docker compose up -d postgres
docker compose ps          # trạng thái phải là healthy
```

## 3. Chạy backend

```bash
cd backend
set -a; source ../.env; set +a
./mvnw spring-boot:run
```

Kỳ vọng:
- Log Flyway báo đã áp dụng `V1__init.sql` (version 1, success).
- Ứng dụng lắng nghe ở `http://localhost:8080`.
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/v3/api-docs` trả `200`.

## 4. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Mở `http://localhost:5173/register`. Vite proxy `/api` → `http://localhost:8080`, không có lỗi CORS.

## 5. Kịch bản kiểm tra bằng HTTP

Mỗi lệnh dùng biến `BASE=http://localhost:8080/api/v1`.

### 5.1 Đăng ký thành công (201)

```bash
curl -i -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"khachhang@example.com","password":"MatKhau123","fullName":"Nguyễn Văn A","phone":"0987654321"}'
```

Kỳ vọng:
- `HTTP/1.1 201`
- Body có `id`, `email` (lowercase), `fullName`, `phone`, `role: "ROLE_CUSTOMER"`,
  `status: "ACTIVE"`, `createdAt` ISO-8601.
- Body **không** chứa `password`, `passwordHash`, `hash`, `role: "ROLE_ADMIN"`.

### 5.2 Trùng email (409)

Chạy lại đúng lệnh 5.1 với một email khác hoa/thường hoặc thêm khoảng trắng:

```bash
curl -i -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"  KhachHang@Example.COM ","password":"MatKhau123","fullName":"Nguyễn Văn B"}'
```

Kỳ vọng: `409`, `Content-Type: application/problem+json`, `type` kết thúc bằng
`/errors/email-already-exists`, `instance` = `/api/v1/auth/register`.

### 5.3 Dữ liệu không hợp lệ (400, nhiều lỗi cùng lúc)

```bash
curl -i -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"khong-hop-le","password":"yeu","fullName":"A","phone":"0987 654 321"}'
```

Kỳ vọng: `400`, `errors[]` chứa đồng thời lỗi của `email`, `password`, `fullName`, `phone`; thông
điệp tiếng Việt; không có stack trace.

### 5.4 Mật khẩu vượt 72 byte (400, không 500)

```bash
curl -i -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"dai@example.com","password":"Aaaaaaaaa1😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀","fullName":"Nguyễn Văn A"}'
```

Kỳ vọng: `400` với lỗi ở trường `password`; **không** phải `500` và không có
`IllegalArgumentException` trong body.

### 5.5 Không thể tự gán quyền quản trị

```bash
curl -s -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"thu-role@example.com","password":"MatKhau123","fullName":"Nguyễn Văn A","role":"ROLE_ADMIN","isActive":false}'
```

Kỳ vọng: `201`, `role: "ROLE_CUSTOMER"`, `status: "ACTIVE"`.

### 5.6 Trạng thái lưu trữ

```bash
docker compose exec postgres psql -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT id, email, role, is_active, left(password_hash, 7) AS hash_prefix FROM auth_users ORDER BY id;"
```

Kỳ vọng:
- Email đã lowercase; không có hai dòng trùng email.
- `role = ROLE_CUSTOMER`, `is_active = t`.
- `hash_prefix` là `$2a$12$` (hoặc `$2b$12$`/`$2y$12$`) — cost 12.

## 6. Kịch bản kiểm tra trên trình duyệt

1. Mở `http://localhost:5173/register`.
2. Gửi form rỗng → mỗi trường bắt buộc hiện lỗi riêng; không có request mạng nào được gửi.
3. Nhập email sai định dạng → lỗi email; nhập mật khẩu yếu → thanh độ mạnh báo đỏ/vàng và lỗi quy
   tắc; xác nhận mật khẩu không khớp → lỗi ngay, nút gửi bị chặn hoặc báo lỗi.
4. Nhập dữ liệu hợp lệ mới → nút chuyển trạng thái đang xử lý, input bị vô hiệu hóa; nhận thông báo
   thành công (`role="status"`/`aria-live`) và được chuyển tới `/login` sau khoảng 1,5 giây.
5. Đăng ký lại cùng email → ô email được đánh dấu lỗi với thông điệp từ `detail` của 409; các trường
   khác giữ nguyên giá trị.
6. Kiểm tra DevTools: không có lỗi console, không có phản hồi 5xx; header bảo mật mặc định của
   Spring Security hiện diện; không có request nào lộ mật khẩu trong response.

## 7. Kiểm thử tự động

```bash
# Backend — unit + integration (Testcontainers cần Docker) + concurrency
cd backend && ./mvnw test

# Frontend — unit/component
cd frontend && npm test -- --run

# Cổng build bắt buộc
cd frontend && npm run build
```

Kỳ vọng: tất cả xanh; báo cáo Testcontainers cho thấy PostgreSQL khởi tạo và Flyway áp dụng V1.

## 8. Dọn dẹp

```bash
docker compose down          # thêm -v nếu muốn xóa dữ liệu dev
```

## 9. Ngoài phạm vi kiểm tra US-01

- Đăng nhập và cấp JWT (US-02): trang `/login` chỉ là placeholder.
- Giới hạn tần suất/ chống bot: đã ghi nhận rủi ro, chưa triển khai theo Assumptions của spec.
- Xác minh email/SMS, quên mật khẩu, cập nhật hồ sơ.
