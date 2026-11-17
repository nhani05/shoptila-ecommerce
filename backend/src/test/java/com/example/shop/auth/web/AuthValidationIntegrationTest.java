package com.example.shop.auth.web;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class AuthValidationIntegrationTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Payload sai nhiều trường trả 400 với đầy đủ lỗi từng trường, không tạo tài khoản")
    void returnsAllFieldErrorsInSingleResponse() throws Exception {
        String payload = """
                {
                  "email": "khong-hop-le",
                  "password": "yeu",
                  "fullName": "A",
                  "phone": "0987 654 321"
                }
                """;

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://api.shoptila.com/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(REGISTER_PATH))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItems("email", "password", "fullName", "phone")))
                .andExpect(jsonPath("$.errors[*].message").isNotEmpty())
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("com.example.shop"))))
                .andExpect(content().string(not(containsString("at jakarta."))));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Các trường bắt buộc bị bỏ trống được liệt kê cùng lúc")
    void listsEveryMissingRequiredField() throws Exception {
        String payload = """
                {
                  "email": "",
                  "password": "",
                  "fullName": "",
                  "phone": ""
                }
                """;

        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItems("email", "password", "fullName")));

        assertThat(userRepository.count()).isZero();
    }
}
