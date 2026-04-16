package com.distributedemail.common.enums;

/**
 * ProviderName - identifies which email provider was used or should be used.
 *
 * Assignment requirement: "Automatic failover to another email provider if one fails"
 *
 * Primary provider:   MAILGUN
 * Secondary provider: SENDGRID (used when Mailgun fails)
 * None:               No provider attempted yet, or both failed
 */
public enum ProviderName {
    MAILGUN,
    SENDGRID,
    NONE
}
