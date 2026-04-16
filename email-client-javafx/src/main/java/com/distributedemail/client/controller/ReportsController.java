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
 * ReportsController - displays campaign reports and email delivery logs.
 *
 * Assignment requirement: "Reports / Logs" GUI view
 *
 * Features:
 *   - List all campaigns with totals
 *   - Select a campaign to view its tasks (with status and provider)
 *   - Refresh button
 */
public class ReportsController implements Initializable {

    @FXML private TableView<CampaignReportRow> campaignTable;
    @FXML private TableColumn<CampaignReportRow, String> colId;
    @FXML private TableColumn<CampaignReportRow, String> colName;
    @FXML private TableColumn<CampaignReportRow, String> colTotal;
    @FXML private TableColumn<CampaignReportRow, String> colSent;
    @FXML private TableColumn<CampaignReportRow, String> colFailed;
    @FXML private TableColumn<CampaignReportRow, String> colCreatedAt;

    @FXML private TableView<TaskReportRow> taskTable;
    @FXML private TableColumn<TaskReportRow, String> colTaskId;
    @FXML private TableColumn<TaskReportRow, String> colEmail;
    @FXML private TableColumn<TaskReportRow, String> colStatus;
    @FXML private TableColumn<TaskReportRow, String> colProvider;
    @FXML private TableColumn<TaskReportRow, String> colRetries;
    @FXML private TableColumn<TaskReportRow, String> colLastError;

    @FXML private Label statusLabel;

    private final ApiService apiService = ApiService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTables();
        loadCampaigns();

        campaignTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, newSel) -> {
                if (newSel != null) {
                    loadTasksForCampaign(newSel.getId());
                }
            });
    }

    private void setupTables() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalRecipients"));
        colSent.setCellValueFactory(new PropertyValueFactory<>("sentCount"));
        colFailed.setCellValueFactory(new PropertyValueFactory<>("failedCount"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        colTaskId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("recipientEmail"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProvider.setCellValueFactory(new PropertyValueFactory<>("providerUsed"));
        colRetries.setCellValueFactory(new PropertyValueFactory<>("retryCount"));
        colLastError.setCellValueFactory(new PropertyValueFactory<>("lastError"));
    }

    @FXML
    public void onRefresh() {
        loadCampaigns();
        taskTable.getItems().clear();
    }

    private void loadCampaigns() {
        new Thread(() -> {
            try {
                JsonNode campaigns = apiService.get("/api/campaigns");
                List<CampaignReportRow> rows = new ArrayList<>();
                if (campaigns.isArray()) {
                    for (JsonNode c : campaigns) {
                        rows.add(new CampaignReportRow(
                            c.path("id").asText(),
                            c.path("name").asText(),
                            c.path("totalRecipients").asText("0"),
                            c.path("sentCount").asText("0"),
                            c.path("failedCount").asText("0"),
                            c.path("createdAt").asText()
                        ));
                    }
                }
                Platform.runLater(() -> {
                    campaignTable.setItems(FXCollections.observableArrayList(rows));
                    statusLabel.setText("Loaded " + rows.size() + " campaigns");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void loadTasksForCampaign(String campaignId) {
        new Thread(() -> {
            try {
                JsonNode tasks = apiService.get("/api/campaigns/" + campaignId + "/tasks");
                List<TaskReportRow> rows = new ArrayList<>();
                if (tasks.isArray()) {
                    for (JsonNode t : tasks) {
                        rows.add(new TaskReportRow(
                            t.path("id").asText(),
                            t.path("recipientEmail").asText(),
                            t.path("status").asText(),
                            t.path("providerUsed").asText(),
                            t.path("retryCount").asText("0"),
                            t.path("lastError").asText("")
                        ));
                    }
                }
                Platform.runLater(() ->
                    taskTable.setItems(FXCollections.observableArrayList(rows)));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error loading tasks: " + e.getMessage()));
            }
        }).start();
    }

    public static class CampaignReportRow {
        private final String id, name, totalRecipients, sentCount, failedCount, createdAt;
        public CampaignReportRow(String id, String name, String totalRecipients,
                                  String sentCount, String failedCount, String createdAt) {
            this.id = id; this.name = name; this.totalRecipients = totalRecipients;
            this.sentCount = sentCount; this.failedCount = failedCount; this.createdAt = createdAt;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getTotalRecipients() { return totalRecipients; }
        public String getSentCount() { return sentCount; }
        public String getFailedCount() { return failedCount; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class TaskReportRow {
        private final String id, recipientEmail, status, providerUsed, retryCount, lastError;
        public TaskReportRow(String id, String recipientEmail, String status,
                              String providerUsed, String retryCount, String lastError) {
            this.id = id; this.recipientEmail = recipientEmail; this.status = status;
            this.providerUsed = providerUsed; this.retryCount = retryCount; this.lastError = lastError;
        }
        public String getId() { return id; }
        public String getRecipientEmail() { return recipientEmail; }
        public String getStatus() { return status; }
        public String getProviderUsed() { return providerUsed; }
        public String getRetryCount() { return retryCount; }
        public String getLastError() { return lastError; }
    }
}
