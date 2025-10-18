// File: src/main/java/app/CountPanel.java
package app;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public final class CountPanel {
    private final VBox root = new VBox(12);

    public CountPanel(ReadOnlyStringProperty channelName, ReadOnlyStringProperty channelLogoUrl,
                      ReadOnlyStringProperty videoTitle, ReadOnlyStringProperty countText) {
        root.setBackground(new Background(new BackgroundFill(Color.web("#1A1A1A"), new CornerRadii(14), Insets.EMPTY)));
        root.setPadding(new Insets(16));
        root.setFillWidth(true);

        HBox header = Ui.header(channelLogoUrl.get(), channelName.get()); // 헤더 33px
        channelName.addListener((o, ov, nv) -> Ui.updateHeaderTitle(header, nv));
        channelLogoUrl.addListener((o, ov, nv) -> Ui.updateHeaderImage(header, nv));

        Label title = new Label(videoTitle.get());
        title.setStyle("-fx-font-size:21px; -fx-font-weight:600; -fx-text-fill: rgba(255,255,255,0.9);"); // 1.5×
        videoTitle.addListener((o, ov, nv) -> title.setText(nv));

        Label count = new Label(countText.get());
        count.setStyle("-fx-font-size:56px; -fx-font-weight:900; -fx-font-family:'SF Pro Rounded','Arial Rounded MT Bold';");
        countText.addListener((o, ov, nv) -> count.setText(nv));

        root.getChildren().addAll(header, title, count);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    public VBox getRoot() { return root; }
}
