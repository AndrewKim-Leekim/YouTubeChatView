// File: src/main/java/app/LiveStreamService.java
package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Utility service that discovers live broadcasts for a channel.
 */
public final class LiveStreamService {
    private static final Map<String,String> DEFAULT_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36",
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.6,en;q=0.5",
            "Cookie", "CONSENT=YES+cb.20210328-17-p0.en+F+678"
    );

    private static final DateTimeFormatter HUMAN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withLocale(Locale.KOREA)
            .withZone(ZoneId.systemDefault());

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper om = new ObjectMapper();

    public CompletableFuture<List<LiveStream>> fetchLiveStreams(String apiKey, String channelId) {
        String ch = safe(channelId);
        if (ch.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String key = safe(apiKey);
        CompletableFuture<List<LiveStream>> viaApi = key.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptyList())
                : fetchViaApi(key, ch).exceptionally(ex -> Collections.emptyList());
        return viaApi.thenCompose(list -> {
            if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
            return fetchViaScraping(ch).exceptionally(ex -> Collections.emptyList());
        });
    }

    private CompletableFuture<List<LiveStream>> fetchViaApi(String apiKey, String channelId) {
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId="
                + url(channelId) + "&eventType=live&type=video&order=date&maxResults=25&key=" + url(apiKey);
        return httpGET(url).thenCompose(body -> {
            try {
                JsonNode root = om.readTree(body);
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }
                List<String> order = new ArrayList<>();
                Map<String, LiveStream> temp = new LinkedHashMap<>();
                for (JsonNode item : items) {
                    String videoId = item.path("id").path("videoId").asText("");
                    if (videoId.isEmpty()) continue;
                    String title = item.path("snippet").path("title").asText("(제목 없음)");
                    temp.put(videoId, new LiveStream(videoId, title, ""));
                    order.add(videoId);
                }
                if (order.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }
                String videosUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails,snippet&id="
                        + url(String.join(",", order)) + "&key=" + url(apiKey);
                return httpGET(videosUrl).thenApply(body2 -> {
                    try {
                        JsonNode detailsRoot = om.readTree(body2).path("items");
                        for (JsonNode node : detailsRoot) {
                            String vid = node.path("id").asText("");
                            if (vid.isEmpty()) continue;
                            LiveStream current = temp.get(vid);
                            if (current == null) continue;
                            String title = node.path("snippet").path("title").asText(current.title());
                            JsonNode live = node.path("liveStreamingDetails");
                            String actual = live.path("actualStartTime").asText("");
                            String scheduled = live.path("scheduledStartTime").asText("");
                            String subtitle = buildSubtitle(actual, scheduled);
                            temp.put(vid, new LiveStream(vid, title, subtitle));
                        }
                    } catch (Exception ignore) {
                        // keep partial data
                    }
                    List<LiveStream> result = new ArrayList<>();
                    for (String vid : order) {
                        LiveStream ls = temp.get(vid);
                        if (ls != null) result.add(ls);
                    }
                    return result;
                });
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
        });
    }

    private CompletableFuture<List<LiveStream>> fetchViaScraping(String channelId) {
        String url = "https://www.youtube.com/channel/" + url(channelId) + "/live?hl=ko";
        return httpGET(url).thenApply(html -> {
            Map<String, Object> dict = YouTubeParsers.extractJSONDict(html, "ytInitialPlayerResponse");
            if (dict == null) return Collections.emptyList();
            Object vd = dict.get("videoDetails");
            if (!(vd instanceof Map)) return Collections.emptyList();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) vd;
            String videoId = Objects.toString(map.get("videoId"), "");
            if (videoId.isEmpty()) return Collections.emptyList();
            String title = Objects.toString(map.get("title"), "(제목 없음)");
            return List.of(new LiveStream(videoId, title, "라이브 페이지에서 감지됨"));
        });
    }

    private static String buildSubtitle(String actual, String scheduled) {
        try {
            if (actual != null && !actual.isBlank()) {
                return "생중계 시작 " + HUMAN_TIME.format(Instant.parse(actual));
            }
        } catch (Exception ignore) {
        }
        try {
            if (scheduled != null && !scheduled.isBlank()) {
                return "예정 " + HUMAN_TIME.format(Instant.parse(scheduled));
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private CompletableFuture<String> httpGET(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        DEFAULT_HEADERS.forEach(builder::header);
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    int status = resp.statusCode();
                    if (status >= 200 && status < 300) return resp.body();
                    throw new RuntimeException("HTTP " + status);
                });
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String url(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    public record LiveStream(String videoId, String title, String subtitle) {
        public String videoId() { return videoId; }
        public String title() { return title; }
        public String subtitle() { return subtitle; }

        @Override public String toString() { return title; }
    }
}
