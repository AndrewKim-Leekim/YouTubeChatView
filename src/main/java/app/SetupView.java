// File: src/main/java/app/SetupView.java
package app;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public final class SetupView {
    public interface OnStart {
        void run(String apiKey, String channel1, String video1, String channel2, String video2);
    }

    private final VBox root = new VBox(22);
    private final TextField tfApiShow = new TextField();
    private final PasswordField tfApiHide = new PasswordField();
    private final TextField tfChannel1 = new TextField();
    private final TextField tfChannel2 = new TextField();
    private final ComboBox<LiveStreamService.LiveStream> cbLive1 = new ComboBox<>();
    private final ComboBox<LiveStreamService.LiveStream> cbLive2 = new ComboBox<>();
    private final Label status1 = tinyLabel("채널 ID를 입력한 뒤 🔁 버튼으로 확인하세요.");
    private final Label status2 = tinyLabel("채널 ID를 입력한 뒤 🔁 버튼으로 확인하세요.");
    private final Label selected1 = tinyLabel("🎬 선택된 방송이 없습니다.");
    private final Label selected2 = tinyLabel("🎬 선택된 방송이 없습니다.");
    private final LiveStreamService liveService = new LiveStreamService();

    private final BooleanProperty showKey = new SimpleBooleanProperty(false);
    private final BooleanProperty editKey = new SimpleBooleanProperty(false);

    public SetupView(AppSettings settings, OnStart onStart) {
        root.setPadding(new Insets(32));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #eef2ff, #f8fafc);");
        root.setMinWidth(820);
        root.setPrefWidth(880);
        root.setMinHeight(560);

        Label title = new Label("✨ YouTube Multi ChatViewer");
        title.setStyle("-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:#1e293b;");
        Label tagline = new Label("채널 ID만 입력하면 실시간 방송을 바로 연결할 수 있어요.");
        tagline.setStyle("-fx-font-size:14px; -fx-text-fill:#475569;");

        tfApiShow.setText(settings.apiKey);
        tfApiHide.setText(settings.apiKey);
        tfChannel1.setText(settings.channel1);
        tfChannel2.setText(settings.channel2);

        HBox apiRow = new HBox(12);
        apiRow.setAlignment(Pos.CENTER_LEFT);
        Label lApi = wideLabel("🔑 API Key");
        tfApiShow.setPromptText("AIza...");
        tfApiHide.setPromptText("AIza...");
        tfApiShow.setDisable(true);
        tfApiHide.setDisable(true);
        Button bShow = pillButton("👁 보기");
        Button bEdit = pillButton("✏️ 편집");
        bShow.setOnAction(e -> toggleShow(bShow));
        bEdit.setOnAction(e -> toggleEdit(bEdit));
        Node apiField = stacked(tfApiShow, tfApiHide);
        HBox.setHgrow((Region) apiField, Priority.ALWAYS);
        apiRow.getChildren().addAll(lApi, apiField, bShow, bEdit);

        ChannelSection channelSection1 = buildChannelSection("📡 채널 1", tfChannel1, cbLive1, status1, selected1, settings.video1);
        ChannelSection channelSection2 = buildChannelSection("🎥 채널 2", tfChannel2, cbLive2, status2, selected2, settings.video2);

        Label help = new Label("☎️ 사용방법 문의: 세종중문교회 방송실");
        help.setStyle("-fx-font-size:16px; -fx-text-fill:#334155; -fx-font-weight:bold;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button start = new Button("🚀 Start");
        start.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:white; -fx-background-radius:28px;"
                + "-fx-padding:10px 28px; -fx-background-color: linear-gradient(to right,#6366f1,#8b5cf6);");
        start.disableProperty().bind(new BooleanBinding() {
            { bind(cbLive1.valueProperty(), cbLive2.valueProperty()); }
            @Override protected boolean computeValue() {
                return cbLive1.getValue() == null || cbLive2.getValue() == null;
            }
        });
        start.setOnAction(e -> {
            LiveStreamService.LiveStream live1 = cbLive1.getValue();
            LiveStreamService.LiveStream live2 = cbLive2.getValue();
            String api = getApiKey().trim();
            String channel1 = tfChannel1.getText().trim();
            String channel2 = tfChannel2.getText().trim();
            onStart.run(api, channel1, live1 == null ? "" : live1.videoId(), channel2,
                    live2 == null ? "" : live2.videoId());
        });

        HBox startRow = new HBox(start);
        startRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, tagline, apiRow, channelSection1.box(), channelSection2.box(), spacer, help, startRow);

        if (!settings.channel1.isEmpty()) {
            Platform.runLater(() -> lookupLive(tfChannel1, cbLive1, status1, selected1,
                    channelSection1.refreshButton(), settings.video1));
        }
        if (!settings.channel2.isEmpty()) {
            Platform.runLater(() -> lookupLive(tfChannel2, cbLive2, status2, selected2,
                    channelSection2.refreshButton(), settings.video2));
        }
    }

    private ChannelSection buildChannelSection(String heading,
                                               TextField channelField,
                                               ComboBox<LiveStreamService.LiveStream> combo,
                                               Label status,
                                               Label selected,
                                               String preferVideoId) {
        VBox wrapper = new VBox(10);
        wrapper.setPadding(new Insets(16));
        wrapper.setStyle("-fx-background-color: rgba(255,255,255,0.75); -fx-background-radius:18px;"
                + "-fx-effect: dropshadow(gaussian, rgba(79,70,229,0.15), 14, 0.2, 0, 4);");

        Label headingLabel = new Label(heading + " 실시간 연결");
        headingLabel.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:#312e81;");

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = wideLabel("🆔 채널 ID");
        channelField.setPromptText("예: UC_x5XG1OV2P6uZZ5FSM9Ttw");
        HBox.setHgrow(channelField, Priority.ALWAYS);
        Button refresh = pillButton("🔁 라이브 찾기");
        refresh.setOnAction(e -> lookupLive(channelField, combo, status, selected, refresh,
                combo.getValue() != null ? combo.getValue().videoId() : preferVideoId));
        row.getChildren().addAll(lbl, channelField, refresh);

        combo.setPromptText("실시간 방송을 선택하세요");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCellFactory(list -> new LiveStreamCell(null));
        combo.setButtonCell(new LiveStreamCell("실시간 방송을 선택하세요"));

        combo.valueProperty().addListener((obs, old, val) -> {
            if (val == null) {
                selected.setText("🎬 선택된 방송이 없습니다.");
            } else {
                selected.setText("🎬 선택: " + val.title());
            }
        });

        channelField.textProperty().addListener((obs, old, val) -> {
            if (!Objects.equals(old, val)) {
                combo.getItems().clear();
                combo.setValue(null);
                status.setText("채널 ID를 입력한 뒤 🔁 버튼으로 확인하세요.");
            }
        });

        VBox.setMargin(combo, new Insets(4, 0, 0, 0));

        wrapper.getChildren().addAll(headingLabel, row, combo, status, selected);
        return new ChannelSection(wrapper, refresh);
    }

    private Label wideLabel(String text) {
        Label l = new Label(text);
        l.setPrefWidth(180);
        l.setAlignment(Pos.CENTER_LEFT);
        l.setStyle("-fx-font-weight:bold; -fx-text-fill:#1f2937;");
        return l;
    }

    private Label tinyLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#475569;");
        return l;
    }

    private Button pillButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-radius:20px; -fx-background-color:#e0e7ff; -fx-text-fill:#1e3a8a;"
                + "-fx-font-weight:bold; -fx-padding:6px 14px;");
        return b;
    }

    private Node stacked(TextField shown, PasswordField hidden) {
        StackPane sp = new StackPane(hidden, shown);
        shown.visibleProperty().bind(showKey);
        shown.managedProperty().bind(showKey);
        hidden.visibleProperty().bind(showKey.not());
        hidden.managedProperty().bind(showKey.not());
        return sp;
    }

    private void toggleShow(Button b) {
        showKey.set(!showKey.get());
        b.setText(showKey.get() ? "🙈 숨기기" : "👁 보기");
    }

    private void toggleEdit(Button b) {
        editKey.set(!editKey.get());
        tfApiShow.setDisable(!editKey.get());
        tfApiHide.setDisable(!editKey.get());
        b.setText(editKey.get() ? "✅ 완료" : "✏️ 편집");
        if (!editKey.get()) {
            String t = getApiKey().trim();
            tfApiShow.setText(t);
            tfApiHide.setText(t);
        }
    }

    private void lookupLive(TextField channelField,
                            ComboBox<LiveStreamService.LiveStream> combo,
                            Label status,
                            Label selected,
                            Button trigger,
                            String preferVideoId) {
        String channelId = channelField.getText() == null ? "" : channelField.getText().trim();
        if (channelId.isEmpty()) {
            status.setText("⚠️ 채널 ID를 입력해주세요.");
            combo.getItems().clear();
            combo.setValue(null);
            selected.setText("🎬 선택된 방송이 없습니다.");
            return;
        }
        status.setText("🔍 라이브 방송 확인 중...");
        selected.setText(" ");
        if (trigger != null) trigger.setDisable(true);
        liveService.fetchLiveStreams(getApiKey(), channelId).whenComplete((list, ex) ->
                Platform.runLater(() -> {
                    if (trigger != null) trigger.setDisable(false);
                    if (ex != null) {
                        String msg = ex.getMessage();
                        if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                        status.setText("⚠️ 조회 실패: " + msg);
                        combo.getItems().clear();
                        combo.setValue(null);
                        selected.setText("🎬 선택된 방송이 없습니다.");
                        return;
                    }
                    if (list == null || list.isEmpty()) {
                        status.setText("❌ 현재 진행 중인 라이브가 없습니다.");
                        combo.getItems().clear();
                        combo.setValue(null);
                        selected.setText("🎬 선택된 방송이 없습니다.");
                        return;
                    }
                    combo.setItems(FXCollections.observableArrayList(list));
                    LiveStreamService.LiveStream chosen = null;
                    if (preferVideoId != null && !preferVideoId.isBlank()) {
                        for (LiveStreamService.LiveStream ls : list) {
                            if (preferVideoId.equals(ls.videoId())) {
                                chosen = ls;
                                break;
                            }
                        }
                    }
                    if (chosen == null) chosen = list.get(0);
                    combo.setValue(chosen);
                    status.setText("✅ 라이브 " + list.size() + "개를 찾았습니다.");
                    selected.setText("🎬 선택: " + chosen.title());
                }));
    }

    private String getApiKey() {
        return showKey.get() ? tfApiShow.getText() : tfApiHide.getText();
    }

    public VBox getRoot() {
        return root;
    }

    private static class LiveStreamCell extends ListCell<LiveStreamService.LiveStream> {
        private final Label title = new Label();
        private final Label subtitle = new Label();
        private final VBox box = new VBox(2);
        private final String placeholder;

        LiveStreamCell(String placeholder) {
            this.placeholder = placeholder;
            title.setStyle("-fx-font-weight:bold; -fx-text-fill:#1f2937;");
            subtitle.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
            subtitle.setWrapText(true);
            box.getChildren().addAll(title, subtitle);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(LiveStreamService.LiveStream item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(placeholder);
                setGraphic(null);
            } else {
                setText(null);
                title.setText(item.title());
                String sub = item.subtitle();
                if (sub == null || sub.isBlank()) {
                    subtitle.setManaged(false);
                    subtitle.setVisible(false);
                } else {
                    subtitle.setManaged(true);
                    subtitle.setVisible(true);
                    subtitle.setText(sub);
                }
                setGraphic(box);
            }
        }
    }

    private record ChannelSection(VBox box, Button refreshButton) {}
}
