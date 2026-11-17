package com.example.shop.auth.service;

import com.example.shop.auth.domain.User;
import com.example.shop.auth.repository.UserRepository;
import com.example.shop.auth.web.dto.RegisterRequest;
import com.example.shop.auth.web.dto.RegisterResponse;
import com.example.shop.common.exception.EmailAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Đăng ký tài khoản khách hàng.
 *
 * <p>Luồng: DTO đã chuẩn hóa → kiểm tra trùng sớm → băm BCrypt → lưu → ánh xạ DTO.
 * Tranh chấp đồng thời được CSDL phân xử bằng UNIQUE; ngoại lệ vi phạm ràng buộc được
 * chuyển thành {@link EmailAlreadyExistsException} để trả {@code 409} thay vì {@code 500}.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uq_auth_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email();

        if (userRepository.existsByEmail(email)) {
            log.warn("event=auth.registration.failed reason=EMAIL_ALREADY_EXISTS");
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.createCustomer(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.phone()
        );
        user.setCreatedAt(Instant.now());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                log.warn("event=auth.registration.failed reason=EMAIL_ALREADY_EXISTS");
                throw new EmailAlreadyExistsException(email, ex);
            }
            throw ex;
        }

        log.info("event=auth.registration.succeeded userId={}", user.getId());
        return toResponse(user);
    }

    private static RegisterResponse toResponse(User user) {
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().name(),
                user.isActive() ? "ACTIVE" : "INACTIVE",
                user.getCreatedAt()
        );
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(UNIQUE_EMAIL_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
