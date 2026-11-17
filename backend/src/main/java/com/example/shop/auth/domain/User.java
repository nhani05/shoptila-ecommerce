package com.example.shop.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tài khoản người dùng lưu ở bảng {@code auth_users}.
 *
 * <p>Không có quan hệ JPA chéo module. Tài khoản công khai chỉ được tạo qua
 * {@link #createCustomer(String, String, String, String)} — không có đường nào để tạo
 * tài khoản {@link Role#ROLE_ADMIN} từ endpoint đăng ký.</p>
 */
@Entity
@Table(name = "auth_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Constructor dành cho JPA. Không dùng để tạo tài khoản nghiệp vụ. */
    protected User() {
    }

    private User(String email, String passwordHash, String fullName, String phone, Role role, boolean active) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.active = active;
    }

    /**
     * Tạo tài khoản khách hàng mới với quyền tối thiểu.
     *
     * @param email        email đã được chuẩn hóa (trim + lowercase)
     * @param passwordHash mật khẩu đã băm bằng BCrypt
     * @param fullName     họ tên đã trim
     * @param phone        số điện thoại đã chuẩn hóa, có thể {@code null}
     */
    public static User createCustomer(String email, String passwordHash, String fullName, String phone) {
        return new User(email, passwordHash, fullName, phone, Role.ROLE_CUSTOMER, true);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Không bao gồm {@code passwordHash} để tránh rò rỉ qua log. */
    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', role=" + role + ", active=" + active + '}';
    }
}
