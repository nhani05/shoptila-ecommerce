package com.example.shop.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Đo độ dài UTF-8 theo byte. Giá trị {@code null} được coi là hợp lệ — để cho
 * {@code @NotBlank} chịu trách nhiệm báo thiếu dữ liệu.
 */
public class Utf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {

    private int maxBytes;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        this.maxBytes = annotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
