// File: src/main/java/app/Main.java
package app;

import java.awt.Taskbar;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Main extends Application {
    // 자동 시작 옵션(원하면 true로)
    private static final boolean AUTOSTART_IF_ALL_FILLED = false;
    private static Image APP_ICON;

    @Override public void start(Stage stage) {
        AppSettings settings = AppSettings.load();
        Image icon = appIcon();
        stage.getIcons().setAll(icon);
        applyDockIcon(icon);

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
            Scene scene = new Scene(setup.getRoot(), 1100, 760);
            stage.setScene(scene);
            stage.setMinWidth(960);
            stage.setMinHeight(700);
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

    private static Image appIcon() {
        if (APP_ICON != null) return APP_ICON;
        int size = 256;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.TRANSPARENT);
        gc.fillRect(0, 0, size, size);

        gc.setFill(Color.web("#ff1f1f"));
        double arc = size * 0.32;
        gc.fillRoundRect(size * 0.11, size * 0.18, size * 0.78, size * 0.64, arc, arc);

        gc.setFill(Color.WHITE);
        double cx = size * 0.5;
        double cy = size * 0.5;
        double halfHeight = size * 0.18;
        double halfWidth = size * 0.22;
        double[] xs = { cx - halfWidth * 0.6, cx - halfWidth * 0.6, cx + halfWidth };
        double[] ys = { cy - halfHeight, cy + halfHeight, cy };
        gc.fillPolygon(xs, ys, 3);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage img = new WritableImage(size, size);
        APP_ICON = canvas.snapshot(params, img);
        return APP_ICON;
    }

    private static void applyDockIcon(Image fxIcon) {
        if (fxIcon == null) return;
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
            java.awt.Image awtImage = SwingFXUtils.fromFXImage(fxIcon, null);
            if (awtImage != null) {
                taskbar.setIconImage(awtImage);
            }
        } catch (Throwable ignore) {
            // Dock icon customization best-effort; ignore failures on unsupported platforms.
        }
    }

    public static void main(String[] args) { launch(args); }
}
