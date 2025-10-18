// File: src/main/java/app/ConfirmDialogs.java
package app;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public final class ConfirmDialogs {
    private ConfirmDialogs(){}

    public static void askQuit(Stage stage) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "현재 창을 닫으면 앱이 종료됩니다.",
                new ButtonType("취소", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("종료", ButtonBar.ButtonData.OK_DONE));
        a.setTitle("프로그램을 종료할까요?");
        a.initOwner(stage);
        a.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) stage.close();
        });
    }
}
