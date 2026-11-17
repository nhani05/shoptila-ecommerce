package com.example.shop.auth.web.dto;

import java.time.Instant;

/**
 * Thông tin tài khoản trả về sau khi đăng ký.
 *
 * <p>Bất biến bảo mật: không có mật khẩu, hash, token hay trường nội bộ nào.</p>
 */
public record RegisterResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String role,
        String status,
        Instant createdAt
) {
}
