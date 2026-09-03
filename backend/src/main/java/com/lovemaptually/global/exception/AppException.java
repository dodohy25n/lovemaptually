package com.lovemaptually.global.exception;

import com.lovemaptually.global.common.response.ApiErrorResponse.ErrorDetail;
import java.util.List;
import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ErrorDetail> details;

    public AppException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public AppException(HttpStatus status, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
