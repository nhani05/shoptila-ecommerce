package com.example.shop.auth.web;

import com.example.shop.auth.service.AuthService;
import com.example.shop.auth.web.dto.RegisterRequest;
import com.example.shop.auth.web.dto.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng ký, đăng nhập và hồ sơ tài khoản")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
            operationId = "registerCustomer",
            summary = "Đăng ký tài khoản khách hàng mới",
            description = "Tạo tài khoản đang hoạt động với vai trò ROLE_CUSTOMER. "
                    + "Email được chuẩn hóa (trim + lowercase). Mật khẩu được băm BCrypt (strength 12) "
                    + "và không bao giờ được trả về."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Tài khoản được tạo thành công.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegisterResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Dữ liệu đầu vào không hợp lệ; mảng errors liệt kê mọi trường vi phạm.",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Email đã được sử dụng bởi một tài khoản khác.",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
