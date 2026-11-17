package com.example.shop.auth.web;

import com.example.shop.auth.domain.User;
import com.example.shop.auth.repository.UserRepository;
import com.example.shop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class AuthRegisterIntegrationTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Payload hợp lệ trả 201 với tài khoản CUSTOMER/ACTIVE và không lộ hash")
    void registersNewCustomer() throws Exception {
        String payload = """
                {
                  "email": "  KhachHang@Example.COM ",
                  "password": "MatKhau123",
                  "fullName": "  Nguyễn Văn A  ",
                  "phone": "0987654321"
                }
                """;

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("khachhang@example.com"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(content().string(not(containsString("MatKhau123"))));

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        User saved = users.get(0);
        assertThat(saved.getEmail()).isEqualTo("khachhang@example.com");
        assertThat(saved.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("MatKhau123", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Số điện thoại là tùy chọn: bỏ trống vẫn tạo tài khoản")
    void registersWithoutPhone() throws Exception {
        String payload = """
                {
                  "email": "khong-phone@example.com",
                  "password": "MatKhau123",
                  "fullName": "Trần Thị B"
                }
                """;

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Thuộc tính role/isActive do client gửi bị bỏ qua")
    void ignoresClientSuppliedRoleAndStatus() throws Exception {
        String payload = """
                {
                  "email": "thu-role@example.com",
                  "password": "MatKhau123",
                  "fullName": "Nguyễn Văn A",
                  "role": "ROLE_ADMIN",
                  "isActive": false,
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(userRepository.findAll()).singleElement()
                .satisfies(user -> {
                    assertThat(user.getRole().name()).isEqualTo("ROLE_CUSTOMER");
                    assertThat(user.isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("Email trùng chính xác trả 409 ProblemDetail và không tạo thêm dòng")
    void rejectsExactDuplicateEmail() throws Exception {
        register("trung@example.com");
        long countAfterFirst = userRepository.count();

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "trung@example.com",
                                  "password": "MatKhau123",
                                  "fullName": "Nguyễn Văn A"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://api.shoptila.com/errors/email-already-exists"))
                .andExpect(jsonPath("$.title").value("Email đã tồn tại"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value(REGISTER_PATH));

        assertThat(userRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("Email trùng khác hoa/thường và có khoảng trắng vẫn bị từ chối")
    void rejectsDuplicateEmailWithDifferentCaseAndWhitespace() throws Exception {
        register("khachhang@example.com");
        long countAfterFirst = userRepository.count();

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "  KHACHHANG@Example.COM ",
                                  "password": "MatKhau123",
                                  "fullName": "Trần Thị B"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://api.shoptila.com/errors/email-already-exists"))
                .andExpect(jsonPath("$.instance").value(REGISTER_PATH));

        assertThat(userRepository.count()).isEqualTo(countAfterFirst);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "MatKhau123",
                                  "fullName": "Nguyễn Văn A"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }
}
