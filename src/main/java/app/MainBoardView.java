// File: src/main/java/app/MainBoardView.java
package app;

import javafx.geometry.Insets;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public final class MainBoardView {
    private final BorderPane root = new BorderPane();

    public MainBoardView(ViewerService service, String apiKey, String v1, String v2) {
        double gap = 8;

        ChatPanel left = new ChatPanel(service.channel1NameProperty(), service.channel1LogoUrlProperty(), v1);
        ChatPanel mid  = new ChatPanel(service.channel2NameProperty(), service.channel2LogoUrlProperty(), v2);

        CountPanel c1 = new CountPanel(service.channel1NameProperty(), service.channel1LogoUrlProperty(),
                service.video1TitleProperty(), service.count1Property());
        CountPanel c2 = new CountPanel(service.channel2NameProperty(), service.channel2LogoUrlProperty(),
                service.video2TitleProperty(), service.count2Property());

        VBox rightCol = new VBox(gap, c1.getRoot(), c2.getRoot());
        rightCol.setFillWidth(true);
        rightCol.setPadding(new Insets(8));

        // 우측 외곽 라운드 테두리
        StackPane rightRounded = new StackPane(rightCol);
        rightRounded.setBorder(new Border(new BorderStroke(
                Color.web("#FFFFFF0F"), BorderStrokeStyle.SOLID, new CornerRadii(14), new BorderWidths(1))));
        rightRounded.setPadding(Insets.EMPTY);

        HBox row = new HBox(gap, left.getRoot(), mid.getRoot(), rightRounded);
        row.setPadding(new Insets(10, 10, 5, 10));

        // 40/40/20 너비 비율
        row.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            left.getRoot().setPrefWidth(w * 0.4);
            mid.getRoot().setPrefWidth(w * 0.4);
            rightRounded.setPrefWidth(w * 0.2);
        });

        root.setCenter(row);
    }

    public BorderPane getRoot() { return root; }
}