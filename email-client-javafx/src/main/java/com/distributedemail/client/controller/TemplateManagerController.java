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
import java.util.*;

/**
 * TemplateManagerController - CRUD UI for email templates.
 *
 * Assignment requirement: "Template Management" GUI view
 *                          "Email templates with dynamic placeholders"
 *
 * Features:
 *   - List all templates
 *   - Create new templates with {{name}}, {{studentNumber}}, {{course}} support
 *   - Edit existing templates
 *   - Delete templates
 *   - Live preview with sample variables
 */
public class TemplateManagerController implements Initializable {

    @FXML private TableView<TemplateRow> templateTable;
    @FXML private TableColumn<TemplateRow, String> colId;
    @FXML private TableColumn<TemplateRow, String> colName;
    @FXML private TableColumn<TemplateRow, String> colSubject;
    @FXML private TableColumn<TemplateRow, String> colCreatedAt;

    @FXML private TextField txtTemplateName;
    @FXML private TextField txtSubjectTemplate;
    @FXML private TextArea txtBodyTemplate;
    @FXML private TextArea txtDescription;
    @FXML private TextArea txtPreviewVars;
    @FXML private TextArea txtPreviewOutput;
    @FXML private Label statusLabel;

    private final ApiService apiService = ApiService.getInstance();
    private Long selectedTemplateId = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSubject.setCellValueFactory(new PropertyValueFactory<>("subjectTemplate"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        // When a template is selected, populate the editor fields
        templateTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, newSel) -> {
                if (newSel != null) {
                    populateEditor(newSel);
                }
            });

        loadTemplates();
        setDefaultPreviewVars();
    }

    private void setDefaultPreviewVars() {
        txtPreviewVars.setText(
            "{\n" +
            "  \"name\": \"Alice Smith\",\n" +
            "  \"studentNumber\": \"ST001\",\n" +
            "  \"course\": \"CS401 Distributed Systems\"\n" +
            "}"
        );
    }

    @FXML
    public void onNew() {
        clearEditor();
        selectedTemplateId = null;
        statusLabel.setText("Fill in the form and click Save to create a new template");
    }

    @FXML
    public void onSave() {
        if (txtTemplateName.getText().trim().isEmpty()) {
            statusLabel.setText("Template name is required");
            return;
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("name", txtTemplateName.getText().trim());
        payload.put("subjectTemplate", txtSubjectTemplate.getText().trim());
        payload.put("bodyTemplate", txtBodyTemplate.getText().trim());
        payload.put("description", txtDescription.getText().trim());

        new Thread(() -> {
            try {
                JsonNode result;
                if (selectedTemplateId == null) {
                    result = apiService.post("/api/templates", payload);
                    Platform.runLater(() -> statusLabel.setText("Template created: " + result.path("name").asText()));
                } else {
                    result = apiService.put("/api/templates/" + selectedTemplateId, payload);
                    Platform.runLater(() -> statusLabel.setText("Template updated: " + result.path("name").asText()));
                }
                loadTemplates();
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error saving template: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void onDelete() {
        if (selectedTemplateId == null) {
            statusLabel.setText("Select a template to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete this template?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Long idToDelete = selectedTemplateId;
                new Thread(() -> {
                    try {
                        apiService.delete("/api/templates/" + idToDelete);
                        Platform.runLater(() -> {
                            statusLabel.setText("Template deleted");
                            clearEditor();
                        });
                        loadTemplates();
                    } catch (Exception e) {
                        Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    /**
     * Live preview: render the template body with sample variables.
     * Calls POST /api/templates/{id}/preview
     */
    @FXML
    public void onPreview() {
        if (selectedTemplateId == null) {
            statusLabel.setText("Save or select a template to preview it");
            return;
        }

        new Thread(() -> {
            try {
                String varsJson = txtPreviewVars.getText().trim();
                Map<String, String> vars = apiService.getObjectMapper()
                    .readValue(varsJson, Map.class);

                JsonNode result = apiService.post("/api/templates/" + selectedTemplateId + "/preview", vars);
                String rendered = result.path("rendered").asText();

                Platform.runLater(() -> txtPreviewOutput.setText(rendered));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Preview error: " + e.getMessage()));
            }
        }).start();
    }

    private void loadTemplates() {
        new Thread(() -> {
            try {
                JsonNode templates = apiService.get("/api/templates");
                List<TemplateRow> rows = new ArrayList<>();

                if (templates.isArray()) {
                    for (JsonNode t : templates) {
                        rows.add(new TemplateRow(
                            t.path("id").asText(),
                            t.path("name").asText(),
                            t.path("subjectTemplate").asText(),
                            t.path("createdAt").asText()
                        ));
                    }
                }

                Platform.runLater(() ->
                    templateTable.setItems(FXCollections.observableArrayList(rows)));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Load error: " + e.getMessage()));
            }
        }).start();
    }

    private void populateEditor(TemplateRow row) {
        selectedTemplateId = Long.parseLong(row.getId());
        new Thread(() -> {
            try {
                JsonNode t = apiService.get("/api/templates/" + selectedTemplateId);
                Platform.runLater(() -> {
                    txtTemplateName.setText(t.path("name").asText());
                    txtSubjectTemplate.setText(t.path("subjectTemplate").asText());
                    txtBodyTemplate.setText(t.path("bodyTemplate").asText());
                    txtDescription.setText(t.path("description").asText());
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error loading template: " + e.getMessage()));
            }
        }).start();
    }

    private void clearEditor() {
        txtTemplateName.clear();
        txtSubjectTemplate.clear();
        txtBodyTemplate.clear();
        txtDescription.clear();
        txtPreviewOutput.clear();
        selectedTemplateId = null;
    }

    public static class TemplateRow {
        private final String id;
        private final String name;
        private final String subjectTemplate;
        private final String createdAt;

        public TemplateRow(String id, String name, String subjectTemplate, String createdAt) {
            this.id = id;
            this.name = name;
            this.subjectTemplate = subjectTemplate;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getSubjectTemplate() { return subjectTemplate; }
        public String getCreatedAt() { return createdAt; }
    }
}
