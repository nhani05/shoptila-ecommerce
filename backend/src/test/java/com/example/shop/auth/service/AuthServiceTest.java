package com.example.shop.auth.service;

import com.example.shop.auth.domain.Role;
import com.example.shop.auth.domain.User;
import com.example.shop.auth.repository.UserRepository;
import com.example.shop.auth.web.dto.RegisterRequest;
import com.example.shop.auth.web.dto.RegisterResponse;
import com.example.shop.common.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "MatKhau123";
    private static final String PASSWORD_HASH = "$2a$12$abcdefghijklmnopqrstuv";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Đăng ký thành công: chuẩn hóa email, băm mật khẩu, gán ROLE_CUSTOMER/ACTIVE")
    void registersCustomerWithNormalizedEmailAndHashedPassword() {
        RegisterRequest request = new RegisterRequest(
                "  KhachHang@Example.COM ", RAW_PASSWORD, "  Nguyễn Văn A  ", "0987654321");
        when(userRepository.existsByEmail("khachhang@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", 42L);
            return toSave;
        });

        RegisterResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("khachhang@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_CUSTOMER);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getPhone()).isEqualTo("0987654321");

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("khachhang@example.com");
        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.phone()).isEqualTo("0987654321");
        assertThat(response.role()).isEqualTo("ROLE_CUSTOMER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("RegisterResponse không có trường mật khẩu/hash")
    void responseExposesNoPasswordOrHash() {
        assertThat(RegisterResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password")
                        || component.getName().toLowerCase().contains("hash"));
    }

    @Test
    @DisplayName("Email đã tồn tại bị từ chối trước khi băm mật khẩu")
    void rejectsDuplicateEmailBeforeHashing() {
        RegisterRequest request = new RegisterRequest(
                "  KHACHHANG@Example.com ", RAW_PASSWORD, "Nguyễn Văn A", null);
        when(userRepository.existsByEmail("khachhang@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("Vi phạm UNIQUE khi đua đồng thời được chuyển thành EmailAlreadyExistsException")
    void mapsUniqueConstraintViolationToDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "race@example.com", RAW_PASSWORD, "Nguyễn Văn A", null);
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_auth_users_email\""));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
