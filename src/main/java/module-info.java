module com.memory.gifconverter {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.commons.io;
    requires java.desktop;


    opens com.memory.gifconverter to javafx.fxml;
    exports com.memory.gifconverter;
}