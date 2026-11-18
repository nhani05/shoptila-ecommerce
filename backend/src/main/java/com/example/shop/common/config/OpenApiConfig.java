package com.example.shop.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadata cho OpenAPI/Swagger UI.
 *
 * <p>Springdoc tự quét controller và DTO; bean này chỉ bổ sung thông tin chung (tiêu đề,
 * phiên bản, mô tả, tag) để Swagger UI hiển thị đúng tài liệu Shoptila thay vì giá trị
 * mặc định "OpenAPI definition" / "v0".</p>
 *
 * <p>Swagger UI: {@code /swagger-ui.html} — OpenAPI JSON: {@code /v3/api-docs} — YAML:
 * {@code /v3/api-docs.yaml}.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shoptilaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shoptila API")
                        .version("1.0.0")
                        .description("""
                                Tài liệu REST API của Shoptila (modular monolith, Phase 1 MVP).
                                Mọi endpoint nằm dưới tiền tố `/api/v1`. Lỗi tuân theo RFC 7807
                                `ProblemDetail`; lỗi kiểm tra dữ liệu kèm mảng `errors[]`.

                                Dùng nút **Try it out** để gọi trực tiếp backend đang chạy. Với môi
                                trường dev, backend mặc định ở `http://localhost:8080`.
                                """))
                .tags(List.of(
                        new Tag()
                                .name("Auth")
                                .description("Đăng ký, đăng nhập và hồ sơ tài khoản")
                ));
    }
}
