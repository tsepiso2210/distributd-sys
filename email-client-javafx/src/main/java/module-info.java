/**
 * module-info.java - Java Module System descriptor for the JavaFX desktop client.
 *
 * Required for JavaFX on Java 9+ when running via the module path.
 *
 * If you encounter "module not found" errors during local build, ensure you
 * have JavaFX installed (e.g. via SDKMAN or Liberica JDK which bundles JavaFX):
 *   sdk install java 21.0.2-librca
 *
 * Alternative (remove this file and use VM args instead):
 *   mvn javafx:run -Djvm.args="--add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED"
 */
module com.distributedemail.client {

    // JavaFX modules needed for controls, FXML layouts, and rendering
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // OkHttp HTTP client (automatic module — jar on classpath)
    requires okhttp3;

    // Jackson for JSON parsing of API responses
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // Lombok annotations (compile-only, not needed at runtime)
    requires static lombok;

    // ---------------------------------------------------------------
    // Open packages to JavaFX reflection (required for @FXML injection)
    // JavaFX uses reflection to inject @FXML-annotated fields in controllers.
    // ---------------------------------------------------------------
    opens com.distributedemail.client to javafx.fxml;
    opens com.distributedemail.client.controller to javafx.fxml;

    // Export public API packages
    exports com.distributedemail.client;
    exports com.distributedemail.client.controller;
    exports com.distributedemail.client.service;
}
