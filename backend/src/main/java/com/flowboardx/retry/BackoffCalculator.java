package com.flowboardx.retry;

/**
 * Exponential backoff with a small jitter component to avoid thundering-herd
 * retries when many node executions fail at once (e.g. a downstream API outage).
 */
public final class BackoffCalculator {

    private BackoffCalculator() {}

    public static long delayForAttempt(RetryPolicy policy, int attemptNumber) {
        if (attemptNumber <= 1) return 0;
        long exponential = policy.baseBackoffMs() * (1L << Math.min(attemptNumber - 1, 16));
        long jitter = (long) (Math.random() * policy.baseBackoffMs());
        long capped = Math.min(exponential + jitter, 60_000L);
        return capped;
    }
}
