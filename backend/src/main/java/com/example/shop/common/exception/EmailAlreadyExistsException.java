package com.example.shop.common.exception;

/**
 * Xảy ra khi email đăng ký đã tồn tại. Được ánh xạ thành {@code 409 Conflict} theo RFC 7807.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email '" + email + "' đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.");
    }

    public EmailAlreadyExistsException(String email, Throwable cause) {
        super("Email '" + email + "' đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.", cause);
    }
}
