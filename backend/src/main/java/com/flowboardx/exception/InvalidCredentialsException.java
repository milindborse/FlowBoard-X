package com.flowboardx.exception;

/** Thrown on failed login (unknown email or wrong password). Maps to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}