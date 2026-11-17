# ADR 0001 — Mô hình vai trò của tài khoản: một cột `role` trên `auth_users`

- **Trạng thái**: Đã chấp nhận
- **Ngày**: 2026-11-17
- **Tính năng liên quan**: `specs/001-user-registration` (US-01)
- **Liên quan**: `research.md` R-01, `specs/001-user-registration/data-model.md`

## Bối cảnh

US-01 tạo tài khoản khách hàng qua `POST /api/v1/auth/register`. Giai đoạn 1 (MVP) chỉ có hai
vai trò cố định (`ROLE_CUSTOMER`, `ROLE_ADMIN`) và mỗi tài khoản chỉ cần một vai trò chính.
Sprint Backlog 1.1 phác thảo mô hình chuẩn hóa gồm `auth_roles` + `auth_user_roles` (nhiều-nhiều).

## Quyết định

Lưu vai trò trực tiếp trong cột `auth_users.role` kiểu `VARCHAR(30) NOT NULL DEFAULT
'ROLE_CUSTOMER'`, có ràng buộc `CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN'))`, ánh xạ sang enum
Java `com.example.shop.auth.domain.Role` bằng `@Enumerated(EnumType.STRING)`.

Không tạo bảng `auth_roles` / `auth_user_roles` ở Giai đoạn 1.

## Lý do

- Mỗi tài khoản MVP chỉ có một vai trò; bảng nối nhiều-nhiều thêm hai bảng, hai repository và logic
  gán vai trò mà chưa có nhu cầu thực tế.
- Tránh JOIN không cần thiết trên mọi lần xác thực/phân quyền.
- Ràng buộc `CHECK` và enum giữ tập vai trò hữu hạn, dễ đọc trong CSDL, không phụ thuộc thứ tự
  ordinal.
- Endpoint đăng ký không có trường `role` trong DTO, nên việc gán `ROLE_CUSTOMER` ở server là mặc
  định của kiểu dữ liệu, không phải một câu `if` dễ quên.

## Hệ quả

- API trả `role` dạng chuỗi `ROLE_CUSTOMER`; frontend không phụ thuộc cấu trúc lưu trữ.
- Khi cần nhiều vai trò/tài khoản ở giai đoạn sau, thêm migration mới (ví dụ
  `V3__auth_roles.sql`) tạo `auth_roles` + `auth_user_roles`, sao chép dữ liệu từ `role`, rồi bỏ
  cột. Thay đổi là additive với API: có thể thêm `roles: string[]` mà giữ `role` để tương thích.
- Không thể gán nhiều vai trò cho một tài khoản cho đến khi migration nâng cấp được thực hiện.

## Phương án đã cân nhắc

| Phương án | Lý do loại |
|---|---|
| `auth_roles` + `auth_user_roles` ngay từ đầu | Đúng chuẩn RBAC mở rộng nhưng vượt nhu cầu MVP; thêm JOIN và mã quản lý |
| Kiểu `ENUM` của PostgreSQL | Thêm giá trị cần `ALTER TYPE`, khó tiến hóa; `VARCHAR` + `CHECK` linh hoạt hơn |
| Lưu vai trò trong JWT là nguồn duy nhất | Trái nguyên tắc RBAC backend phải kiểm tra quyền từ dữ liệu/CSDL |
