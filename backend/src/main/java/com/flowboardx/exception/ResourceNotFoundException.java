package com.flowboardx.exception;

/** Thrown when a requested entity (workflow, run, version, node...) does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}