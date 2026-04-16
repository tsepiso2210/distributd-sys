package com.distributedemail.client.controller;

import com.distributedemail.client.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.*;

/**
 * ComposeBulkEmailController - GUI for submitting bulk email campaigns.
 *
 * Assignment requirement: "Compose Bulk Email" GUI view
 *
 * Features:
 *   - Campaign name, subject, priority
 *   - Choose template or write custom body
 *   - Add/remove recipients with per-recipient template variables
 *   - Submit to POST /api/campaigns
 *
 * Assignment requirement: "Client submits a bulk email request with:
 *   - recipients, subject, body or template ID, priority"
 */
public class ComposeBulkEmailController implements Initializable {

    @FXML private TextField txtCampaignName;
    @FXML private TextField txtSubject;
    @FXML private TextField txtSenderEmail;
    @FXML private TextField txtSenderName;
    @FXML private ComboBox<String> cbPriority;
    @FXML private ComboBox<String> cbTemplate;
    @FXML private TextArea txtBody;
    @FXML private TextArea txtRecipients;
    @FXML private Label statusLabel;
    @FXML private Button btnSend;
    @FXML private ProgressIndicator sendingIndicator;

    private final ApiService apiService = ApiService.getInstance();
    private final Map<String, Long> templateNameToId = new LinkedHashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbPriority.setItems(FXCollections.observableArrayList("HIGH", "NORMAL"));
        cbPriority.setValue("NORMAL");

        loadTemplates();

        // When template is selected, disable the manual body textarea
        cbTemplate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals("-- No Template (use body below) --")) {
                txtBody.setDisable(true);
                txtBody.setPromptText("Template selected - body will be rendered from template");
            } else {
                txtBody.setDisable(false);
                txtBody.setPromptText("Write email body here. Use {{name}}, {{studentNumber}}, {{course}} placeholders");
            }
        });
    }

    private void loadTemplates() {
        new Thread(() -> {
            try {
                JsonNode templates = apiService.get("/api/templates");
                List<String> templateNames = new ArrayList<>();
                templateNames.add("-- No Template (use body below) --");

                if (templates.isArray()) {
                    for (JsonNode t : templates) {
                        String name = t.path("name").asText();
                        Long id = t.path("id").asLong();
                        templateNames.add(name);
                        templateNameToId.put(name, id);
                    }
                }

                Platform.runLater(() -> {
                    cbTemplate.setItems(FXCollections.observableArrayList(templateNames));
                    cbTemplate.setValue(templateNames.get(0));
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                    statusLabel.setText("Could not load templates: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Send the campaign.
     *
     * Recipients textarea format (one per line):
     *   email@example.com | Full Name | studentNumber=ST001,course=CS401
     *
     * Example:
     *   alice@student.edu | Alice Smith | studentNumber=ST001,course=CS401 Distributed Systems
     *   bob@student.edu   | Bob Jones   | studentNumber=ST002,course=CS401 Distributed Systems
     */
    @FXML
    public void onSend() {
        if (!validateForm()) return;

        sendingIndicator.setVisible(true);
        btnSend.setDisable(true);
        statusLabel.setText("Sending campaign...");

        new Thread(() -> {
            try {
                // Build the request payload
                Map<String, Object> payload = buildPayload();

                JsonNode response = apiService.post("/api/campaigns", payload);

                Platform.runLater(() -> {
                    sendingIndicator.setVisible(false);
                    btnSend.setDisable(false);
                    String message = response.path("message").asText("Campaign created successfully");
                    statusLabel.setText(message);
                    clearForm();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    sendingIndicator.setVisible(false);
                    btnSend.setDisable(false);
                    statusLabel.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private Map<String, Object> buildPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignName", txtCampaignName.getText().trim());
        payload.put("subject", txtSubject.getText().trim());
        payload.put("senderEmail", txtSenderEmail.getText().trim());
        payload.put("senderName", txtSenderName.getText().trim());
        payload.put("priority", cbPriority.getValue());

        // Template or raw body
        String selectedTemplate = cbTemplate.getValue();
        if (selectedTemplate != null && templateNameToId.containsKey(selectedTemplate)) {
            payload.put("templateId", templateNameToId.get(selectedTemplate));
        } else {
            payload.put("rawBody", txtBody.getText().trim());
        }

        // Parse recipients
        List<Map<String, Object>> recipients = parseRecipients();
        payload.put("recipients", recipients);

        return payload;
    }

    private List<Map<String, Object>> parseRecipients() {
        List<Map<String, Object>> recipients = new ArrayList<>();
        String[] lines = txtRecipients.getText().trim().split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|");
            Map<String, Object> recipient = new HashMap<>();
            recipient.put("email", parts[0].trim());

            if (parts.length > 1) {
                recipient.put("name", parts[1].trim());
            }

            // Parse template variables: key=value,key2=value2
            Map<String, String> vars = new HashMap<>();
            if (parts.length > 2) {
                String[] varPairs = parts[2].trim().split(",");
                for (String pair : varPairs) {
                    String[] kv = pair.trim().split("=", 2);
                    if (kv.length == 2) {
                        vars.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
            if (!vars.isEmpty()) {
                recipient.put("templateVariables", vars);
            }

            recipients.add(recipient);
        }

        return recipients;
    }

    private boolean validateForm() {
        if (txtCampaignName.getText().trim().isEmpty()) {
            statusLabel.setText("Campaign name is required");
            return false;
        }
        if (txtSubject.getText().trim().isEmpty()) {
            statusLabel.setText("Subject is required");
            return false;
        }
        if (txtSenderEmail.getText().trim().isEmpty()) {
            statusLabel.setText("Sender email is required");
            return false;
        }
        if (txtRecipients.getText().trim().isEmpty()) {
            statusLabel.setText("At least one recipient is required");
            return false;
        }
        return true;
    }

    private void clearForm() {
        txtCampaignName.clear();
        txtSubject.clear();
        txtBody.clear();
        txtRecipients.clear();
        cbPriority.setValue("NORMAL");
        cbTemplate.setValue("-- No Template (use body below) --");
    }
}
