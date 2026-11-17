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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class PasswordByteLimitTest {

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
    @DisplayName("Mật khẩu 73 byte ASCII bị từ chối với 400 ở trường password")
    void rejectsAsciiPasswordOver72Bytes() throws Exception {
        postWithPassword("dai-73@example.com", asciiPasswordOfLength(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Mật khẩu đúng 72 byte ASCII hợp lệ, trả 201")
    void acceptsAsciiPasswordOfExactly72Bytes() throws Exception {
        postWithPassword("dung-72@example.com", asciiPasswordOfLength(72))
                .andExpect(status().isCreated());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mật khẩu emoji vượt 72 byte trả 400, không phải 500 hay IllegalArgumentException")
    void rejectsMultibytePasswordOver72BytesWithoutServerError() throws Exception {
        String emojiPassword = "Aa1" + "😀".repeat(40);

        postWithPassword("dai-emoji@example.com", emojiPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(content().string(not(containsString("IllegalArgumentException"))))
                .andExpect(content().string(not(containsString("org.springframework"))));

        assertThat(userRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions postWithPassword(
            String email, String password) throws Exception {
        String payload = """
                {
                  "email": "%s",
                  "password": "%s",
                  "fullName": "Nguyễn Văn A"
                }
                """.formatted(email, password);

        return mockMvc.perform(post(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    /** Mật khẩu ASCII đủ chữ hoa, chữ thường, chữ số với độ dài byte mong muốn. */
    private static String asciiPasswordOfLength(int length) {
        StringBuilder builder = new StringBuilder("Aa1");
        while (builder.length() < length) {
            builder.append('b');
        }
        return builder.substring(0, length);
    }
}
