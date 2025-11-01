// File: src/main/java/app/CountPanel.java
package app;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public final class CountPanel {
    private final VBox root = new VBox(18);

    public CountPanel(ReadOnlyStringProperty channelName, ReadOnlyStringProperty channelLogoUrl,
                      ReadOnlyStringProperty videoTitle, ReadOnlyStringProperty countText,
                      ReadOnlyStringProperty subsText) {
        root.setBackground(new Background(new BackgroundFill(Color.web("#1A1A1A"), new CornerRadii(14), Insets.EMPTY)));
        root.setPadding(new Insets(16));
        root.setFillWidth(true);

        HBox header = Ui.header(channelLogoUrl.get(), channelName.get()); // 헤더 33px
        channelName.addListener((o, ov, nv) -> Ui.updateHeaderTitle(header, nv));
        channelLogoUrl.addListener((o, ov, nv) -> Ui.updateHeaderImage(header, nv));

        Label title = new Label(videoTitle.get());
        title.setStyle("-fx-font-size:21px; -fx-font-weight:600; -fx-text-fill: rgba(255,255,255,0.9);"); // 1.5×
        videoTitle.addListener((o, ov, nv) -> title.setText(nv));

        VBox metrics = new VBox(12,
                metricBox("시청자:", countText, true,
                        "linear-gradient(to right, rgba(239,68,68,0.28), rgba(185,28,28,0.18))",
                        "#fecaca", "#fee2e2", 52),
                metricBox("구독자:", subsText, false,
                        "linear-gradient(to right, rgba(59,130,246,0.24), rgba(29,78,216,0.16))",
                        "#bfdbfe", "#dbeafe", 40));

        root.getChildren().addAll(header, title, metrics);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    public VBox getRoot() { return root; }

    private VBox metricBox(String labelText,
                           ReadOnlyStringProperty valueProp,
                           boolean emphasize,
                           String background,
                           String labelColor,
                           String valueColor,
                           double valueFontSize) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 16, 14, 16));
        box.setStyle("-fx-background-color: " + background + "; -fx-background-radius: 14px;" +
                (emphasize ? "-fx-effect: dropshadow(gaussian, rgba(239,68,68,0.25), 18, 0.25, 0, 6);" :
                        "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.18), 14, 0.3, 0, 4);"));

        Label label = new Label(labelText);
        label.setStyle("-fx-font-size:14px; -fx-font-weight:700; -fx-text-fill:" + labelColor +
                "; -fx-letter-spacing:0.6px;");

        Label value = new Label(decorateValue(valueProp.get()));
        value.setWrapText(true);
        value.setStyle("-fx-font-size:" + valueFontSize + "px; -fx-font-weight:900; -fx-text-fill:" + valueColor +
                "; -fx-font-family:'SF Pro Rounded','Arial Rounded MT Bold','Malgun Gothic';");

        valueProp.addListener((obs, old, nv) -> value.setText(decorateValue(nv)));

        box.getChildren().addAll(label, value);
        return box;
    }

    private String decorateValue(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        if ("—".equals(raw)) return raw;
        if ("비공개".equals(raw)) return raw;
        if (raw.endsWith("명")) return raw;
        return raw + "명";
    }
}
