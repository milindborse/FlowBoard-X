package com.flowboardx.exception;

/**
 * Thrown when an authenticated user tries to access or modify a resource they do not own
 * (e.g. another user's workflow). Maps to HTTP 403. Named "Forbidden" rather than
 * "AccessDenied" to avoid clashing with Spring Security's AccessDeniedException.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}