package com.flowboardx.retry;

public record RetryPolicy(int maxAttempts, long baseBackoffMs) {
    public static RetryPolicy none() {
        return new RetryPolicy(1, 0);
    }
}
