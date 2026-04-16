package com.distributedemail.worker.service;

import com.distributedemail.common.enums.ProviderName;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiterService - token bucket rate limiter per email provider.
 *
 * Assignment requirement: "Rate limiting and throttling to prevent spam
 *   and comply with provider limits"
 *
 * Uses the Bucket4j library which implements the Token Bucket algorithm:
 *   - Each provider has a "bucket" with a fixed number of tokens
 *   - Each send consumes one token
 *   - Tokens are refilled at a constant rate (e.g., 5 tokens per second)
 *   - If the bucket is empty, the thread waits until a token is available
 *
 * This ensures:
 *   - Mailgun: max N emails per second (configurable)
 *   - SendGrid: max M emails per second (configurable)
 *
 * Distributed Systems Design Note:
 *   This is a per-instance rate limiter. In a truly distributed setup with
 *   multiple worker instances, you would use a shared rate limiter backed by
 *   Redis (Bucket4j supports this) to coordinate limits across all instances.
 */
@Slf4j
@Service
public class RateLimiterService {

    @Value("${email.provider.mailgun.rate-limit-per-second:5}")
    private int mailgunRateLimit;

    @Value("${email.provider.sendgrid.rate-limit-per-second:10}")
    private int sendgridRateLimit;

    // One bucket per provider
    private final Map<ProviderName, Bucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    public void initBuckets() {
        // Mailgun rate limiter bucket
        buckets.put(ProviderName.MAILGUN, createBucket(mailgunRateLimit));
        // SendGrid rate limiter bucket
        buckets.put(ProviderName.SENDGRID, createBucket(sendgridRateLimit));

        log.info("Rate limiters initialized: Mailgun={}/sec, SendGrid={}/sec",
            mailgunRateLimit, sendgridRateLimit);
    }

    /**
     * Acquires a rate limit token for the specified provider.
     * Blocks if the rate limit is exceeded until a token becomes available.
     *
     * @param provider The email provider to throttle
     */
    public void acquireToken(ProviderName provider) {
        Bucket bucket = buckets.get(provider);
        if (bucket == null) {
            log.warn("No rate limiter found for provider {}, allowing request", provider);
            return;
        }

        try {
            // blocking wait - thread sleeps until a token is available
            bucket.asBlocking().consume(1);
            log.debug("Rate limit token acquired for provider {}", provider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate limiter wait interrupted for provider {}", provider);
        }
    }

    /**
     * Try to acquire a token without blocking.
     * Returns true if the token was acquired, false if rate limit is exceeded.
     */
    public boolean tryAcquireToken(ProviderName provider) {
        Bucket bucket = buckets.get(provider);
        if (bucket == null) return true;
        return bucket.tryConsume(1);
    }

    /**
     * Create a token bucket with the specified refill rate.
     * Tokens refill at rate of [ratePerSecond] tokens per second.
     */
    private Bucket createBucket(int ratePerSecond) {
        Refill refill = Refill.intervally(ratePerSecond, Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(ratePerSecond, refill);
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
