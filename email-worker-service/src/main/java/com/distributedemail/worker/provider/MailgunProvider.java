package com.distributedemail.worker.provider;

import com.distributedemail.common.enums.ProviderName;
import com.distributedemail.common.provider.EmailProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * MailgunProvider - primary email provider implementation.
 *
 * Assignment requirement: "Use a clean EmailProvider interface with at least
 *   MailgunProvider and SendGridProvider"
 *
 * This class makes HTTP calls to the Mailgun REST API to send emails.
 * When this provider fails, the ProviderSelectionService automatically
 * falls over to SendGridProvider.
 *
 * Mailgun API documentation: https://documentation.mailgun.com/docs/mailgun/api-reference/openapi-final/tag/Messages/
 *
 * NOTE: Set MAILGUN_API_KEY and MAILGUN_DOMAIN as environment variables.
 *       Do not hardcode real credentials here.
 */
@Slf4j
@Component
public class MailgunProvider implements EmailProvider {

    @Value("${email.provider.mailgun.api-key}")
    private String apiKey;

    @Value("${email.provider.mailgun.domain}")
    private String domain;

    @Value("${email.provider.mailgun.base-url:https://api.mailgun.net/v3}")
    private String baseUrl;

    private final OkHttpClient httpClient;

    public MailgunProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public SendResult send(String from, String fromName,
                           String to, String toName,
                           String subject, String htmlBody, String textBody) {

        log.debug("Mailgun: sending to {} via domain {}", to, domain);

        // Build the multipart form body for Mailgun API
        String fromHeader = (fromName != null && !fromName.isEmpty())
            ? fromName + " <" + from + ">"
            : from;

        String toHeader = (toName != null && !toName.isEmpty())
            ? toName + " <" + to + ">"
            : to;

        RequestBody body = new FormBody.Builder()
            .add("from", fromHeader)
            .add("to", toHeader)
            .add("subject", subject)
            .add("html", htmlBody != null ? htmlBody : textBody)
            .add("text", textBody != null ? textBody : stripHtml(htmlBody))
            .build();

        String url = baseUrl + "/" + domain + "/messages";

        // Use HTTP Basic Auth with API key as password (Mailgun standard)
        String credential = Credentials.basic("api", apiKey);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (response.isSuccessful()) {
                log.info("Mailgun: email sent to {} (status {})", to, response.code());
                // Mailgun returns a message ID in JSON: {"id": "<...@mailgun.org>", "message": "Queued. Thank you."}
                return new SendResult(true, extractMessageId(responseBody),
                    null, response.code(), ProviderName.MAILGUN);
            } else {
                log.warn("Mailgun: failed to send to {} - HTTP {} - {}",
                    to, response.code(), responseBody);
                return new SendResult(false, null,
                    "Mailgun HTTP " + response.code() + ": " + responseBody,
                    response.code(), ProviderName.MAILGUN);
            }

        } catch (IOException e) {
            log.error("Mailgun: network error sending to {}: {}", to, e.getMessage());
            return new SendResult(false, null,
                "Mailgun network error: " + e.getMessage(),
                0, ProviderName.MAILGUN);
        }
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.MAILGUN;
    }

    @Override
    public boolean isHealthy() {
        // Simple health check: verify the domain is accessible
        try {
            String url = baseUrl + "/" + domain;
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic("api", apiKey))
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.code() != 401 && response.code() != 403;
            }
        } catch (IOException e) {
            log.warn("Mailgun health check failed: {}", e.getMessage());
            return false;
        }
    }

    private String extractMessageId(String responseBody) {
        // Simple extraction from Mailgun JSON response
        if (responseBody != null && responseBody.contains("\"id\"")) {
            int start = responseBody.indexOf("\"id\":\"") + 6;
            int end = responseBody.indexOf("\"", start);
            if (start > 5 && end > start) {
                return responseBody.substring(start, end);
            }
        }
        return "mailgun-sent";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
