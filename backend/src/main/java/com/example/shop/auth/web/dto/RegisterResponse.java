package com.example.shop.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Thông tin tài khoản trả về sau khi đăng ký.
 *
 * <p>Bất biến bảo mật: không có mật khẩu, hash, token hay trường nội bộ nào.</p>
 */
@Schema(
        name = "RegisterResponse",
        description = "Tài khoản vừa tạo. Không bao giờ chứa mật khẩu hay hash."
)
public record RegisterResponse(

        @Schema(description = "ID tài khoản", example = "42")
        Long id,

        @Schema(description = "Email đã chuẩn hóa (trim + lowercase)", example = "khachhang@example.com")
        String email,

        @Schema(description = "Họ và tên", example = "Nguyễn Văn A")
        String fullName,

        @Schema(description = "Số điện thoại, có thể null", example = "0987654321", nullable = true)
        String phone,

        @Schema(description = "Vai trò do server gán", example = "ROLE_CUSTOMER")
        String role,

        @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
        String status,

        @Schema(
                description = "Thời điểm tạo tài khoản (UTC)",
                example = "2026-11-17T07:15:30Z",
                type = "string",
                format = "date-time"
        )
        Instant createdAt
) {
}
