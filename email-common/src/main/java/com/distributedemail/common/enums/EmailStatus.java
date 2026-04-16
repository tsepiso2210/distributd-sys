package com.distributedemail.common.enums;

/**
 * EmailStatus - tracks the lifecycle of a single email task.
 *
 * Assignment requirement: "Email status tracking: sent, delivered, bounced, failed"
 *
 * State machine:
 *   PENDING -> QUEUED -> SENDING -> SENT -> DELIVERED
 *                                       -> BOUNCED
 *                    -> FAILED -> RETRYING -> (back to QUEUED)
 *                                          -> DEAD_LETTERED (max retries exceeded)
 */
public enum EmailStatus {

    /** Task created but not yet published to Kafka */
    PENDING,

    /** Published to a Kafka topic, waiting for a worker to pick it up */
    QUEUED,

    /** Worker is actively attempting to send this email */
    SENDING,

    /** Provider accepted the email for delivery */
    SENT,

    /** Provider confirmed email was delivered to recipient mailbox */
    DELIVERED,

    /** Email was rejected by recipient mail server (permanent failure) */
    BOUNCED,

    /** Send attempt failed (network error, provider error, etc.) */
    FAILED,

    /** Currently waiting before the next retry attempt */
    RETRYING,

    /** Exceeded maximum retry count — moved to dead-letter topic */
    DEAD_LETTERED
}
