package com.distributedemail.common.provider;

import com.distributedemail.common.enums.ProviderName;

/**
 * EmailProvider - the interface that all email providers must implement.
 *
 * Assignment requirement: "Use a clean EmailProvider interface with at least
 *   MailgunProvider and SendGridProvider"
 *
 * This interface abstracts the underlying HTTP API calls to the email
 * provider. The worker service uses this interface so it does not need
 * to know which provider is being used. The ProviderSelectionService
 * chooses the correct implementation at runtime.
 */
public interface EmailProvider {

    /**
     * Send a single email via this provider.
     *
     * @param from          Sender email address
     * @param fromName      Sender display name
     * @param to            Recipient email address
     * @param toName        Recipient display name (may be null)
     * @param subject       Email subject line
     * @param htmlBody      HTML email body (supports HTML tags)
     * @param textBody      Plain-text fallback body
     * @return SendResult   Result object indicating success/failure and provider ID
     */
    SendResult send(String from, String fromName,
                    String to, String toName,
                    String subject, String htmlBody, String textBody);

    /**
     * Returns the name of this provider for logging and tracking purposes.
     */
    ProviderName getProviderName();

    /**
     * Health check - returns true if the provider is reachable and functional.
     * Used by the ProviderSelectionService to determine which provider to use.
     */
    boolean isHealthy();

    /**
     * Result of a send attempt.
     */
    record SendResult(
        boolean success,
        String messageId,
        String errorMessage,
        int statusCode,
        ProviderName provider
    ) {}
}
