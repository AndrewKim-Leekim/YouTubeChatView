// File: src/main/java/app/Ui.java
package app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public final class Ui {
    private Ui(){}

    public static HBox header(String logoUrl, String name) {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        ImageView iv = avatar(logoUrl, 44);
        Label title = new Label(name == null || name.isEmpty() ? "—" : name);
        title.setStyle("-fx-font-size:33px; -fx-font-weight:600;"); // 1.5×
        h.getChildren().addAll(iv, title);
        return h;
    }
    public static void updateHeaderTitle(HBox header, String name) {
        ((Label)header.getChildren().get(1)).setText((name==null||name.isEmpty())?"—":name);
    }
    public static void updateHeaderImage(HBox header, String url) {
        ImageView iv = (ImageView) header.getChildren().get(0);
        if (url != null && !url.isEmpty()) iv.setImage(new Image(url, true));
    }
    public static ImageView avatar(String url, double size) {
        ImageView iv = new ImageView();
        if (url != null && !url.isEmpty()) iv.setImage(new Image(url, true));
        iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(true);
        iv.setClip(new javafx.scene.shape.Circle(size/2, size/2, size/2));
        return iv;
    }
}
