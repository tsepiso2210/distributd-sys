package com.distributedemail.client.controller;

import com.distributedemail.client.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * DashboardController - displays real-time statistics from the API.
 *
 * Assignment requirement: "Dashboard" GUI view
 *
 * Displays:
 *   - Total emails sent, delivered, failed, dead-lettered
 *   - Recent campaigns table
 *   - Refresh button to reload stats
 *
 * Calls: GET /api/dashboard/stats
 */
public class DashboardController implements Initializable {

    @FXML private Label lblTotalSent;
    @FXML private Label lblTotalDelivered;
    @FXML private Label lblTotalFailed;
    @FXML private Label lblTotalPending;
    @FXML private Label lblTotalDeadLettered;
    @FXML private Label lblTotalRetrying;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private TableView<CampaignRow> campaignTable;
    @FXML private TableColumn<CampaignRow, String> colCampaignId;
    @FXML private TableColumn<CampaignRow, String> colCampaignName;
    @FXML private TableColumn<CampaignRow, String> colRecipients;
    @FXML private TableColumn<CampaignRow, String> colCreatedAt;

    private final ApiService apiService = ApiService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadStats();
    }

    private void setupTable() {
        colCampaignId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCampaignName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colRecipients.setCellValueFactory(new PropertyValueFactory<>("totalRecipients"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }

    @FXML
    public void onRefresh() {
        loadStats();
    }

    private void loadStats() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("Loading...");

        // Run API call on background thread to avoid freezing the UI
        new Thread(() -> {
            try {
                JsonNode stats = apiService.get("/api/dashboard/stats");

                Platform.runLater(() -> {
                    lblTotalSent.setText(String.valueOf(stats.path("totalSent").asLong(0)));
                    lblTotalDelivered.setText(String.valueOf(stats.path("totalDelivered").asLong(0)));
                    lblTotalFailed.setText(String.valueOf(stats.path("totalFailed").asLong(0)));
                    lblTotalPending.setText(String.valueOf(stats.path("totalPending").asLong(0)));
                    lblTotalDeadLettered.setText(String.valueOf(stats.path("totalDeadLettered").asLong(0)));
                    lblTotalRetrying.setText(String.valueOf(stats.path("totalRetrying").asLong(0)));
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Stats loaded successfully");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Error loading stats: " + e.getMessage());
                });
            }
        }).start();
    }

    /** Inner class to represent a campaign row in the table */
    public static class CampaignRow {
        private final String id;
        private final String name;
        private final String totalRecipients;
        private final String createdAt;

        public CampaignRow(String id, String name, String totalRecipients, String createdAt) {
            this.id = id;
            this.name = name;
            this.totalRecipients = totalRecipients;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getTotalRecipients() { return totalRecipients; }
        public String getCreatedAt() { return createdAt; }
    }
}
