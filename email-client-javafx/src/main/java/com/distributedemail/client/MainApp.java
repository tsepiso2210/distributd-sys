package com.distributedemail.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * MainApp - JavaFX application entry point.
 *
 * Assignment requirement: "JavaFX desktop client module"
 *
 * GUI Views:
 *   1. Dashboard         - Overview of email statistics
 *   2. Compose Bulk Email - Create a new bulk email campaign
 *   3. Template Manager  - CRUD for email templates with placeholders
 *   4. Reports / Logs    - View campaign and delivery logs
 *   5. Failed Emails     - View and retry failed email tasks
 *
 * NOTE: This app requires a running email-api-service.
 *       Default API URL: http://localhost:8081
 *       Change the URL in ApiService.java or via an environment variable.
 *
 * To run locally:
 *   cd email-client-javafx
 *   mvn javafx:run
 *
 * Prerequisites:
 *   - Java 17+ with JavaFX support (or via JAVA_TOOL_OPTIONS with module path)
 *   - email-api-service running on port 8081
 */
public class MainApp extends Application {

    public static final String API_BASE_URL =
        System.getProperty("api.url", "http://localhost:8081");

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 750);

        // Load the CSS stylesheet
        scene.getStylesheets().add(
            getClass().getResource("/css/main.css").toExternalForm());

        primaryStage.setTitle("Distributed Bulk Email Sender");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
