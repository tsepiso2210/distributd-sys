package com.distributedemail.client.controller;

import com.distributedemail.client.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * FailedEmailsController - shows failed and dead-lettered email tasks.
 *
 * Assignment requirement: "Failed Emails / Retry View" GUI view
 *
 * Features:
 *   - Table of all failed/dead-lettered tasks
 *   - Select and manually retry individual tasks
 *   - View the error message for each failure
 *   - Refresh to reload
 */
public class FailedEmailsController implements Initializable {

    @FXML private TableView<FailedTaskRow> failedTable;
    @FXML private TableColumn<FailedTaskRow, String> colId;
    @FXML private TableColumn<FailedTaskRow, String> colEmail;
    @FXML private TableColumn<FailedTaskRow, String> colCampaignId;
    @FXML private TableColumn<FailedTaskRow, String> colStatus;
    @FXML private TableColumn<FailedTaskRow, String> colProvider;
    @FXML private TableColumn<FailedTaskRow, String> colRetries;
    @FXML private TableColumn<FailedTaskRow, String> colError;
    @FXML private TextArea txtErrorDetail;
    @FXML private Label statusLabel;
    @FXML private Button btnRetry;

    private final ApiService apiService = ApiService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("recipientEmail"));
        colCampaignId.setCellValueFactory(new PropertyValueFactory<>("campaignId"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProvider.setCellValueFactory(new PropertyValueFactory<>("providerUsed"));
        colRetries.setCellValueFactory(new PropertyValueFactory<>("retryCount"));
        colError.setCellValueFactory(new PropertyValueFactory<>("lastError"));

        failedTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, newSel) -> {
                if (newSel != null) {
                    txtErrorDetail.setText(
                        "Task ID: " + newSel.getId() + "\n" +
                        "Recipient: " + newSel.getRecipientEmail() + "\n" +
                        "Status: " + newSel.getStatus() + "\n" +
                        "Provider: " + newSel.getProviderUsed() + "\n" +
                        "Retry Count: " + newSel.getRetryCount() + "\n" +
                        "Last Error:\n" + newSel.getLastError()
                    );
                }
            });

        loadFailedTasks();
    }

    @FXML
    public void onRefresh() {
        loadFailedTasks();
    }

    /**
     * Manual retry: re-publishes a dead-lettered task back to the retry topic.
     * Calls POST /api/campaigns/retry-task/{taskId}
     */
    @FXML
    public void onRetrySelected() {
        FailedTaskRow selected = failedTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a task to retry");
            return;
        }

        btnRetry.setDisable(true);
        new Thread(() -> {
            try {
                apiService.post("/api/campaigns/retry-task/" + selected.getId(), null);
                Platform.runLater(() -> {
                    statusLabel.setText("Task " + selected.getId() + " queued for retry");
                    btnRetry.setDisable(false);
                    loadFailedTasks();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Retry failed: " + e.getMessage());
                    btnRetry.setDisable(false);
                });
            }
        }).start();
    }

    private void loadFailedTasks() {
        new Thread(() -> {
            try {
                JsonNode tasks = apiService.get("/api/campaigns/failed-tasks");
                List<FailedTaskRow> rows = new ArrayList<>();

                if (tasks.isArray()) {
                    for (JsonNode t : tasks) {
                        JsonNode campaign = t.path("campaign");
                        rows.add(new FailedTaskRow(
                            t.path("id").asText(),
                            t.path("recipientEmail").asText(),
                            campaign.path("id").asText(""),
                            t.path("status").asText(),
                            t.path("providerUsed").asText(),
                            t.path("retryCount").asText("0"),
                            t.path("lastError").asText("")
                        ));
                    }
                }

                Platform.runLater(() -> {
                    failedTable.setItems(FXCollections.observableArrayList(rows));
                    statusLabel.setText("Found " + rows.size() + " failed/dead-lettered tasks");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error loading: " + e.getMessage()));
            }
        }).start();
    }

    public static class FailedTaskRow {
        private final String id, recipientEmail, campaignId, status, providerUsed, retryCount, lastError;
        public FailedTaskRow(String id, String recipientEmail, String campaignId,
                              String status, String providerUsed, String retryCount, String lastError) {
            this.id = id; this.recipientEmail = recipientEmail; this.campaignId = campaignId;
            this.status = status; this.providerUsed = providerUsed;
            this.retryCount = retryCount; this.lastError = lastError;
        }
        public String getId() { return id; }
        public String getRecipientEmail() { return recipientEmail; }
        public String getCampaignId() { return campaignId; }
        public String getStatus() { return status; }
        public String getProviderUsed() { return providerUsed; }
        public String getRetryCount() { return retryCount; }
        public String getLastError() { return lastError; }
    }
}
