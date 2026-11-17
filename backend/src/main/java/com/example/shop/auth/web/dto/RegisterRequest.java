package com.example.shop.auth.web.dto;

import com.example.shop.common.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Dữ liệu đăng ký từ client.
 *
 * <p>Chuẩn hóa ở compact constructor (chạy trước Bean Validation nhờ Jackson gọi canonical
 * constructor): email trim + lowercase, họ tên/điện thoại trim, điện thoại rỗng → {@code null}.
 * Mật khẩu không bị biến đổi. DTO cố ý <strong>không</strong> có {@code role}, {@code isActive},
 * {@code status}, {@code id} — thuộc tính lạ do client gửi sẽ bị Jackson bỏ qua.</p>
 */
@Schema(
        name = "RegisterRequest",
        description = """
                Dữ liệu đăng ký tài khoản khách hàng. Các trường `role`, `isActive`, `status`,
                `id` nếu client gửi kèm đều bị bỏ qua; server luôn gán ROLE_CUSTOMER và ACTIVE.
                """
)
public record RegisterRequest(

        @Schema(
                description = "Email đăng nhập; được trim và chuyển thành chữ thường",
                example = "khachhang@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 150
        )
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
        String email,

        @Schema(
                description = """
                        8–72 ký tự, tối đa 72 byte UTF-8, có ít nhất 1 chữ hoa, 1 chữ thường và
                        1 chữ số. Không bao giờ được trả về trong response.
                        """,
                example = "MatKhau123",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password",
                minLength = 8,
                maxLength = 72
        )
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số"
        )
        @MaxUtf8Bytes(72)
        String password,

        @Schema(
                description = "Họ và tên hiển thị; được trim",
                example = "Nguyễn Văn A",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 2,
                maxLength = 100
        )
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ và tên phải có từ 2 đến 100 ký tự")
        String fullName,

        @Schema(
                description = "Số điện thoại Việt Nam (tùy chọn); chuỗi rỗng được chuẩn hóa thành null",
                example = "0987654321",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                maxLength = 20
        )
        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        @Pattern(
                regexp = "^$|^(0|\\+84)[35789][0-9]{8}$",
                message = "Số điện thoại không hợp lệ"
        )
        String phone
) {

    public RegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
        if (fullName != null) {
            fullName = fullName.trim();
        }
        if (phone != null) {
            phone = phone.trim();
            if (phone.isEmpty()) {
                phone = null;
            }
        }
    }
}
