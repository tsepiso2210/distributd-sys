package com.distributedemail.common.kafka;

/**
 * KafkaTopics - central registry of all Kafka topic names.
 *
 * Assignment requirement: "Separate Kafka topics must exist for:
 *   - high priority
 *   - normal priority
 *   - retry
 *   - status
 *   - dead-letter"
 *
 * Having a single class for topic names prevents typos and makes
 * refactoring easier. Both producer (API service) and consumer
 * (worker service) import this class.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /**
     * High-priority email tasks.
     * Workers assigned to this topic run with higher concurrency
     * so high-priority emails are processed before normal ones.
     * Satisfies: "Priority emails must be processed ahead of normal ones"
     */
    public static final String HIGH_PRIORITY = "email.high-priority";

    /**
     * Normal-priority email tasks.
     * Workers processing this topic may run with fewer threads.
     */
    public static final String NORMAL_PRIORITY = "email.normal-priority";

    /**
     * Tasks that failed but still have retry attempts remaining.
     * Worker picks these up after computing the exponential backoff delay.
     * Satisfies: "Retry mechanism for failed email deliveries using exponential backoff"
     */
    public static final String RETRY = "email.retry";

    /**
     * Status update events published by the worker after each send attempt.
     * Could be consumed by a monitoring service or dashboard.
     * Satisfies: "Maintain email status history"
     */
    public static final String STATUS = "email.status";

    /**
     * Tasks that have exhausted all retry attempts.
     * These are stored for manual inspection / operator review.
     * Satisfies: "After max retries, move task to dead-letter topic"
     */
    public static final String DEAD_LETTER = "email.dead-letter";
}
