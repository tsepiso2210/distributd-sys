package com.distributedemail.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * MainLayoutController - manages the main application shell with sidebar navigation.
 *
 * The main layout has:
 *   - Left sidebar with navigation buttons
 *   - Right content area that swaps views based on selected menu item
 *
 * Views loaded dynamically:
 *   1. Dashboard.fxml
 *   2. ComposeBulkEmail.fxml
 *   3. TemplateManager.fxml
 *   4. Reports.fxml
 *   5. FailedEmails.fxml
 */
public class MainLayoutController implements Initializable {

    @FXML private BorderPane mainPane;
    @FXML private VBox sidebar;
    @FXML private Label statusLabel;
    @FXML private Button btnDashboard;
    @FXML private Button btnCompose;
    @FXML private Button btnTemplates;
    @FXML private Button btnReports;
    @FXML private Button btnFailed;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Load Dashboard by default
        loadView("Dashboard");
        highlightButton(btnDashboard);
    }

    @FXML
    public void onDashboard(ActionEvent event) {
        loadView("Dashboard");
        highlightButton(btnDashboard);
    }

    @FXML
    public void onCompose(ActionEvent event) {
        loadView("ComposeBulkEmail");
        highlightButton(btnCompose);
    }

    @FXML
    public void onTemplates(ActionEvent event) {
        loadView("TemplateManager");
        highlightButton(btnTemplates);
    }

    @FXML
    public void onReports(ActionEvent event) {
        loadView("Reports");
        highlightButton(btnReports);
    }

    @FXML
    public void onFailedEmails(ActionEvent event) {
        loadView("FailedEmails");
        highlightButton(btnFailed);
    }

    private void loadView(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/" + viewName + ".fxml"));
            Node view = loader.load();
            mainPane.setCenter(view);
            statusLabel.setText("View: " + viewName);
        } catch (IOException e) {
            statusLabel.setText("Error loading view: " + viewName + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void highlightButton(Button active) {
        // Remove active class from all buttons
        for (Node node : sidebar.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().remove("nav-button-active");
            }
        }
        // Add active class to selected button
        active.getStyleClass().add("nav-button-active");
    }
}
