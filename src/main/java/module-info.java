module com.cvagrofarmsstore {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;
    requires org.apache.logging.log4j;
    requires java.prefs;

    // Open packages to javafx.fxml for reflection-based FXML injection
    opens com.cvagrofarmsstore.controllers to javafx.fxml;
    opens com.cvagrofarmsstore.model       to javafx.base;

    // Export main package so JavaFX launcher can access App
    exports com.cvagrofarmsstore;
}
