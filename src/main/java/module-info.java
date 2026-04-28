module org.example.frontjavafx.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.net.http;

    opens org.example.frontjavafx.app to javafx.fxml;
    opens org.example.frontjavafx.model to javafx.base;

    exports org.example.frontjavafx.app;
}