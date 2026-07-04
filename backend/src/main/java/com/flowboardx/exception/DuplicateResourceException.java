package com.flowboardx.exception;

/** Thrown on unique-constraint style conflicts (e.g. duplicate workflow name for a user). Maps to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}