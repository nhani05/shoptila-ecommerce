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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Chứng minh tranh chấp email được phân xử nguyên tử bởi ràng buộc UNIQUE: hai yêu cầu
 * đồng thời cùng email → đúng một 201, một 409, một dòng dữ liệu, không 5xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class RegistrationConcurrencyTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final int THREADS = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Hai đăng ký đồng thời cùng email tạo đúng một tài khoản")
    void createsExactlyOneAccountUnderConcurrentRegistration() throws Exception {
        String payload = """
                {
                  "email": "race@example.com",
                  "password": "MatKhau123",
                  "fullName": "Nguyễn Văn A"
                }
                """;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Các luồng không sẵn sàng kịp thời");
                    }
                    MvcResult result = mockMvc.perform(post(REGISTER_PATH)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn();
                    return result.getResponse().getStatus();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(statuses).noneMatch(status -> status >= 500);
            assertThat(userRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
