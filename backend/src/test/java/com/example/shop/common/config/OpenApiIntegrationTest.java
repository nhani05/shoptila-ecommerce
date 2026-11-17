package com.example.shop.common.config;

import com.example.shop.support.PostgresTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bảo vệ cấu hình OpenAPI/Swagger: tài liệu phải công khai, có metadata Shoptila và mô tả đúng
 * endpoint đăng ký cùng schema lỗi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI JSON công khai, có metadata, operation đăng ký và schema lỗi")
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Shoptila API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.operationId")
                        .value("registerCustomer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiProblemDetail").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.email.example")
                        .value("khachhang@example.com"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.format")
                        .value("password"));
    }

    @Test
    @DisplayName("OpenAPI YAML công khai để tải về")
    void exposesOpenApiYaml() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger UI và swagger-config truy cập công khai")
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    @DisplayName("/swagger-ui.html chuyển hướng tới Swagger UI")
    void redirectsSwaggerUiShortcut() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }
}
