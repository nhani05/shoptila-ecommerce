<!--
Sync Impact Report
==================
Version change: (unversioned template) → 1.0.0
Bump rationale: Initial ratification. The previous file contained only template placeholders;
this amendment defines the Shoptila project's binding principles for the first time.
Modified principles: N/A — all principles newly defined.
Added principles:
  - I. Modular Monolith & Ranh giới Phase 1
  - II. Giao tiếp Chéo Module Chỉ qua api/ (NON-NEGOTIABLE)
  - III. Tiền tệ Chính xác — BigDecimal / Số nguyên VND
  - IV. VNPAY IPN là Nguồn Xác nhận Duy nhất (NON-NEGOTIABLE)
  - V. Chống Bán vượt Tồn kho bằng Atomic UPDATE (NON-NEGOTIABLE)
  - VI. Frontend React + Vite + TypeScript
Added sections:
  - Ràng buộc Công nghệ & Repository
  - Quy trình Phát triển & Cổng Chất lượng
Removed sections: None
Deferred placeholders: None
Follow-up TODOs: None
-->

# Shoptila Constitution

## Core Principles

### I. Modular Monolith & Ranh giới Phase 1

Backend của Shoptila PHẢI là một ứng dụng **Spring Boot 3 / Java 17+ duy nhất**, tổ chức
package-by-feature theo miền nghiệp vụ: `common`, `auth`, `catalog`, `cart`, `inventory`,
`order`, `payment`. Mỗi module PHẢI sở hữu trọn vẹn `domain/`, `repository/`, `service/`,
`web/` và `api/` của mình.

Chỉ các tính năng thuộc **Giai đoạn 1 (MVP)** được triển khai. Các hạng mục Giai đoạn 2–4
(WebSocket/STOMP, Redis, Kafka/RabbitMQ, Outbox, tách microservices, Prometheus/Grafana)
KHÔNG ĐƯỢC đưa vào mã nguồn trừ khi constitution được sửa đổi hoặc `plan.md` được phê duyệt
riêng cho hạng mục đó.

**Lý do:** Giữ nghiệp vụ trong một tiến trình giúp Phase 1 đơn giản, nhưng vẫn bảo toàn khả
năng tách microservices ở Giai đoạn 3 mà không phải viết lại nghiệp vụ.

### II. Giao tiếp Chéo Module Chỉ qua `api/` (NON-NEGOTIABLE)

1. Giao tiếp giữa các module PHẢI đi **duy nhất** qua package `api/` của module đích
   (`CatalogApi`, `InventoryApi`, `OrderApi`, `PaymentApi`) hoặc qua domain event.
   NGHIÊM CẤM import `domain`, `repository`, `service`, `web`, `job` của module khác.
2. NGHIÊM CẤM quan hệ JPA chéo module (`@ManyToOne`, `@OneToMany`, `@JoinColumn`). Chỉ được
   lưu khóa ngoại dưới dạng ID (`Long productId`, `Long orderId`).
3. Tên bảng PostgreSQL PHẢI có tiền tố module: `auth_users`, `catalog_products`, `cart_items`,
   `inventory_stocks`, `order_orders`, `payment_transactions`.
4. Module `common` KHÔNG ĐƯỢC phụ thuộc bất kỳ module nghiệp vụ nào. Phát hành token nằm ở
   `auth`; xác minh token (`JwtVerifier`, `JwtAuthFilter`) nằm ở `common`.
5. Controller KHÔNG ĐƯỢC trả về JPA `@Entity`; mọi endpoint PHẢI có tiền tố `/api/v1`, dùng DTO,
   và trả lỗi theo `ProblemDetail` (RFC 7807).

**Lý do:** Ranh giới module là điều kiện tiên quyết để tách service ở Giai đoạn 3; vi phạm
ranh giới làm phát sinh liên kết ẩn không thể tháo gỡ.

### III. Tiền tệ Chính xác — BigDecimal / Số nguyên VND

1. Mọi giá trị tiền VND PHẢI dùng `BigDecimal` hoặc `long` (số nguyên VND) thông qua
   `common.util.MoneyUtils`. NGHIÊM CẤM dùng `double` hoặc `float` cho tiền tệ.
2. Phép tính, so sánh, làm tròn PHẢI dùng API của `BigDecimal` (`compareTo`, `multiply`,
   `setScale`), không dùng `==`.
3. Khi gửi VNPAY, số tiền PHẢI được nhân `100` (`amount × 100`); khi nhận IPN/Return,
   `vnp_Amount` PHẢI được đối chiếu với số tiền kỳ vọng nhân `100`.
4. Khi tạo đơn, PHẢI lưu snapshot tên và giá sản phẩm vào `OrderItem`; giá trị đơn hàng do
   backend tính lại, KHÔNG ĐƯỢC tin giá do client gửi lên.

**Lý do:** Sai số dấu phẩy động gây lệch tiền và sai chữ ký VNPAY; snapshot bảo toàn lịch sử
đơn hàng khi giá sản phẩm thay đổi.

### IV. VNPAY IPN là Nguồn Xác nhận Duy nhất (NON-NEGOTIABLE)

1. Chữ ký `HMAC-SHA512` PHẢI được tạo và xác minh trên các tham số đã sắp xếp cho **cả**
   Return URL và IPN. Chữ ký không hợp lệ PHẢI bị từ chối và không được thay đổi trạng thái đơn.
2. IPN tại `/api/v1/payments/vnpay/ipn` là **nguồn xác nhận thanh toán duy nhất**. Chỉ IPN
   hợp lệ mới được chuyển đơn sang `PAID`. Return URL chỉ để hiển thị; frontend PHẢI đọc trạng
   thái thật từ `GET /api/v1/orders/{id}`.
3. `vnp_TxnRef` PHẢI duy nhất cho mỗi lần thanh toán và PHẢI có ràng buộc `UNIQUE` ở CSDL.
   IPN PHẢI đối chiếu `vnp_TxnRef` tồn tại và `vnp_Amount` khớp số tiền kỳ vọng trước khi xử lý.
4. Xử lý IPN PHẢI idempotent: chuyển trạng thái có điều kiện `PENDING → SUCCESS`, nhờ đó các lần
   VNPAY gọi lại chỉ thực thi `OrderApi.markPaid()` đúng **một lần**. Phản hồi IPN PHẢI đúng
   định dạng VNPAY yêu cầu.
5. Thời hạn URL thanh toán VNPAY PHẢI **ngắn hơn** thời hạn hủy đơn của `OrderExpiryJob`, để đơn
   đã hủy không thể được thanh toán muộn.

**Lý do:** Dữ liệu từ Return URL do trình duyệt gửi lên có thể bị sửa; IPN server-to-server kèm
chữ ký và idempotency là cơ sở duy nhất để tin rằng tiền đã được thu.

### V. Chống Bán vượt Tồn kho bằng Atomic UPDATE (NON-NEGOTIABLE)

1. Tồn kho KHÔNG ĐƯỢC đọc rồi ghi trong bộ nhớ Java. `InventoryApi.reserve()` PHẢI dùng câu lệnh
   cập nhật có điều kiện, nguyên tử:
   `UPDATE inventory_stocks SET quantity = quantity - :qty WHERE product_id = :id AND quantity >= :qty`,
   thực thi trong `@Transactional`.
2. Nếu số dòng cập nhật bằng `0`, giao dịch PHẢI thất bại và rollback ngay; KHÔNG ĐƯỢC trả về
   tồn kho âm trong bất kỳ tình huống đồng thời nào.
3. Hủy đơn hoặc hết hạn thanh toán PHẢI gọi `InventoryApi.release()` để hoàn tồn kho.
4. `InventoryConcurrencyTest` PHẢI chứng minh tồn kho không bao giờ âm khi nhiều luồng cùng mua,
   và PHẢI pass trước khi bất kỳ task `inventory`, `order` hoặc `payment` được đánh dấu hoàn thành.

**Lý do:** Kiểm tra rồi mới trừ ở tầng ứng dụng tạo race condition; chỉ điều kiện `quantity >= :qty`
trong một câu UPDATE duy nhất mới đảm bảo không bán vượt tồn kho.

### VI. Frontend React + Vite + TypeScript

1. Frontend PHẢI dùng **React + Vite + TypeScript**, tổ chức feature-sliced dưới `src/features/`;
   không dùng JavaScript thuần cho mã ứng dụng mới.
2. Mọi lời gọi API PHẢI đi qua `shared/api/axiosClient.ts` (gắn JWT); route PHẢI nhóm theo công
   khai / khách hàng / admin bằng React Router. `RoleGuard` chỉ phục vụ trải nghiệm;
   phân quyền thực sự PHẢI được backend thực thi.
3. Form PHẢI validate phía client (chặn số lượng âm, ký tự không phải số) song song với Bean
   Validation phía backend; giao diện PHẢI responsive trên desktop, tablet và mobile.
4. Hiển thị tiền PHẢI dùng định dạng VND tập trung trong `shared/utils`; frontend KHÔNG ĐƯỢC
   tự quyết định giá trị thanh toán.

**Lý do:** Một stack và một quy ước duy nhất giữ frontend nhất quán, dễ kiểm thử, và không tạo
kẽ hở tin tưởng dữ liệu client.

## Ràng buộc Công nghệ & Repository

- **Stack cố định:** Java 17+, Spring Boot 3, Spring Security (JWT `jjwt`, BCrypt), Spring Data
  JPA, Bean Validation, `springdoc-openapi`; PostgreSQL + Flyway; JUnit 5 + Testcontainers;
  Docker Compose + Nginx; VNPAY Sandbox.
- **Migration:** Mọi thay đổi CSDL PHẢI đi qua Flyway. NGHIÊM CẤM sửa script đã áp dụng; mọi
  thay đổi mới là một script phiên bản mới (`V3__...sql`, `V4__...sql`, ...).
- **Bí mật:** `DB_*`, `JWT_*`, `VNPAY_*` PHẢI đọc từ biến môi trường. NGHIÊM CẤM commit `.env`
  thật hoặc hardcode `JWT_SECRET` / `VNPAY_HASH_SECRET`; chỉ commit `.env.example`.
- **RBAC:** Vai trò `CUSTOMER` và `ADMIN` PHẢI được thực thi ở backend bằng Spring Security
  (`SecurityConfig` / `@PreAuthorize`), không chỉ dựa vào `RoleGuard` của React.
- **Tài liệu:** Endpoint PHẢI có chú thích Swagger; quyết định kiến trúc có đánh đổi PHẢI được ghi
  vào `docs/adr/`; `README.md` PHẢI phản ánh đúng trạng thái tính năng.

## Quy trình Phát triển & Cổng Chất lượng

- **Spec-Kit lifecycle:** Mọi tính năng PHẢI đi qua Specify → Clarify → Plan → Tasks → Implement.
  KHÔNG ĐƯỢC viết mã Java/TypeScript trước khi `plan.md` và `tasks.md` được phê duyệt.
- **TDD cho Critical Tier:** Logic trong `inventory`, `order`, `payment`, `cart` PHẢI có test
  JUnit 5 / Testcontainers thất bại trước (Red) rồi mới hiện thực (Green), sau đó refactor.
- **Cổng kiểm thử bắt buộc:** `InventoryConcurrencyTest`, `VnpaySignatureTest`,
  `IpnIdempotencyTest` và `OrderFlowTest` PHẢI pass qua `./mvnw test` trước khi hoàn thành bất kỳ
  task thuộc `inventory`, `order`, `payment`. Frontend PHẢI build thành công bằng Vite.
- **Xác minh trình duyệt:** Luồng lọc sản phẩm → giỏ hàng → checkout → VNPAY Sandbox → trang kết
  quả → quản trị PHẢI được kiểm tra trên trình duyệt thật, không có lỗi console và không có phản
  hồi 5xx trước khi phát hành.
- **Observability:** Log PHẢI có ngữ cảnh có cấu trúc (`orderId`, `vnp_TxnRef`, `userId`) cho
  checkout, IPN và job hết hạn; NGHIÊM CẤM log bí mật, chữ ký hoặc token.
- **Git:** Nhánh `feature/<module>-<mô-tả>`; commit theo module
  (ví dụ `feat(order): ...`, `test(payment): ...`).

## Governance

- Constitution này **thay thế** mọi thói quen và tài liệu khác khi có xung đột. `AGENTS.md` và
  `README.md` là hướng dẫn thực thi hằng ngày, nhưng không được trái với các nguyên tắc trên.
- **Sửa đổi:** Đề xuất sửa đổi qua `/speckit-constitution`, kèm lý do và tác động trong PR. Thay
  đổi PHẢI được người duy trì dự án phê duyệt trước khi có hiệu lực; Sync Impact Report PHẢI được
  đính kèm khi sửa đổi.
- **Phiên bản hóa:** Tuân theo Semantic Versioning. MAJOR khi xóa hoặc định nghĩa lại nguyên tắc
  không tương thích; MINOR khi thêm nguyên tắc/mục hoặc mở rộng đáng kể hướng dẫn; PATCH khi làm
  rõ câu chữ, sửa lỗi chính tả hoặc tinh chỉnh không đổi ngữ nghĩa.
- **Tuân thủ:** Mọi PR và review PHẢI kiểm tra các nguyên tắc này; vi phạm PHẢI chặn merge. Mọi
  phức tạp bổ sung PHẢI được biện minh. Kiểm tra tuân thủ diễn ra tại các cổng của từng giai đoạn
  Spec-Kit và trước khi phát hành.

**Version**: 1.0.0 | **Ratified**: 2026-11-16 | **Last Amended**: 2026-11-16
