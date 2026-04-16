package com.distributedemail.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RetryService - exponential backoff calculator for failed email deliveries.
 *
 * Assignment requirement: "Retry mechanism for failed email deliveries
 *   using exponential backoff"
 *
 * Exponential Backoff Formula:
 *   delay = min(baseDelay * 2^retryCount, maxDelay)
 *
 * Example with baseDelay=1000ms, maxDelay=32000ms:
 *   retryCount=0 -> delay = 1000ms (1 second)
 *   retryCount=1 -> delay = 2000ms (2 seconds)
 *   retryCount=2 -> delay = 4000ms (4 seconds)
 *   retryCount=3 -> delay = 8000ms (8 seconds)
 *   retryCount=4 -> delay = 16000ms (16 seconds)
 *   retryCount=5 -> delay = 32000ms (32 seconds, capped)
 *
 * Distributed Systems Design Note:
 *   Exponential backoff prevents thundering herd problems where many
 *   retrying clients overwhelm a recovering service simultaneously.
 *   Adding jitter (random delay) further spreads out retry attempts.
 *   This implementation adds 10% random jitter to prevent synchronization.
 */
@Slf4j
@Service
public class RetryService {

    @Value("${email.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${email.retry.base-delay-ms:1000}")
    private long baseDelayMs;

    @Value("${email.retry.max-delay-ms:32000}")
    private long maxDelayMs;

    /**
     * Determines if a task should be retried.
     *
     * @param currentRetryCount The number of retries already attempted
     * @return true if another retry should be attempted
     */
    public boolean shouldRetry(int currentRetryCount) {
        return currentRetryCount < maxAttempts;
    }

    /**
     * Calculates the wait time before the next retry attempt.
     * Includes 10% jitter to prevent synchronized retries across workers.
     *
     * @param retryCount The retry attempt number (0-indexed)
     * @return Delay in milliseconds before next attempt
     */
    public long calculateBackoffDelay(int retryCount) {
        // Exponential backoff: baseDelay * 2^retryCount
        long exponentialDelay = baseDelayMs * (long) Math.pow(2, retryCount);

        // Cap the delay at maxDelayMs
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);

        // Add ±10% jitter to spread retry load
        double jitter = 1.0 + (Math.random() * 0.2 - 0.1); // 0.9 to 1.1
        long jitteredDelay = (long) (cappedDelay * jitter);

        log.debug("Retry {} backoff: {}ms (base: {}ms, cap: {}ms)",
            retryCount, jitteredDelay, exponentialDelay, maxDelayMs);

        return jitteredDelay;
    }

    /**
     * Applies the backoff delay by sleeping the current thread.
     * This is called in the retry consumer before re-attempting delivery.
     *
     * @param retryCount Current retry number
     */
    public void waitForBackoff(int retryCount) {
        long delay = calculateBackoffDelay(retryCount);
        log.info("Applying exponential backoff: waiting {}ms before retry {} of {}",
            delay, retryCount + 1, maxAttempts);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff sleep interrupted for retry {}", retryCount);
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
