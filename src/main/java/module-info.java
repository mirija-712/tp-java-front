module com.example.auth_front {
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;

    requires org.kordamp.bootstrapfx.core;

    opens com.example.auth_front to javafx.fxml;
    exports com.example.auth_front;
}