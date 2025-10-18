module app {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires java.net.http;
    requires java.prefs;
    requires com.fasterxml.jackson.databind;

    exports app;
    opens app to javafx.graphics;
}
