// File: src/main/java/app/ChatPanel.java
package app;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public final class ChatPanel {
    private final VBox root = new VBox(10);

    public ChatPanel(ReadOnlyStringProperty channelName, ReadOnlyStringProperty channelLogoUrl, String videoId) {
        root.setBackground(new Background(new BackgroundFill(Color.web("#151515"), new CornerRadii(14), Insets.EMPTY)));
        root.setPadding(new Insets(12));
        root.setFillWidth(true);

        HBox header = Ui.header(channelLogoUrl.get(), channelName.get());
        channelName.addListener((o, ov, nv) -> Ui.updateHeaderTitle(header, nv));
        channelLogoUrl.addListener((o, ov, nv) -> Ui.updateHeaderImage(header, nv));
        root.getChildren().add(header);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setBackground(new Background(new BackgroundFill(Color.web("#FFFFFF40"), CornerRadii.EMPTY, Insets.EMPTY)));
        sep.setOpacity(0.25);
        root.getChildren().add(sep);

        WebView wv = new WebView();
        wv.setZoom(1.6); // WHY: 보이는 줄 수를 ~절반으로 줄이기(가독성↑)
        WebEngine eng = wv.getEngine();
        eng.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36");
        eng.load("https://www.youtube.com/live_chat?v=" + videoId + "&is_popout=1&dark_theme=1");

        // WHY: 채팅 폰트/줄간격 확대(페이지 로드 후 CSS 주입)
        eng.getLoadWorker().stateProperty().addListener((obs, oldS, newS) -> {
            if (newS == Worker.State.SUCCEEDED) {
                String js = """
                (function(){
                  try {
                    const id='__ytmcv_boost__';
                    if (document.getElementById(id)) return;
                    const css = `
                      yt-live-chat-text-message-renderer,
                      yt-live-chat-legacy-text-message-renderer {
                        font-size: 18px !important;
                        line-height: 1.35 !important;
                      }
                      #items.yt-live-chat-item-list-renderer {
                        padding: 4px !important;
                      }
                    `;
                    const st = document.createElement('style');
                    st.id = id; st.textContent = css;
                    document.documentElement.appendChild(st);
                  } catch(e) {}
                })();
                """;
                try { eng.executeScript(js); } catch (Exception ignore) {}
            }
        });

        root.getChildren().add(wv);
        VBox.setVgrow(wv, Priority.ALWAYS);
        root.setBorder(new Border(new BorderStroke(Color.TRANSPARENT, BorderStrokeStyle.SOLID, new CornerRadii(14), BorderWidths.DEFAULT)));
    }

    public VBox getRoot() { return root; }
}
