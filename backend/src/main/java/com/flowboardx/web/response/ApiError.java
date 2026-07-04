package com.flowboardx.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope. Never exposes stack traces or internal exception details.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final boolean success;
    private final String message;
    private final int status;
    private final String error;
    private final String path;
    @Builder.Default
    private final Instant timestamp = Instant.now();
    /** Field-level validation messages, e.g. "name: must not be blank". Null when not applicable. */
    private final List<String> details;

    public static ApiError of(int status, String error, String message, String path) {
        return ApiError.builder()
                .success(false)
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();
    }

    public static ApiError of(int status, String error, String message, String path, List<String> details) {
        return ApiError.builder()
                .success(false)
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}