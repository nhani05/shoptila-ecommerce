package com.example.shop.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trả mọi lỗi theo RFC 7807 {@link ProblemDetail}.
 *
 * <p>Nguyên tắc bảo mật: lỗi {@code 500} không bao giờ lộ thông điệp gốc, class name hay
 * stack trace ra client; chi tiết chỉ được ghi ở server log.</p>
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    static final URI EMAIL_ALREADY_EXISTS_TYPE =
            URI.create("https://api.shoptila.com/errors/email-already-exists");
    static final URI VALIDATION_FAILED_TYPE =
            URI.create("https://api.shoptila.com/errors/validation-failed");
    static final URI INTERNAL_ERROR_TYPE =
            URI.create("https://api.shoptila.com/errors/internal-error");

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uq_auth_users_email";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Một hoặc nhiều trường không hợp lệ.");
        problem.setType(VALIDATION_FAILED_TYPE);
        problem.setTitle("Dữ liệu không hợp lệ");
        problem.setProperty("errors", toFieldErrors(ex.getBindingResult().getAllErrors()));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không hợp lệ hoặc không đọc được.");
        problem.setType(VALIDATION_FAILED_TYPE);
        problem.setTitle("Dữ liệu không hợp lệ");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(EMAIL_ALREADY_EXISTS_TYPE);
        problem.setTitle("Email đã tồn tại");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        if (isEmailUniqueViolation(ex)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.");
            problem.setType(EMAIL_ALREADY_EXISTS_TYPE);
            problem.setTitle("Email đã tồn tại");
            problem.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        log.error("event=request.failed reason=DATA_INTEGRITY_VIOLATION", ex);
        return internalError(request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {

        log.error("event=request.failed reason=UNEXPECTED_ERROR", ex);
        return internalError(request);
    }

    private ResponseEntity<ProblemDetail> internalError(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi, vui lòng thử lại sau.");
        problem.setType(INTERNAL_ERROR_TYPE);
        problem.setTitle("Lỗi hệ thống");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static List<Map<String, String>> toFieldErrors(List<ObjectError> errors) {
        List<Map<String, String>> result = new ArrayList<>(errors.size());
        for (ObjectError error : errors) {
            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();
            String message = error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Dữ liệu không hợp lệ";
            Map<String, String> item = new LinkedHashMap<>(2);
            item.put("field", field);
            item.put("message", message);
            result.add(item);
        }
        return result;
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(UNIQUE_EMAIL_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
