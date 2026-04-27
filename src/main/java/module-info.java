module com.example.tiete_monitor {
    requires javafx.controls;
    requires javafx.fxml;

    // ESSAS LINHAS SÃO AS MAIS IMPORTANTES:
    opens com.example.tiete_monitor to javafx.graphics;
    opens com.example.tiete_monitor.frontend to javafx.graphics;

    exports com.example.tiete_monitor;
    exports com.example.tiete_monitor.frontend;
}