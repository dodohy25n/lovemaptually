package com.lovemaptually.global.common.response;

import java.util.List;

public record ApiErrorResponse(int status, String message, ErrorBody error) {

    public static ApiErrorResponse of(int status, String message, String code) {
        return of(status, message, code, List.of());
    }

    public static ApiErrorResponse of(
            int status,
            String message,
            String code,
            List<ErrorDetail> details
    ) {
        return new ApiErrorResponse(status, message, new ErrorBody(code, List.copyOf(details)));
    }

    public record ErrorBody(String code, List<ErrorDetail> details) {
    }

    public record ErrorDetail(String field, String reason) {
    }
}
