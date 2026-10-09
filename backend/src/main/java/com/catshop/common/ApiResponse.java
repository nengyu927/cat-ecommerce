package com.catshop.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified API response envelope.
 * Success: { "success": true,  "data": {...}, "timestamp": "..." }
 * Error:   { "success": false, "error": { "code": "...", "message": "...", "details": [...] }, "timestamp": "..." }
 */
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;
    private ErrorBody error;
    private LocalDateTime timestamp;
    @lombok.Setter
    private int code;

    public ApiResponse(boolean success, T data, ErrorBody error, LocalDateTime timestamp) {
        this.success = success; this.data = data; this.error = error; this.timestamp = timestamp;
        this.code = success ? 200 : switch (error.getCode()) {
            case "UNAUTHORIZED", "BAD_CREDENTIALS" -> 401;
            case "FORBIDDEN" -> 403;
            case "NOT_FOUND" -> 404;
            case "CONFLICT", "CONCURRENT_UPDATE" -> 409;
            case "RATE_LIMITED" -> 429;
            case "INTERNAL_ERROR" -> 500;
            default -> 400;
        };
    }

    public String getMessage() { return success ? "OK" : error.getMessage(); }
    public List<FieldError> getErrors() { return error == null ? null : error.getDetails(); }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, null), LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldError> details) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, details), LocalDateTime.now());
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        private String code;
        private String message;
        private List<FieldError> details;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        public String getReason() { return message; }
    }
}
