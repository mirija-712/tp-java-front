module com.example.auth_front {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens com.example.auth_front to javafx.fxml;
    exports com.example.auth_front;
}