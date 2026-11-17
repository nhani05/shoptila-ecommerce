# Data Model: US-01 — Đăng ký tài khoản khách hàng

**Feature**: `specs/001-user-registration` | **Date**: 2026-11-16

Nguồn: `spec.md` (FR-001…FR-017) và các quyết định trong `research.md`.

---

## 1. Lược đồ CSDL (Flyway)

File: `backend/src/main/resources/db/migration/V1__init.sql` (migration đầu tiên của dự án; không
có migration nào tồn tại trước đó).

```sql
CREATE TABLE auth_users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_CUSTOMER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_users_email       UNIQUE (email),
    CONSTRAINT ck_auth_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_auth_users_role        CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN'))
);

CREATE INDEX idx_auth_users_created_at ON auth_users (created_at);
```

### Quyết định lược đồ

| Quyết định | Lý do |
|---|---|
| `UNIQUE (email)` | CSDL là cơ chế phân xử cuối cùng cho tranh chấp đồng thời; unique index phục vụ luôn truy vấn `existsByEmail` (không thêm index `email` trùng lặp). |
| `CHECK (email = lower(email))` | Phòng thủ chiều sâu: mọi đường ghi phải lưu email đã chuẩn hóa. |
| `role` là `VARCHAR(30)` + CHECK thay vì bảng vai trò | Quyết định R-01: MVP một vai trò/tài khoản; nâng cấp sang nhiều vai trò bằng migration mới. |
| `is_active BOOLEAN NOT NULL DEFAULT TRUE` | Tương ứng trạng thái `ACTIVE`; không có luồng vô hiệu hóa trong US-01. |
| `created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP` | Thời gian UTC có múi giờ; an toàn với nhiều môi trường. |
| `BIGSERIAL` | Khóa kỹ thuật đơn giản; ID không lộ thông tin nghiệp vụ. |
| `idx_auth_users_created_at` | Hỗ trợ danh sách/đếm tài khoản theo thời gian ở các tính năng admin sau; chi phí thấp. |

**Không** có `updated_at` trong US-01 vì chưa có luồng cập nhật tài khoản (đúng danh sách cột đã
chỉ định). Khi thêm tính năng cập nhật hồ sơ, bổ sung bằng migration mới.

**Không** có bảng `auth_roles` / `auth_user_roles` trong US-01 (xem R-01).

---

## 2. Mô hình miền (Java)

### 2.1 `com.example.shop.auth.domain.Role` (enum)

| Giá trị | Ý nghĩa |
|---|---|
| `ROLE_CUSTOMER` | Mặc định cho mọi đăng ký công khai |
| `ROLE_ADMIN` | Chỉ gán bởi seed/quản trị; không bao giờ qua endpoint đăng ký |

Lưu dạng `@Enumerated(EnumType.STRING)` để CSDL đọc được bằng mắt và không phụ thuộc thứ tự ordinal.

### 2.2 `com.example.shop.auth.domain.User` (JPA entity)

| Thuộc tính Java | Cột | Kiểu | Ghi chú |
|---|---|---|---|
| `id` | `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `email` | `email` | `String` | `@Column(nullable = false, unique = true, length = 150)`; luôn ở dạng chuẩn hóa |
| `passwordHash` | `password_hash` | `String` | `@Column(nullable = false, length = 255)`; không có getter trả về mật khẩu gốc |
| `fullName` | `full_name` | `String` | `@Column(nullable = false, length = 100)` |
| `phone` | `phone` | `String` (nullable) | `@Column(length = 20)` |
| `role` | `role` | `Role` | `@Enumerated(STRING)`, `@Column(nullable = false, length = 30)` |
| `active` | `is_active` | `boolean` | `@Column(name = "is_active", nullable = false)` |
| `createdAt` | `created_at` | `Instant` | `@Column(name = "created_at", nullable = false, updatable = false)`; gán bởi service hoặc `@CreationTimestamp` |

**Quy tắc JPA**:
- `@Table(name = "auth_users")`.
- Không có quan hệ JPA chéo module (entity duy nhất của module `auth`).
- Không có constructor rỗng công khai ngoài yêu cầu JPA; các trường được gán qua factory
  `User.createCustomer(...)` để không thể vô tình tạo tài khoản admin.
- Không dùng `@Data`/`toString()` sinh tự động chứa `passwordHash`; `toString()` không bao gồm hash.

### 2.3 `com.example.shop.auth.repository.UserRepository`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
}
```

Không thêm `findByEmail` ở US-01 (thuộc US-02 đăng nhập).

---

## 3. Ma trận kiểm tra dữ liệu

Thứ tự thực thi: Jackson dựng `RegisterRequest` (compact constructor chuẩn hóa) → Bean Validation →
service (chuẩn hóa lần cuối/ghi) → ràng buộc CSDL.

| Trường | Quy tắc | Nơi thực thi | Thông điệp mẫu / trường lỗi |
|---|---|---|---|
| `email` | Bắt buộc; trim + lowercase trước khi kiểm tra; đúng định dạng email; ≤150 ký tự | DTO + Bean Validation + CHECK/UNIQUE CSDL | field `email` |
| `password` | Bắt buộc; 8–72 ký tự; ≥1 chữ hoa, ≥1 chữ thường, ≥1 chữ số; ≤72 byte UTF-8; **không** trim | Bean Validation + validator tùy biến | field `password` |
| `fullName` | Bắt buộc; trim; 2–100 ký tự | DTO + Bean Validation | field `fullName` |
| `phone` | Tùy chọn; rỗng → `null`; nếu có: `^(0\|\+84)[35789][0-9]{8}$`; ≤20 ký tự | DTO + Bean Validation | field `phone` |
| `role`, `isActive`, `status`, `id` | Client **không** được gửi; nếu có trong JSON thì bị bỏ qua | DTO không có các trường này | — (không tạo lỗi) |

### Ví dụ giá trị biên

| Đầu vào | Kết quả |
|---|---|
| `email = "  KhachHang@Example.COM "` | Lưu `khachhang@example.com`; trùng với bản ghi chữ thường |
| `password = "MatKhau1"` (8 ký tự ASCII) | Hợp lệ |
| `password = "matkhau1"` | 400 — thiếu chữ hoa |
| `password = "MậtKhẩu1"` (9 ký tự, nhiều byte) | Hợp lệ nếu ≤72 byte UTF-8 |
| `password` 40 ký tự emoji | 400 — vượt 72 byte dù chỉ 40 ký tự |
| `phone = "0987654321"` / `"+84987654321"` | Hợp lệ |
| `phone = ""` | Được chấp nhận, lưu `NULL` |
| `phone = "0987 654 321"` | 400 |
| `fullName = "  Nguyễn Văn A  "` | Lưu `Nguyễn Văn A` |
| `phone = "0912345678", role = "ROLE_ADMIN"` | Tài khoản vẫn `ROLE_CUSTOMER` |

---

## 4. Ánh xạ phản hồi

`RegisterResponse` (record) ← `User`:

| Trường response | Nguồn | Ghi chú |
|---|---|---|
| `id` | `user.id` | |
| `email` | `user.email` | đã chuẩn hóa |
| `fullName` | `user.fullName` | |
| `phone` | `user.phone` | `null` nếu không có |
| `role` | `user.role.name()` | `"ROLE_CUSTOMER"` |
| `status` | `user.active ? "ACTIVE" : "INACTIVE"` | US-01 luôn `ACTIVE` |
| `createdAt` | `user.createdAt` | ISO-8601 UTC |

**Bất biến bảo mật**: `RegisterResponse` không có trường mật khẩu, hash, token, hay thông tin nội
bộ. Không dùng entity làm body phản hồi.

---

## 5. Trạng thái và chuyển trạng thái

US-01 chỉ tạo tài khoản ở trạng thái `ACTIVE`. Không có chuyển trạng thái nào khác trong phạm vi
này. Các trạng thái tương lai (`INACTIVE`, khóa tài khoản) sẽ do tính năng quản trị bổ sung và phải
đi qua migration mới nếu cần cột/bảng.

---

## 6. Bất biến (invariants)

1. **Email duy nhất và chuẩn hóa**: không tồn tại hai dòng `auth_users` có email bằng nhau sau khi
   lower-case.
2. **Không lưu mật khẩu rõ**: `password_hash` luôn bắt đầu bằng tiền tố BCrypt (`$2a$`, `$2b$`
   hoặc `$2y$`) với cost 12; không log/không trả về.
3. **Quyền tối thiểu**: mọi dòng sinh ra từ endpoint đăng ký có `role = 'ROLE_CUSTOMER'` và
   `is_active = TRUE`.
4. **Toàn vẹn tạo tài khoản**: hoặc một dòng hoàn chỉnh được tạo, hoặc không dòng nào; không có bản
   ghi dở dang khi lỗi.
5. **Tranh chấp đồng thời**: với hai yêu cầu cùng email, đúng một yêu cầu nhận 201, yêu cầu còn lại
   nhận 409, không có 5xx.

---

## 7. Dữ liệu khởi tạo (ngoài phạm vi)

US-01 **không** tạo `V2__seed_data.sql` và không seed tài khoản admin/demo. Việc seed dữ liệu mẫu
sẽ được thực hiện ở tính năng/kế hoạch riêng (Sprint 0/1 khác) để tránh trộn dữ liệu demo vào
migration khởi tạo tính năng đăng ký.
