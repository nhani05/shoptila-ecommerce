package com.example.shop.common.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.List;

/**
 * Mô tả tài liệu cho lỗi RFC 7807 mà API thực tế trả về.
 *
 * <p>Đây là DTO chỉ dùng cho OpenAPI/Swagger UI, không được serialize ở runtime. Nhờ đó Swagger
 * hiển thị đúng các trường {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance} và mảng {@code errors[]} của lỗi kiểm tra dữ liệu — thay vì schema
 * {@code ProblemDetail} chung của Spring không có {@code errors}.</p>
 */
@Schema(
        name = "ApiProblemDetail",
        description = "Lỗi theo RFC 7807. Lỗi kiểm tra dữ liệu (400) có thêm mảng `errors`."
)
public record ApiProblemDetail(

        @Schema(
                description = "URI định danh loại lỗi",
                example = "https://api.shoptila.com/errors/email-already-exists"
        )
        URI type,

        @Schema(description = "Tiêu đề ngắn của lỗi", example = "Email đã tồn tại")
        String title,

        @Schema(description = "Mã HTTP", example = "409")
        int status,

        @Schema(
                description = "Mô tả chi tiết cho người dùng",
                example = "Email đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác."
        )
        String detail,

        @Schema(description = "Đường dẫn request phát sinh lỗi", example = "/api/v1/auth/register")
        URI instance,

        @Schema(
                description = "Danh sách lỗi theo trường; chỉ xuất hiện với lỗi 400",
                nullable = true
        )
        List<FieldError> errors
) {

    @Schema(name = "ApiFieldError", description = "Một lỗi gắn với trường dữ liệu")
    public record FieldError(

            @Schema(description = "Tên trường vi phạm", example = "email")
            String field,

            @Schema(description = "Thông điệp lỗi", example = "Định dạng email không hợp lệ")
            String message
    ) {
    }
}
