package com.distributedemail.common.enums;

/**
 * EmailPriority - determines which Kafka topic the email task is published to.
 *
 * Assignment requirement: "Priority messaging so high-priority emails are processed first"
 *
 * HIGH   -> published to "email.high-priority" Kafka topic
 *           Worker consumers on this topic have higher concurrency (more threads)
 *           to ensure these emails are processed before NORMAL ones.
 *
 * NORMAL -> published to "email.normal-priority" Kafka topic
 */
public enum EmailPriority {
    HIGH,
    NORMAL
}
