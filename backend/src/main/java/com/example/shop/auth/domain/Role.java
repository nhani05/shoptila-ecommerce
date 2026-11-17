package com.example.shop.auth.domain;

/**
 * Vai trò của tài khoản. Giai đoạn 1: một vai trò cho mỗi tài khoản.
 * Chỉ {@link #ROLE_CUSTOMER} được gán qua endpoint đăng ký công khai.
 */
public enum Role {
    ROLE_CUSTOMER,
    ROLE_ADMIN
}
