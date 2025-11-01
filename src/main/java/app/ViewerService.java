// File: src/main/java/app/ViewerService.java
package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class ViewerService {
    private static final Map<String,String> DEFAULT_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36",
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.6,en;q=0.5",
            "Cookie", "CONSENT=YES+cb.20210328-17-p0.en+F+678"
    );
    private static final String INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU1d76aAPkbD-fD-6I2yiqZ7Q";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper om = new ObjectMapper();
    private final ScheduledExecutorService sch = Executors.newSingleThreadScheduledExecutor();
    private final NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);

    private String apiKey = "";
    private String v1 = "";
    private String v2 = "";

    private final SimpleStringProperty count1 = new SimpleStringProperty("시청: —명");
    private final SimpleStringProperty count2 = new SimpleStringProperty("시청: —명");
    private final SimpleStringProperty subs1  = new SimpleStringProperty("구독자: —");
    private final SimpleStringProperty subs2  = new SimpleStringProperty("구독자: —");
    private final SimpleStringProperty ch1Name = new SimpleStringProperty("");
    private final SimpleStringProperty ch2Name = new SimpleStringProperty("");
    private final SimpleStringProperty ch1Logo = new SimpleStringProperty("");
    private final SimpleStringProperty ch2Logo = new SimpleStringProperty("");
    private final SimpleStringProperty v1Title = new SimpleStringProperty(" ");
    private final SimpleStringProperty v2Title = new SimpleStringProperty(" ");

    public void start(String apiKey, String v1, String v2) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.v1 = v1 == null ? "" : v1.trim();
        this.v2 = v2 == null ? "" : v2.trim();

        loadVideoChannelAndSubs(this.v1, ch1Name, ch1Logo, v1Title, subs1);
        loadVideoChannelAndSubs(this.v2, ch2Name, ch2Logo, v2Title, subs2);

        refreshAll();
        sch.scheduleAtFixedRate(this::refreshAll, 2, 2, TimeUnit.SECONDS);
    }

    public void stop() { sch.shutdownNow(); }

    private void refreshAll() {
        refreshOne(v1, count1);
        refreshOne(v2, count2);
    }

    private void refreshOne(String videoId, SimpleStringProperty out) {
        fetchConcurrentViewersAPI(videoId)
                .thenCompose(opt -> opt.isPresent() ? CompletableFuture.completedFuture(opt)
                        : fetchConcurrentViewersInnertube(videoId)
                                .thenCompose(opt2 -> opt2.isPresent()
                                        ? CompletableFuture.completedFuture(opt2)
                                        : scrapeWatchPageWatchersJSON(videoId)))
                .exceptionally(ex -> { System.err.println("[viewers] "+ex); return Optional.empty(); })
                .thenAccept(opt -> Platform.runLater(() -> {
                    String value = opt.map(nf::format).orElse("—");
                    out.set("시청: " + value + "명");
                }));
    }

    // ===== Viewers =====

    private CompletableFuture<Optional<Long>> fetchConcurrentViewersAPI(String videoId) {
        if (apiKey.isEmpty() || videoId.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        String u = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id="
                + url(videoId) + "&key=" + url(apiKey);
        return httpGET(u).thenApply(body -> {
            try {
                JsonNode item = om.readTree(body).path("items").path(0);
                String s = item.path("liveStreamingDetails").path("concurrentViewers").asText("");
                if (!s.isEmpty()) return Optional.of(Long.parseLong(s));
            } catch (Exception ignore) {}
            return Optional.empty();
        });
    }

    private CompletableFuture<Optional<Long>> scrapeWatchPageWatchersJSON(String videoId) {
        String u = "https://www.youtube.com/watch?v=" + url(videoId) + "&hl=ko";
        return httpGET(u, true).thenApply(html -> {
            Map<String,Object> dict = YouTubeParsers.extractJSONDict(html, "ytInitialPlayerResponse");
            if (dict != null) {
                Integer n = YouTubeParsers.findWatchingNowCount(dict);
                if (n != null) return Optional.of(n.longValue());
            }
            dict = YouTubeParsers.extractJSONDict(html, "ytInitialData");
            if (dict != null) {
                Integer n = YouTubeParsers.findWatchingNowCount(dict);
                if (n != null) return Optional.of(n.longValue());
            }
            return Optional.empty();
        });
    }

    // ===== Meta + Subscribers =====

    private void loadVideoChannelAndSubs(String videoId,
                                         SimpleStringProperty chNameOut,
                                         SimpleStringProperty chLogoOut,
                                         SimpleStringProperty vTitleOut,
                                         SimpleStringProperty subsOut) {
        if (videoId == null || videoId.isEmpty()) return;

        if (!apiKey.isEmpty()) {
            String vUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id="+url(videoId)+"&key="+url(apiKey);
            httpGET(vUrl).thenAccept(vBody -> {
                try {
                    JsonNode snip = om.readTree(vBody).path("items").path(0).path("snippet");
                    if (!snip.isMissingNode()) {
                        String chId = snip.path("channelId").asText("");
                        String chTitle = snip.path("channelTitle").asText("");
                        String vTitle = snip.path("title").asText(" ");
                        Platform.runLater(() -> { chNameOut.set(chTitle); vTitleOut.set(vTitle); });

                        String c1 = "https://www.googleapis.com/youtube/v3/channels?part=snippet&id="+url(chId)+"&key="+url(apiKey);
                        httpGET(c1).thenAccept(cBody -> {
                            try {
                                JsonNode tn = om.readTree(cBody).path("items").path(0).path("snippet").path("thumbnails");
                                String logo = tn.path("high").path("url").asText(
                                        tn.path("medium").path("url").asText(
                                                tn.path("default").path("url").asText("")));
                                if (!logo.isEmpty()) Platform.runLater(() -> chLogoOut.set(logo));
                            } catch (Exception ignore) {}
                        });

                        String c2 = "https://www.googleapis.com/youtube/v3/channels?part=statistics&id="+url(chId)+"&key="+url(apiKey);
                        httpGET(c2).thenAccept(sBody -> {
                            try {
                                JsonNode stats = om.readTree(sBody).path("items").path(0).path("statistics");
                                if (stats.path("hiddenSubscriberCount").asBoolean(false)) {
                                    Platform.runLater(() -> subsOut.set("구독자: 비공개"));
                                } else {
                                    String sc = stats.path("subscriberCount").asText("");
                                    if (!sc.isEmpty()) {
                                        String pretty = nf.format(Long.parseLong(sc));
                                        Platform.runLater(() -> subsOut.set("구독자: "+pretty+"명"));
                                        return;
                                    }
                                    fetchSubscribersViaInnertube(chId).thenAccept(txt -> {
                                        if (txt != null) Platform.runLater(() -> subsOut.set(txt));
                                        else scrapeSubscribersFromChannelURLs("https://www.youtube.com/channel/"+chId, chId)
                                                .thenAccept(txt2 -> { if (txt2 != null) Platform.runLater(() -> subsOut.set(txt2)); });
                                    });
                                }
                            } catch (Exception ignore) {}
                        });
                        return;
                    }
                } catch (Exception ignore) {}
                fallbackOnly(videoId, chNameOut, chLogoOut, vTitleOut, subsOut);
            }).exceptionally(ex -> { fallbackOnly(videoId, chNameOut, chLogoOut, vTitleOut, subsOut); return null; });
        } else {
            fallbackOnly(videoId, chNameOut, chLogoOut, vTitleOut, subsOut);
        }
    }

    private void fallbackOnly(String videoId,
                              SimpleStringProperty chNameOut,
                              SimpleStringProperty chLogoOut,
                              SimpleStringProperty vTitleOut,
                              SimpleStringProperty subsOut) {
        fetchChannelMetaFallback(videoId).thenAccept(meta -> {
            if (meta != null) {
                Platform.runLater(() -> {
                    chNameOut.set(meta.title);
                    if (meta.logoUrl != null) chLogoOut.set(meta.logoUrl);
                });
            }
        });
        fetchVideoOEmbed(videoId).thenAccept(t -> Platform.runLater(() -> vTitleOut.set(t == null ? " " : t)));
        fetchAuthorURLFromVideo(videoId).thenCompose(chURL -> resolveChannelId(chURL).thenCompose(uc -> {
            if (uc != null) return fetchSubscribersViaInnertube(uc)
                    .thenApply(txt -> txt != null ? txt : null)
                    .thenCompose(txt -> txt != null ? CompletableFuture.completedFuture(txt)
                            : scrapeSubscribersFromChannelURLs(chURL == null ? "" : chURL, uc));
            return scrapeSubscribersFromChannelURLs(chURL == null ? "" : chURL, null);
        })).thenAccept(txt -> { if (txt != null) Platform.runLater(() -> subsOut.set(txt)); });
    }

    private CompletableFuture<String> fetchSubscribersViaInnertube(String channelId) {
        String ch = "https://www.youtube.com/channel/"+channelId+"?hl=ko";
        return httpGET(ch, true).thenCompose(html -> {
            YouTubeParsers.YtCfg cfg = YouTubeParsers.extractYtCfgKeys(html);
            if (cfg == null) return CompletableFuture.completedFuture(null);
            try {
                String url = "https://www.youtube.com/youtubei/v1/browse?key="+cfg.apiKey;
                Map<String,Object> body = Map.of(
                        "context", Map.of("client", Map.of(
                                "clientName", "WEB",
                                "clientVersion", cfg.clientVersion,
                                "hl","ko","gl","KR"
                        )),
                        "browseId", channelId
                );
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type","application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(om.writeValueAsBytes(body)));
                DEFAULT_HEADERS.forEach(b::header);

                return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        // ⬇︎ BUGFIX: checked exception을 람다 내부에서 처리
                        .thenApply(bodyStr -> {
                            try { return om.readTree(bodyStr); }
                            catch (Exception e) { return null; }
                        })
                        .thenApply(node -> node == null ? null : YouTubeParsers.findSubscriberText(node))
                        .thenApply(txt -> YouTubeParsers.formatSubscribers(txt, nf));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(null);
            }
        }).thenApply(pretty -> pretty == null ? null : "구독자: " + pretty + "명");
    }

    private CompletableFuture<String> scrapeSubscribersFromChannelURLs(String baseChannelURL, String preferChannelId) {
        List<String> urls = new ArrayList<>();
        if (baseChannelURL != null && !baseChannelURL.isEmpty()) {
            urls.add(baseChannelURL + (baseChannelURL.contains("?")? "" : "?hl=ko"));
            urls.add(baseChannelURL + (baseChannelURL.endsWith("/")? "" : "/") + "about?hl=ko");
        }
        if (preferChannelId != null && !preferChannelId.isEmpty()) {
            urls.add("https://www.youtube.com/channel/"+preferChannelId+"?hl=ko");
            urls.add("https://www.youtube.com/channel/"+preferChannelId+"/about?hl=ko");
        }
        CompletableFuture<String> any = new CompletableFuture<>();
        for (String u : urls) {
            httpGET(u, true).thenApply(html -> {
                Map<String,Object> dict = YouTubeParsers.extractJSONDict(html, "ytInitialData");
                if (dict != null) {
                    String txt = YouTubeParsers.findSubscriberText(dict);
                    String pretty = YouTubeParsers.formatSubscribers(txt, nf);
                    if (pretty != null) any.complete("구독자: "+pretty+"명");
                }
                if (!any.isDone()) {
                    String raw = YouTubeParsers.extractSubscriberTextFromHTML(html);
                    String pretty = YouTubeParsers.formatSubscribers(raw, nf);
                    if (pretty != null) any.complete("구독자: "+pretty+"명");
                    else if (html.contains("구독자 비공개") || html.toLowerCase().contains("subscribers hidden"))
                        any.complete("구독자: 비공개");
                }
                return null;
            });
        }
        sch.schedule(() -> { if (!any.isDone()) any.complete(null); }, 5, TimeUnit.SECONDS);
        return any;
    }

    private CompletableFuture<String> fetchAuthorURLFromVideo(String videoId) {
        String u = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v="+url(videoId)+"&format=json";
        return httpGET(u).thenApply(body -> {
            try { return om.readTree(body).path("author_url").asText(""); }
            catch (Exception e) { return ""; }
        });
    }

    private CompletableFuture<String> resolveChannelId(String channelURL) {
        if (channelURL == null || channelURL.isEmpty()) return CompletableFuture.completedFuture(null);
        try {
            URI uri = URI.create(channelURL);
            String path = uri.getPath();
            if (path != null) {
                String[] parts = path.split("/");
                if (parts.length >= 3 && "channel".equals(parts[1]) && parts[2].startsWith("UC"))
                    return CompletableFuture.completedFuture(parts[2]);
            }
        } catch (Exception ignore) {}
        String u = channelURL + (channelURL.contains("?") ? "" : "?hl=ko");
        return httpGET(u, true).thenApply(YouTubeParsers::extractChannelIdFromHTML);
    }

    private static final class ChannelMeta { final String title; final String logoUrl; ChannelMeta(String t, String l){title=t;logoUrl=l;} }
    private CompletableFuture<ChannelMeta> fetchChannelMetaFallback(String videoId) {
        String u = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v="+url(videoId)+"&format=json";
        return httpGET(u).thenCompose(body -> {
            try {
                JsonNode o = om.readTree(body);
                String chTitle = o.path("author_name").asText("");
                String chUrl = o.path("author_url").asText("") + "?hl=ko";
                return httpGET(chUrl, true).thenApply(html -> new ChannelMeta(chTitle, YouTubeParsers.extractOgImage(html)));
            } catch (Exception e) { return CompletableFuture.completedFuture(null); }
        });
    }

    private CompletableFuture<String> fetchVideoOEmbed(String videoId) {
        String u = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v="+url(videoId)+"&format=json";
        return httpGET(u).thenApply(body -> {
            try { return om.readTree(body).path("title").asText(" "); }
            catch (Exception e) { return " "; }
        });
    }

    private CompletableFuture<String> httpGET(String url) { return httpGET(url, false); }

    private CompletableFuture<String> httpGET(String url, boolean withConsentHeaders) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (withConsentHeaders) DEFAULT_HEADERS.forEach(b::header);
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }

    private static String url(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    public ReadOnlyStringProperty count1Property() { return count1; }
    public ReadOnlyStringProperty count2Property() { return count2; }
    public ReadOnlyStringProperty subs1Property()  { return subs1; }
    public ReadOnlyStringProperty subs2Property()  { return subs2; }
    public ReadOnlyStringProperty channel1NameProperty() { return ch1Name; }
    public ReadOnlyStringProperty channel2NameProperty() { return ch2Name; }
    public ReadOnlyStringProperty channel1LogoUrlProperty() { return ch1Logo; }
    public ReadOnlyStringProperty channel2LogoUrlProperty() { return ch2Logo; }
    public ReadOnlyStringProperty video1TitleProperty() { return v1Title; }
    public ReadOnlyStringProperty video2TitleProperty() { return v2Title; }

    private CompletableFuture<Optional<Long>> fetchConcurrentViewersInnertube(String videoId) {
        if (videoId == null || videoId.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        String payload = "{" +
                "\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20240729.01.00\",\"hl\":\"ko\",\"gl\":\"KR\"}}," +
                "\"videoId\":\"" + videoId + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://www.youtube.com/youtubei/v1/player?key=" + INNERTUBE_API_KEY))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        DEFAULT_HEADERS.forEach(builder::header);
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new RuntimeException("HTTP " + resp.statusCode());
                    }
                    try {
                        JsonNode root = om.readTree(resp.body());
                        Optional<Long> direct = findConcurrentViewCount(root);
                        if (direct.isPresent()) return direct;
                        Integer alt = YouTubeParsers.findWatchingNowCount(root);
                        if (alt != null) return Optional.of(alt.longValue());
                    } catch (Exception ignore) {}
                    return Optional.empty();
                });
    }

    private Optional<Long> findConcurrentViewCount(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return Optional.empty();
        }
        if (node.isObject()) {
            JsonNode direct = node.get("concurrentViewCount");
            if (direct != null) {
                String txt = direct.asText("");
                if (!txt.isBlank()) {
                    try { return Optional.of(Long.parseLong(txt.replace(",", ""))); }
                    catch (NumberFormatException ignore) {}
                }
            }
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Optional<Long> nested = findConcurrentViewCount(it.next().getValue());
                if (nested.isPresent()) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<Long> nested = findConcurrentViewCount(child);
                if (nested.isPresent()) return nested;
            }
        }
        return Optional.empty();
    }

}
