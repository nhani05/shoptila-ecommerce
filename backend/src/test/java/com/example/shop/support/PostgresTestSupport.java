package com.example.shop.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Hạ tầng Testcontainers dùng chung cho integration test.
 *
 * <p>Import bằng {@code @Import(PostgresTestSupport.class)}. Container được Spring Boot
 * khởi động và nối vào {@code DataSource} qua {@link ServiceConnection}; Flyway chạy trên
 * PostgreSQL thật (không phải H2).</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestSupport {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
