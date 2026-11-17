package com.example.shop.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc độ dài tối đa theo số <strong>byte UTF-8</strong> của chuỗi.
 *
 * <p>Cần thiết cho mật khẩu: BCrypt chỉ xử lý tối đa 72 byte và ném
 * {@link IllegalArgumentException} (CVE-2025-22228) khi vượt quá. Giới hạn theo ký tự
 * không đủ vì tiếng Việt và emoji dùng nhiều byte cho một ký tự.</p>
 */
@Documented
@Constraint(validatedBy = Utf8BytesValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value() default 72;

    String message() default "Mật khẩu không được vượt quá 72 byte";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
