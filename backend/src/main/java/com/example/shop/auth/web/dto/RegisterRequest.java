package com.example.shop.auth.web.dto;

import com.example.shop.common.validation.MaxUtf8Bytes;
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
public record RegisterRequest(

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số"
        )
        @MaxUtf8Bytes(72)
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ và tên phải có từ 2 đến 100 ký tự")
        String fullName,

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
