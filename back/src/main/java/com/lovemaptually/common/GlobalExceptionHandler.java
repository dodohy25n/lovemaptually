package com.lovemaptually.common;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record Detail(String field, String reason) {}
    public record ErrorBody(String code, List<Detail> details) {}
    public record ErrorResponse(int status, String message, ErrorBody error) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return error(e.status(), e.code(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        List<Detail> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new Detail(f.getField(), message(f))).toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요", details);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다", List.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorResponse> duplicate(DuplicateKeyException e) {
        return error(HttpStatus.CONFLICT, "RESOURCE_DUPLICATED", "이미 존재하는 데이터입니다", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "현재 요청을 처리할 수 없습니다", List.of());
    }

    private String message(FieldError e) { return Optional.ofNullable(e.getDefaultMessage()).orElse("잘못된 값입니다"); }
    private ResponseEntity<ErrorResponse> error(HttpStatus s, String c, String m, List<Detail> d) {
        return ResponseEntity.status(s).body(new ErrorResponse(s.value(), m, new ErrorBody(c, d)));
    }
}
