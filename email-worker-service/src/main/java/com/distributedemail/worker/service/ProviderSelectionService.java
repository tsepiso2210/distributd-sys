package com.distributedemail.worker.service;

import com.distributedemail.common.enums.ProviderName;
import com.distributedemail.common.provider.EmailProvider;
import com.distributedemail.worker.provider.MailgunProvider;
import com.distributedemail.worker.provider.SendGridProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ProviderSelectionService - implements automatic provider failover.
 *
 * Assignment requirement: "If provider 1 fails, automatically try provider 2"
 *                          "Automatic failover to another email provider if one fails"
 *
 * Failover logic:
 *   1. First attempt: try MAILGUN (primary provider)
 *   2. If Mailgun fails: try SENDGRID (secondary/failover provider)
 *   3. If both fail: return failure result for retry handling
 *
 * Distributed Systems Design Note:
 *   This implements the "Circuit Breaker" pattern at a simple level.
 *   For production, consider using Resilience4j's @CircuitBreaker annotation
 *   to automatically open the circuit after N failures and periodically
 *   probe if the primary provider has recovered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderSelectionService {

    private final MailgunProvider mailgunProvider;
    private final SendGridProvider sendGridProvider;

    /**
     * Send an email using the appropriate provider, with automatic failover.
     *
     * @param lastAttemptedProvider The provider that was last tried (NONE for first attempt)
     * @param from                  Sender email
     * @param fromName              Sender name
     * @param to                    Recipient email
     * @param toName                Recipient name
     * @param subject               Email subject
     * @param htmlBody              HTML body
     * @param textBody              Plain text body
     * @return The result of the send attempt
     */
    public EmailProvider.SendResult sendWithFailover(
            ProviderName lastAttemptedProvider,
            String from, String fromName,
            String to, String toName,
            String subject, String htmlBody, String textBody) {

        // Determine which providers to try and in what order
        if (lastAttemptedProvider == ProviderName.NONE) {
            // First attempt: try Mailgun first
            log.debug("First attempt for {}: trying Mailgun", to);
            EmailProvider.SendResult result = mailgunProvider.send(
                from, fromName, to, toName, subject, htmlBody, textBody);

            if (result.success()) {
                return result;
            }

            // Mailgun failed -> try SendGrid (FAILOVER)
            log.warn("Mailgun failed for {}. Error: {}. Failing over to SendGrid...",
                to, result.errorMessage());

            return sendGridProvider.send(from, fromName, to, toName, subject, htmlBody, textBody);

        } else if (lastAttemptedProvider == ProviderName.MAILGUN) {
            // Mailgun already failed on last attempt, skip it and try SendGrid
            log.info("Retrying {} with SendGrid (Mailgun failed previously)", to);
            return sendGridProvider.send(from, fromName, to, toName, subject, htmlBody, textBody);

        } else if (lastAttemptedProvider == ProviderName.SENDGRID) {
            // SendGrid also failed, try Mailgun in case it recovered
            log.info("Retrying {} with Mailgun (SendGrid failed previously)", to);
            return mailgunProvider.send(from, fromName, to, toName, subject, htmlBody, textBody);
        }

        // Both providers failed
        return new EmailProvider.SendResult(false, null,
            "All providers failed", 0, ProviderName.NONE);
    }

    /**
     * Get the next provider to try after a failure.
     * Used when building the retry Kafka message.
     */
    public ProviderName getNextProvider(ProviderName lastFailed) {
        if (lastFailed == ProviderName.NONE || lastFailed == ProviderName.SENDGRID) {
            return ProviderName.MAILGUN;
        }
        return ProviderName.SENDGRID;
    }
}
