# Feature Specification: Đăng ký tài khoản khách hàng mới (US-01)

**Feature Branch**: `feature/us-01-auth-register`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "[US-01 - UC01: Đăng ký tài khoản] Cho phép khách vãng lai (Guest) đăng ký tài khoản khách hàng mới trên Shoptila bằng email, mật khẩu, họ tên và số điện thoại. Hệ thống kiểm tra email duy nhất, validate định dạng đầu vào, băm mật khẩu bằng BCrypt, mặc định gán quyền ROLE_CUSTOMER và kích hoạt tài khoản. Trả về thông tin tài khoản vừa tạo (không lộ password_hash) hoặc lỗi rõ ràng theo chuẩn ProblemDetail (RFC 7807) nếu email đã tồn tại hoặc dữ liệu không hợp lệ."

## User Scenarios & Testing *(mandatory)*

> **Lưu ý cách gọi tên:** US-01.1 → US-01.3 dưới đây là các **phần ưu tiên của cùng một user
> story US-01**, không phải các user story độc lập. Chúng được tách để triển khai và kiểm thử
> tăng dần (MVP → chặn trùng email → xử lý lỗi dữ liệu).

### US-01.1 - Khách vãng lai đăng ký tài khoản thành công (Priority: P1)

Một khách vãng lai (chưa có tài khoản, chưa đăng nhập) truy cập trang đăng ký của Shoptila, nhập họ tên, email, mật khẩu, xác nhận mật khẩu và (tùy chọn) số điện thoại, rồi gửi yêu cầu. Hệ thống tạo tài khoản khách hàng đang hoạt động, gán quyền khách hàng mặc định, và xác nhận kết quả cho người dùng.

**Why this priority**: Đây là bước vào cửa của toàn bộ hành trình mua hàng — không có tài khoản thì không thể thêm vào giỏ, đặt hàng hay theo dõi đơn. Toàn bộ các user story còn lại của Sprint 1 (đăng nhập, giỏ hàng, đơn hàng) đều phụ thuộc vào nó.

**Independent Test**: Có thể kiểm thử độc lập bằng cách gửi một yêu cầu đăng ký với thông tin hợp lệ mới và xác nhận rằng một tài khoản đang hoạt động, mang quyền khách hàng, có thể đăng nhập được ngay sau đó. Không cần bất kỳ tính năng nào khác ngoài đăng nhập.

**Acceptance Scenarios**:

1. **Given** một khách vãng lai với email chưa từng được đăng ký, **When** người đó gửi họ tên, email, mật khẩu hợp lệ và số điện thoại hợp lệ, **Then** hệ thống tạo tài khoản mới ở trạng thái đang hoạt động, gán quyền khách hàng (không phải quản trị), và trả về thông tin tài khoản vừa tạo kèm xác nhận thành công.
2. **Given** một yêu cầu đăng ký hợp lệ, **When** hệ thống trả kết quả, **Then** kết quả chứa định danh tài khoản, email, họ tên, số điện thoại (nếu có), trạng thái, quyền và thời điểm tạo — nhưng **không** chứa mật khẩu hay dữ liệu băm mật khẩu dưới bất kỳ hình thức nào.
3. **Given** một tài khoản vừa được tạo, **When** người dùng đăng nhập bằng đúng email và mật khẩu đã dùng khi đăng ký, **Then** đăng nhập thành công với quyền khách hàng.
4. **Given** một khách vãng lai không nhập số điện thoại, **When** người đó gửi yêu cầu với các trường bắt buộc hợp lệ, **Then** tài khoản vẫn được tạo thành công (số điện thoại là tùy chọn).

---

### US-01.2 - Ngăn đăng ký trùng email (Priority: P2)

Một khách vãng lai cố đăng ký bằng email đã được sử dụng. Hệ thống từ chối yêu cầu, không tạo tài khoản thứ hai, và cho biết rõ nguyên nhân để người dùng chuyển sang đăng nhập hoặc dùng email khác.

**Why this priority**: Email là định danh đăng nhập duy nhất. Trùng email sẽ khiến người dùng không thể đăng nhập đúng tài khoản và phá vỡ liên kết giữa giỏ hàng, đơn hàng và chủ sở hữu.

**Independent Test**: Tạo một tài khoản, sau đó gửi một yêu cầu đăng ký thứ hai với cùng email; xác nhận yêu cầu thứ hai bị từ chối với thông báo trùng email và số lượng tài khoản không đổi.

**Acceptance Scenarios**:

1. **Given** email `khachhang@example.com` đã có tài khoản, **When** khách vãng lai đăng ký với đúng email đó, **Then** hệ thống từ chối yêu cầu và cho biết email đã được sử dụng.
2. **Given** email đã tồn tại ở dạng chữ thường, **When** khách vãng lai đăng ký với cùng email nhưng viết hoa khác nhau hoặc có khoảng trắng đầu/cuối, **Then** hệ thống vẫn nhận diện là trùng và từ chối.
3. **Given** hai yêu cầu đăng ký cùng một email được gửi gần như đồng thời, **When** cả hai được xử lý, **Then** đúng một tài khoản được tạo và yêu cầu còn lại nhận lỗi trùng email (không có lỗi hệ thống, không có tài khoản thứ hai).

---

### US-01.3 - Từ chối dữ liệu không hợp lệ với lỗi theo từng trường (Priority: P3)

Một khách vãng lai gửi dữ liệu thiếu hoặc sai định dạng. Hệ thống không tạo tài khoản và trả về thông báo lỗi chỉ rõ trường nào sai và cần sửa thế nào, theo định dạng lỗi chuẩn của dự án.

**Why this priority**: Giúp người dùng tự sửa lỗi ngay tại chỗ, giảm bỏ dở đăng ký và giảm yêu cầu hỗ trợ. Không chặn được luồng thành công nhưng ảnh hưởng trực tiếp đến tỷ lệ hoàn tất.

**Independent Test**: Gửi lần lượt các yêu cầu vi phạm từng quy tắc (email sai định dạng, mật khẩu yếu, họ tên quá ngắn, số điện thoại sai) và xác nhận mỗi lần đều bị từ chối với thông tin đúng trường vi phạm, không tạo tài khoản.

**Acceptance Scenarios**:

1. **Given** khách vãng lai nhập email sai định dạng (ví dụ thiếu `@` hoặc tên miền), **When** gửi yêu cầu, **Then** hệ thống từ chối và chỉ rõ email không hợp lệ.
2. **Given** mật khẩu ngắn hơn 8 ký tự, hoặc thiếu chữ hoa, hoặc thiếu chữ thường, hoặc thiếu chữ số, **When** gửi yêu cầu, **Then** hệ thống từ chối và mô tả quy tắc mật khẩu chưa đạt.
3. **Given** họ tên rỗng, chỉ có khoảng trắng, hoặc dài hơn 100 ký tự, **When** gửi yêu cầu, **Then** hệ thống từ chối và chỉ rõ lỗi ở trường họ tên.
4. **Given** số điện thoại được nhập nhưng không đúng định dạng di động Việt Nam, **When** gửi yêu cầu, **Then** hệ thống từ chối và chỉ rõ số điện thoại không hợp lệ.
5. **Given** một trường bắt buộc bị bỏ trống, **When** gửi yêu cầu, **Then** phản hồi liệt kê đầy đủ mọi trường vi phạm trong cùng một lần (không dừng ở lỗi đầu tiên).
6. **Given** yêu cầu vi phạm nhiều quy tắc cùng lúc, **When** hệ thống phản hồi lỗi, **Then** lỗi tuân theo định dạng lỗi chuẩn của dự án (RFC 7807 `ProblemDetail`) và không tiết lộ chi tiết kỹ thuật nội bộ.

---

### Edge Cases

- **Đăng ký đồng thời cùng email**: Hai người dùng gửi cùng email tại cùng thời điểm — phải có đúng một tài khoản được tạo, yêu cầu kia nhận lỗi trùng email, không được trả lỗi hệ thống 5xx.
- **Email có khoảng trắng hoặc chữ hoa**: Được chuẩn hóa trước khi kiểm tra trùng và lưu trữ; không tạo hai tài khoản cho cùng một hộp thư.
- **Số điện thoại có khoảng trắng, dấu gạch, hoặc tiền tố nước ngoài**: Bị từ chối kèm thông báo; hệ thống không tự đoán định dạng.
- **Mật khẩu đúng 8 ký tự**: Được chấp nhận; 7 ký tự bị từ chối.
- **Mật khẩu vượt quá độ dài tối đa mà cơ chế bảo mật mật khẩu hỗ trợ**: Bị từ chối kèm thông báo — không được âm thầm cắt bớt, vì cắt bớt khiến người dùng không thể đăng nhập lại bằng đúng mật khẩu đã nhập.
- **Mật khẩu chứa khoảng trắng đầu/cuối**: Được giữ nguyên như người dùng nhập (không tự cắt khoảng trắng của mật khẩu).
- **Họ tên tiếng Việt có dấu**: Được chấp nhận và lưu nguyên vẹn.
- **Gửi kèm vai trò hoặc trạng thái trong yêu cầu**: Bị bỏ qua; hệ thống luôn gán quyền khách hàng và trạng thái đang hoạt động.
- **Gửi yêu cầu hai lần do người dùng bấm đúp**: Lần gửi đầu thành công; lần sau nhận lỗi trùng email. Giao diện phải ngăn gửi lặp trong khi đang xử lý.
- **Dịch vụ lưu trữ tạm thời không sẵn sàng**: Người dùng nhận lỗi chung dễ hiểu, không lộ thông tin nội bộ; không có tài khoản dở dang được tạo.
- **Xác nhận mật khẩu không khớp**: Bị chặn ngay trên giao diện trước khi gửi yêu cầu lên hệ thống.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống MUST cho phép khách vãng lai (chưa xác thực) tạo tài khoản khách hàng mới bằng email, mật khẩu và họ tên; số điện thoại là tùy chọn.
- **FR-002**: Hệ thống MUST chuẩn hóa email (cắt khoảng trắng đầu/cuối, chuyển về chữ thường) trước khi kiểm tra trùng lặp và lưu trữ.
- **FR-003**: Hệ thống MUST đảm bảo email là duy nhất trên toàn hệ thống; mọi nỗ lực tạo tài khoản với email đã tồn tại MUST bị từ chối và không tạo thêm tài khoản.
- **FR-004**: Hệ thống MUST từ chối đăng ký trùng email bằng một phản hồi lỗi xung đột rõ ràng, trong đó thông điệp cho người dùng biết email đã được sử dụng và gợi ý đăng nhập hoặc dùng email khác.
- **FR-005**: Hệ thống MUST kiểm tra định dạng email, họ tên, mật khẩu và số điện thoại trước khi tạo tài khoản; mọi vi phạm MUST được báo trong cùng một phản hồi, theo từng trường.
- **FR-006**: Mật khẩu MUST có tối thiểu 8 ký tự và chứa ít nhất một chữ hoa, một chữ thường và một chữ số.
- **FR-007**: Hệ thống MUST lưu mật khẩu duy nhất dưới dạng băm một chiều an toàn; MUST NOT lưu, ghi log, trả về hoặc có thể khôi phục mật khẩu dạng rõ dưới bất kỳ hình thức nào.
- **FR-008**: Hệ thống MUST không âm thầm cắt ngắn mật khẩu; mật khẩu vượt quá độ dài tối đa được hỗ trợ MUST bị từ chối kèm thông báo.
- **FR-009**: Mọi tài khoản được tạo qua đăng ký công khai MUST được gán duy nhất quyền khách hàng (`ROLE_CUSTOMER`); MUST NOT bao giờ được gán quyền quản trị, kể cả khi yêu cầu có gửi kèm thông tin quyền.
- **FR-010**: Tài khoản mới MUST ở trạng thái đang hoạt động (ACTIVE) ngay sau khi đăng ký, không cần bước xác minh email hay phê duyệt thủ công trong Giai đoạn 1.
- **FR-011**: Phản hồi đăng ký thành công MUST trả về định danh tài khoản, email đã chuẩn hóa, họ tên, số điện thoại (nếu có), trạng thái, quyền và thời điểm tạo; MUST NOT chứa mật khẩu, dữ liệu băm mật khẩu hoặc bất kỳ trường nội bộ nào khác.
- **FR-012**: Họ tên MUST được cắt khoảng trắng đầu/cuối và có độ dài từ 2 đến 100 ký tự.
- **FR-013**: Số điện thoại, nếu được cung cấp, MUST là số di động Việt Nam hợp lệ (bắt đầu bằng `0` hoặc `+84`, theo sau là đầu số `3`, `5`, `7`, `8`, `9` và 8 chữ số) và MUST được lưu ở dạng chuẩn.
- **FR-014**: Mọi phản hồi lỗi MUST tuân theo định dạng lỗi chuẩn của dự án (RFC 7807 `ProblemDetail`) với loại lỗi, tiêu đề, mã trạng thái, mô tả và đường dẫn yêu cầu; lỗi kiểm tra dữ liệu MUST chỉ rõ từng trường vi phạm.
- **FR-015**: Việc tạo tài khoản MUST có tính nguyên tử: nếu xảy ra tranh chấp email tại thời điểm ghi, hệ thống trả lỗi trùng email cho người dùng thay vì lỗi hệ thống, và không để lại dữ liệu dở dang.
- **FR-016**: Hệ thống MUST ghi nhận sự kiện đăng ký thành công và thất bại cho mục đích vận hành, không bao gồm mật khẩu, dữ liệu băm, token hay thông tin bí mật.
- **FR-017**: Đăng ký MUST không yêu cầu người dùng đã đăng nhập; sau khi đăng ký thành công, người dùng được hướng dẫn chuyển sang đăng nhập (hệ thống không tự động cấp phiên đăng nhập).

### Key Entities *(include if feature involves data)*

- **Tài khoản khách hàng (Customer Account)**: Đại diện một người mua trên Shoptila. Thuộc tính chính: định danh duy nhất, email (duy nhất, đã chuẩn hóa, dùng để đăng nhập), mật khẩu chỉ tồn tại dưới dạng băm, họ tên, số điện thoại tùy chọn, vai trò, trạng thái hoạt động và thời điểm tạo. Giai đoạn 1: mỗi tài khoản có **một** vai trò chính; mô hình nhiều vai trò được để mở cho các giai đoạn sau.
- **Vai trò (Role)**: Đại diện mức quyền của tài khoản. Giai đoạn 1 có hai vai trò: khách hàng (`ROLE_CUSTOMER`, mặc định cho đăng ký công khai) và quản trị (`ROLE_ADMIN`, chỉ gán qua dữ liệu khởi tạo/quản trị, không qua đăng ký).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ít nhất 95% khách vãng lai có thông tin hợp lệ hoàn tất đăng ký trong dưới 2 phút mà không cần hỗ trợ.
- **SC-002**: 100% tài khoản tạo mới đăng nhập được ngay sau đăng ký và có quyền khách hàng; 0% tài khoản đăng ký công khai có quyền quản trị.
- **SC-003**: 100% nỗ lực đăng ký trùng email bị từ chối với thông báo nêu rõ nguyên nhân; số tài khoản trùng được tạo bằng 0.
- **SC-004**: 0 trường hợp mật khẩu hoặc dữ liệu băm mật khẩu xuất hiện trong phản hồi hoặc log trong toàn bộ vòng kiểm thử.
- **SC-005**: 95% yêu cầu đăng ký hợp lệ nhận được phản hồi trong dưới 2 giây ở mức tải bình thường của Giai đoạn 1.
- **SC-006**: 100% phản hồi lỗi dữ liệu chỉ rõ trường vi phạm và cách khắc phục, để người dùng tự sửa thành công mà không cần liên hệ hỗ trợ.
- **SC-007**: 0 lỗi hệ thống (5xx) phát sinh từ các kịch bản đăng ký hợp lệ về nghiệp vụ, kể cả khi có tranh chấp email đồng thời.

## Assumptions

- **Khách vãng lai**: Người dùng chưa có tài khoản và chưa đăng nhập. Không có luồng đăng ký dành riêng cho quản trị viên trong tính năng này.
- **Không xác minh email/số điện thoại ở Giai đoạn 1**: Tài khoản hoạt động ngay; xác minh email/SMS thuộc các giai đoạn sau.
- **Không tự động đăng nhập sau đăng ký**: Sau khi thành công, giao diện hiển thị thông báo và chuyển người dùng sang trang đăng nhập (theo kế hoạch Sprint 1.1).
- **Đồng ý điều khoản**: Ô xác nhận điều khoản/chính sách bảo mật do giao diện bắt buộc trước khi gửi; hệ thống chưa lưu bằng chứng đồng ý ở Giai đoạn 1.
- **Chống lạm dụng cơ bản**: Giới hạn tần suất và chống bot không thuộc phạm vi Giai đoạn 1; rủi ro được ghi nhận để xử lý sau.
- **Không có yêu cầu số điện thoại duy nhất**: Hai tài khoản có thể dùng chung một số điện thoại; số điện thoại không dùng để đăng nhập ở Giai đoạn 1.
- **Ngôn ngữ thông báo**: Thông điệp lỗi hiển thị cho người dùng bằng tiếng Việt; mã lỗi máy đọc được giữ ổn định để giao diện ánh xạ.
- **Giao diện đăng ký thuộc phạm vi tính năng**: Bao gồm trang đăng ký với kiểm tra hợp lệ tức thời, đo độ mạnh mật khẩu, xác nhận mật khẩu, trạng thái đang xử lý và thông báo kết quả.

### Ràng buộc dự án đã được phê chuẩn

Các ràng buộc dưới đây được kế thừa từ hiến chương dự án và Sprint Backlog 1.1, không phải quyết định thiết kế mới của tính năng:

- Điểm cuối đăng ký: `POST /api/v1/auth/register`; thành công trả `201 Created`, dữ liệu không hợp lệ trả `400 Bad Request`, email trùng trả `409 Conflict`.
- Mật khẩu được băm bằng BCrypt trước khi lưu.
- Tên quyền sử dụng đúng chuẩn dự án: `ROLE_CUSTOMER`, `ROLE_ADMIN`.
- Lỗi tuân theo `ProblemDetail` (RFC 7807) như mọi điểm cuối khác của Shoptila.
- Bảng lưu trữ thuộc module `auth` và dùng tiền tố `auth_` (chi tiết lược đồ do bước lập kế hoạch quyết định).
