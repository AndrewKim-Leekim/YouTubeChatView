// File: src/main/java/app/SetupView.java
package app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class SetupView {
    public interface OnStart { void run(String apiKey, String v1, String v2); }

    private final VBox root = new VBox(16);
    private final TextField tfApiShow = new TextField();
    private final PasswordField tfApiHide = new PasswordField();
    private final TextField tfV1 = new TextField();
    private final TextField tfV2 = new TextField();
    private boolean showKey = false;
    private boolean editKey = false;

    public SetupView(AppSettings settings, OnStart onStart) {
        root.setPadding(new Insets(24));
        Label title = new Label("YouTube Multi ChatViewer");
        title.setStyle("-fx-font-size:22px; -fx-font-weight:bold;");

        // preload from settings
        tfApiShow.setText(settings.apiKey);
        tfApiHide.setText(settings.apiKey);
        tfV1.setText(settings.video1);
        tfV2.setText(settings.video2);

        HBox apiRow = new HBox(8);
        apiRow.setAlignment(Pos.CENTER_LEFT);
        Label lApi = wideLabel("API Key");
        tfApiShow.setPromptText("AIza...");
        tfApiHide.setPromptText("AIza...");
        tfApiShow.setDisable(true); tfApiHide.setDisable(true); // WHY: 편집 버튼 눌러야 수정
        Button bShow = new Button("보기");
        Button bEdit = new Button("편집");
        bShow.setOnAction(e -> toggleShow(bShow));
        bEdit.setOnAction(e -> toggleEdit(bEdit));
        Node apiField = stacked(tfApiShow, tfApiHide, () -> showKey);
        HBox.setHgrow((Region)apiField, Priority.ALWAYS);
        apiRow.getChildren().addAll(lApi, apiField, bShow, bEdit);

        HBox v1Row = new HBox(8);
        v1Row.setAlignment(Pos.CENTER_LEFT);
        Label lV1 = wideLabel("YouTube Video ID 1");
        tfV1.setPromptText("예: ItEYI3SR7ig");
        HBox.setHgrow(tfV1, Priority.ALWAYS);
        v1Row.getChildren().addAll(lV1, tfV1);

        HBox v2Row = new HBox(8);
        v2Row.setAlignment(Pos.CENTER_LEFT);
        Label lV2 = wideLabel("YouTube Video ID 2");
        tfV2.setPromptText("예: AbCdEfG1234");
        HBox.setHgrow(tfV2, Priority.ALWAYS);
        v2Row.getChildren().addAll(lV2, tfV2);

        Label help = new Label("사용방법문의: 세종중문교회 방송실");
        help.setStyle("-fx-font-size:20px; -fx-font-weight:bold;");

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);

        Button start = new Button("Start");
        start.setPrefWidth(120);
        HBox startRow = new HBox(start); startRow.setAlignment(Pos.CENTER_RIGHT);
        start.disableProperty().bind(
                tfV1.textProperty().isEmpty()
                        .or(tfV2.textProperty().isEmpty())
                        .or(tfApiShow.textProperty().isEmpty().and(tfApiHide.textProperty().isEmpty()))
        );
        start.setOnAction(e -> {
            String api = getApiKey().trim();
            String v1 = tfV1.getText().trim();
            String v2 = tfV2.getText().trim();
            onStart.run(api, v1, v2);
        });

        root.getChildren().addAll(title, apiRow, v1Row, v2Row, help, spacer, startRow);
    }

    private Label wideLabel(String text) {
        Label l = new Label(text); l.setPrefWidth(160); l.setAlignment(Pos.CENTER_LEFT); return l;
    }

    private Node stacked(TextField shown, PasswordField hidden, BooleanSupplier visible) {
        StackPane sp = new StackPane(hidden, shown);
        shown.visibleProperty().bind(new javafx.beans.binding.BooleanBinding() {
            @Override protected boolean computeValue() { return visible.getAsBoolean(); }
        });
        hidden.visibleProperty().bind(new javafx.beans.binding.BooleanBinding() {
            @Override protected boolean computeValue() { return !visible.getAsBoolean(); }
        });
        return sp;
    }

    private void toggleShow(Button b) {
        showKey = !showKey;
        b.setText(showKey ? "숨기기" : "보기");
    }

    private void toggleEdit(Button b) {
        editKey = !editKey;
        tfApiShow.setDisable(!editKey);
        tfApiHide.setDisable(!editKey);
        b.setText(editKey ? "완료" : "편집");
        if (!editKey) {
            String t = getApiKey().trim();
            tfApiShow.setText(t); tfApiHide.setText(t);
        }
    }

    private String getApiKey() { return showKey ? tfApiShow.getText() : tfApiHide.getText(); }
    public VBox getRoot() { return root; }

    private interface BooleanSupplier { boolean getAsBoolean(); }
}
