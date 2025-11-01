// File: src/main/java/app/Main.java
package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    // 자동 시작 옵션(원하면 true로)
    private static final boolean AUTOSTART_IF_ALL_FILLED = false;

    @Override public void start(Stage stage) {
        AppSettings settings = AppSettings.load();

        Runnable openSetup = () -> {
            SetupView setup = new SetupView(settings, (apiKey, channel1, video1, channel2, video2) -> {
                settings.apiKey = apiKey;
                settings.channel1 = channel1;
                settings.channel2 = channel2;
                settings.video1 = video1;
                settings.video2 = video2;
                settings.save(); // WHY: Start 시 확정값 저장
                ViewerService service = new ViewerService();
                openMain(stage, service, settings);
            });
            stage.setScene(new Scene(setup.getRoot(), 960, 640));
            stage.setTitle("Setup");
            stage.setOnCloseRequest(e -> { e.consume(); ConfirmDialogs.askQuit(stage); });
            stage.show();
        };

        boolean ready = !settings.video1.isEmpty() && !settings.video2.isEmpty();
        if (AUTOSTART_IF_ALL_FILLED && ready) {
            ViewerService service = new ViewerService();
            openMain(stage, service, settings);
        } else {
            openSetup.run();
        }
    }

    private void openMain(Stage stage, ViewerService service, AppSettings s) {
        MainBoardView main = new MainBoardView(service, s.apiKey, s.video1, s.video2);
        stage.setScene(new Scene(main.getRoot(), 1280, 800));
        stage.setTitle("YouTube Multi Chat Viewer (JavaFX)");
        stage.centerOnScreen();
        stage.setOnCloseRequest(e -> { e.consume(); ConfirmDialogs.askQuit(stage); });
        stage.show();
        service.start(s.apiKey, s.video1, s.video2);
    }

    public static void main(String[] args) { launch(args); }
}
