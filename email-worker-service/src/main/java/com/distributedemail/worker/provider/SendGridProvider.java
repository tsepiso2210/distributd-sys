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
 * SendGridProvider - secondary/failover email provider implementation.
 *
 * Assignment requirement: "Automatic failover to another email provider if one fails"
 *
 * This is the FAILOVER provider. When MailgunProvider fails, the
 * ProviderSelectionService will try this provider instead.
 *
 * SendGrid API documentation: https://docs.sendgrid.com/api-reference/mail-send/mail-send
 *
 * NOTE: Set SENDGRID_API_KEY as an environment variable.
 *       Do not hardcode real credentials here.
 */
@Slf4j
@Component
public class SendGridProvider implements EmailProvider {

    @Value("${email.provider.sendgrid.api-key}")
    private String apiKey;

    @Value("${email.provider.sendgrid.base-url:https://api.sendgrid.com/v3}")
    private String baseUrl;

    private final OkHttpClient httpClient;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    public SendGridProvider() {
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

        log.debug("SendGrid: sending to {}", to);

        // Build SendGrid v3 Mail Send JSON payload
        String jsonBody = buildSendGridPayload(from, fromName, to, toName, subject, htmlBody, textBody);

        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
            .url(baseUrl + "/mail/send")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            // SendGrid returns 202 Accepted on success
            if (response.code() == 202 || response.isSuccessful()) {
                // SendGrid returns the message ID in the X-Message-Id header
                String messageId = response.header("X-Message-Id", "sendgrid-sent");
                log.info("SendGrid: email sent to {} (messageId: {})", to, messageId);
                return new SendResult(true, messageId, null, response.code(), ProviderName.SENDGRID);
            } else {
                log.warn("SendGrid: failed to send to {} - HTTP {} - {}",
                    to, response.code(), responseBody);
                return new SendResult(false, null,
                    "SendGrid HTTP " + response.code() + ": " + responseBody,
                    response.code(), ProviderName.SENDGRID);
            }

        } catch (IOException e) {
            log.error("SendGrid: network error sending to {}: {}", to, e.getMessage());
            return new SendResult(false, null,
                "SendGrid network error: " + e.getMessage(),
                0, ProviderName.SENDGRID);
        }
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.SENDGRID;
    }

    @Override
    public boolean isHealthy() {
        // Check SendGrid API availability
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/user/profile")
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.code() != 401 && response.code() != 403;
            }
        } catch (IOException e) {
            log.warn("SendGrid health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Build the SendGrid v3 JSON payload.
     * SendGrid uses a structured JSON body unlike Mailgun's form-encoded body.
     */
    private String buildSendGridPayload(String from, String fromName,
                                         String to, String toName,
                                         String subject, String htmlBody, String textBody) {
        String fromJson = fromName != null
            ? "{\"email\":\"" + from + "\",\"name\":\"" + escapeJson(fromName) + "\"}"
            : "{\"email\":\"" + from + "\"}";

        String toJson = toName != null
            ? "{\"email\":\"" + to + "\",\"name\":\"" + escapeJson(toName) + "\"}"
            : "{\"email\":\"" + to + "\"}";

        String html = htmlBody != null ? escapeJson(htmlBody) : escapeJson(textBody != null ? textBody : "");
        String text = textBody != null ? escapeJson(textBody) : stripHtml(htmlBody);

        return """
            {
              "personalizations": [
                {
                  "to": [%s]
                }
              ],
              "from": %s,
              "subject": "%s",
              "content": [
                {"type": "text/plain", "value": "%s"},
                {"type": "text/html", "value": "%s"}
              ]
            }
            """.formatted(toJson, fromJson, escapeJson(subject), text, html);
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
