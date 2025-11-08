// File: src/main/java/app/LiveStreamService.java
package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.YouTubeParsers;

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

    public CompletableFuture<List<LiveStream>> fetchLiveStreams(String apiKey, String channelIdOrHandle) {
        String raw = safe(channelIdOrHandle);
        if (raw.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String key = safe(apiKey);
        return resolveChannel(key, raw).thenCompose(ref -> {
            if (ref == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            CompletableFuture<List<LiveStream>> viaApi;
            if (key.isEmpty() || ref.channelId().isBlank()) {
                viaApi = CompletableFuture.completedFuture(Collections.emptyList());
            } else {
                viaApi = fetchViaApi(key, ref.channelId()).exceptionally(ex -> Collections.emptyList());
            }
            return viaApi.thenCompose(list -> {
                if (!list.isEmpty()) {
                    return CompletableFuture.completedFuture(list);
                }
                return fetchViaScraping(ref).exceptionally(ex -> Collections.emptyList());
            });
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

    private CompletableFuture<List<LiveStream>> fetchViaScraping(ChannelRef ref) {
        List<String> paths = ref.scrapePaths();
        if (paths.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return fetchViaScraping(paths, 0);
    }

    private CompletableFuture<List<LiveStream>> fetchViaScraping(List<String> paths, int index) {
        if (index >= paths.size()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String path = paths.get(index);
        String url = "https://www.youtube.com/" + path + "/live?hl=ko";
        return httpGET(url)
                .thenApply(this::parseLiveFromHtml)
                .exceptionally(ex -> Collections.emptyList())
                .thenCompose(list -> {
                    if (!list.isEmpty() || index + 1 >= paths.size()) {
                        return CompletableFuture.completedFuture(list);
                    }
                    return fetchViaScraping(paths, index + 1);
                });
    }

    private List<LiveStream> parseLiveFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, LiveStream> dedup = new LinkedHashMap<>();

        Map<String, Object> player = YouTubeParsers.extractJSONDict(html, "ytInitialPlayerResponse");
        if (player != null) {
            Object vd = player.get("videoDetails");
            if (vd instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) vd;
                String videoId = stringValue(map.get("videoId"));
                if (!videoId.isEmpty()) {
                    String title = defaultString(stringValue(map.get("title")), "(제목 없음)");
                    dedup.putIfAbsent(videoId, new LiveStream(videoId, title, "라이브 페이지에서 감지됨"));
                }
            }
        }

        Map<String, Object> data = YouTubeParsers.extractJSONDict(html, "ytInitialData");
        if (data != null) {
            List<Map<String, Object>> renderers = new ArrayList<>();
            collectVideoRenderers(data, renderers);
            for (Map<String, Object> renderer : renderers) {
                if (!isLiveRenderer(renderer)) {
                    continue;
                }
                String videoId = stringValue(renderer.get("videoId"));
                if (videoId.isEmpty()) {
                    continue;
                }
                String title = defaultString(extractText(renderer.get("title")), "(제목 없음)");
                String subtitle = deriveSubtitle(renderer);
                dedup.putIfAbsent(videoId, new LiveStream(videoId, title, subtitle));
            }
        }

        if (dedup.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(dedup.values());
    }

    private void collectVideoRenderers(Object node, List<Map<String, Object>> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> childMap) {
                    if ("videoRenderer".equals(entry.getKey()) || "gridVideoRenderer".equals(entry.getKey())) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> renderer = (Map<String, Object>) childMap;
                        out.add(renderer);
                    } else if ("richItemRenderer".equals(entry.getKey())) {
                        collectVideoRenderers(childMap, out);
                    }
                }
                collectVideoRenderers(value, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                collectVideoRenderers(value, out);
            }
        }
    }

    private boolean isLiveRenderer(Map<String, Object> renderer) {
        if (renderer == null) {
            return false;
        }
        if (renderer.containsKey("upcomingEventData")) {
            return true;
        }
        if (containsLiveBadge(renderer.get("badges"))) {
            return true;
        }
        if (containsLiveBadge(renderer.get("ownerBadges"))) {
            return true;
        }
        if (containsLiveOverlay(renderer.get("thumbnailOverlays"))) {
            return true;
        }
        if (containsLiveText(renderer.get("viewCountText"))) {
            return true;
        }
        if (containsLiveText(renderer.get("shortViewCountText"))) {
            return true;
        }
        return false;
    }

    private boolean containsLiveBadge(Object badges) {
        if (!(badges instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object badge = map.get("metadataBadgeRenderer");
                if (badge instanceof Map<?, ?> meta) {
                    String style = stringValue(meta.get("style"));
                    if ("BADGE_STYLE_TYPE_LIVE_NOW".equals(style) || "BADGE_STYLE_TYPE_UPCOMING".equals(style)) {
                        return true;
                    }
                    String label = extractText(meta.get("label"));
                    if (containsLiveKeyword(label)) {
                        return true;
                    }
                    String tooltip = stringValue(meta.get("tooltip"));
                    if (containsLiveKeyword(tooltip)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsLiveOverlay(Object overlays) {
        if (!(overlays instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object overlay = map.get("thumbnailOverlayTimeStatusRenderer");
                if (overlay instanceof Map<?, ?> status) {
                    String style = stringValue(status.get("style"));
                    if ("LIVE".equalsIgnoreCase(style) || "LIVE_NOW".equalsIgnoreCase(style) || "UPCOMING".equalsIgnoreCase(style)) {
                        return true;
                    }
                    String text = extractText(status.get("text"));
                    if (containsLiveKeyword(text)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsLiveText(Object candidate) {
        String text = extractText(candidate);
        return containsLiveKeyword(text);
    }

    private boolean containsLiveKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("live") || lower.contains("실시간") || lower.contains("시청 중") || lower.contains("방송 예정");
    }

    private String deriveSubtitle(Map<String, Object> renderer) {
        Object upcoming = renderer.get("upcomingEventData");
        if (upcoming instanceof Map<?, ?> map) {
            String when = stringValue(map.get("startTime"));
            if (!when.isEmpty()) {
                try {
                    long epoch = Long.parseLong(when);
                    if (epoch > 0L) {
                        return "예정 " + HUMAN_TIME.format(Instant.ofEpochSecond(epoch));
                    }
                } catch (NumberFormatException ignore) {
                    // fall back to text extraction
                }
            }
            String text = extractText(map.get("label"));
            if (!text.isEmpty()) {
                return text;
            }
        }

        String viewText = extractText(renderer.get("viewCountText"));
        if (!viewText.isEmpty()) {
            return viewText;
        }

        String shortText = extractText(renderer.get("shortViewCountText"));
        if (!shortText.isEmpty()) {
            return shortText;
        }

        String published = extractText(renderer.get("publishedTimeText"));
        if (!published.isEmpty()) {
            return published;
        }

        return "라이브 페이지에서 감지됨";
    }

    private String extractText(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Map<?, ?> map) {
            Object simple = map.get("simpleText");
            if (simple instanceof String s) {
                return s;
            }
            Object text = map.get("text");
            if (text instanceof String s) {
                return s;
            }
            Object runs = map.get("runs");
            if (runs instanceof List<?>) {
                StringBuilder sb = new StringBuilder();
                for (Object run : (List<?>) runs) {
                    String chunk = extractText(run);
                    if (!chunk.isEmpty()) {
                        sb.append(chunk);
                    }
                }
                return sb.toString();
            }
        } else if (value instanceof List<?>) {
            StringBuilder sb = new StringBuilder();
            for (Object run : (List<?>) value) {
                String chunk = extractText(run);
                if (!chunk.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(chunk);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value, "");
    }

    private String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
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

    private CompletableFuture<ChannelRef> resolveChannel(String apiKey, String rawInput) {
        ChannelRef parsed = parseChannelInput(rawInput);
        if (parsed.channelId().isBlank() && !apiKey.isBlank()) {
            CompletableFuture<String> fromHandle = parsed.handle().isBlank()
                    ? CompletableFuture.completedFuture("")
                    : resolveHandleViaApi(apiKey, parsed.handle()).exceptionally(ex -> "");
            return fromHandle.thenApply(handleId -> {
                if (!parsed.channelId().isBlank()) {
                    return parsed;
                }
                String channelId = handleId.isBlank() ? parsed.channelId() : handleId;
                return new ChannelRef(channelId, parsed.handle(), parsed.customPath());
            });
        }
        return CompletableFuture.completedFuture(parsed);
    }

    private CompletableFuture<String> resolveHandleViaApi(String apiKey, String handle) {
        String clean = handle.startsWith("@") ? handle.substring(1) : handle;
        if (clean.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        String url = "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle="
                + url(clean) + "&key=" + url(apiKey);
        return httpGET(url).thenApply(body -> {
            try {
                JsonNode root = om.readTree(body);
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    return "";
                }
                JsonNode first = items.get(0);
                return first.path("id").asText("");
            } catch (Exception e) {
                return "";
            }
        });
    }

    private ChannelRef parseChannelInput(String rawInput) {
        String trimmed = safe(rawInput);
        if (trimmed.isEmpty()) {
            return ChannelRef.EMPTY;
        }

        String channelId = "";
        String handle = extractHandle(trimmed);
        String custom = "";

        if (looksLikeChannelId(trimmed)) {
            channelId = trimmed;
        }

        String normalized = trimmed;
        int q = normalized.indexOf('?');
        if (q >= 0) normalized = normalized.substring(0, q);
        int hash = normalized.indexOf('#');
        if (hash >= 0) normalized = normalized.substring(0, hash);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            int idx = normalized.indexOf("//");
            normalized = idx >= 0 ? normalized.substring(idx + 2) : normalized;
        }
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        if (normalized.startsWith("m.youtube.com")) {
            normalized = normalized.substring("m.youtube.com".length());
        } else if (normalized.startsWith("youtube.com")) {
            normalized = normalized.substring("youtube.com".length());
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (channelId.isEmpty() && normalized.startsWith("channel/")) {
            channelId = sliceIdentifier(normalized.substring("channel/".length()));
        }
        if (handle.isEmpty() && normalized.startsWith("@")) {
            handle = extractHandle(normalized);
        }
        if (handle.isEmpty()) {
            int idx = normalized.indexOf("/@");
            if (idx >= 0) {
                handle = extractHandle(normalized.substring(idx + 1));
            }
        }
        if (custom.isEmpty() && normalized.startsWith("c/")) {
            custom = "c/" + sliceIdentifier(normalized.substring(2));
        }
        if (custom.isEmpty() && normalized.startsWith("user/")) {
            custom = "user/" + sliceIdentifier(normalized.substring(5));
        }

        if (channelId.isEmpty() && handle.isEmpty() && isHandleCandidate(trimmed)) {
            handle = trimmed.startsWith("@") ? trimmed : "@" + trimmed;
        }

        return new ChannelRef(channelId, handle, custom);
    }

    private static String sliceIdentifier(String value) {
        String v = value == null ? "" : value;
        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);
        return v.trim();
    }

    private static boolean looksLikeChannelId(String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.startsWith("UC") && v.length() >= 20 && v.length() <= 40;
    }

    private static boolean isHandleCandidate(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.startsWith("@")) {
            v = v.substring(1);
        }
        if (v.isEmpty()) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static String extractHandle(String text) {
        if (text == null) {
            return "";
        }
        int at = text.indexOf('@');
        if (at < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("@");
        for (int i = at + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.length() > 1 ? sb.toString() : "";
    }

    private record ChannelRef(String channelId, String handle, String customPath) {
        private static final ChannelRef EMPTY = new ChannelRef("", "", "");

        List<String> scrapePaths() {
            if (channelId.isBlank() && handle.isBlank() && customPath.isBlank()) {
                return Collections.emptyList();
            }
            List<String> paths = new ArrayList<>();
            if (!channelId.isBlank()) {
                paths.add("channel/" + channelId);
            }
            if (!handle.isBlank()) {
                paths.add(handle.startsWith("@") ? handle : "@" + handle);
            }
            if (!customPath.isBlank()) {
                String p = customPath.startsWith("/") ? customPath.substring(1) : customPath;
                paths.add(p);
            }
            List<String> dedup = new ArrayList<>();
            for (String p : paths) {
                if (!dedup.contains(p)) {
                    dedup.add(p);
                }
            }
            return dedup;
        }
    }

    public record LiveStream(String videoId, String title, String subtitle) {
        public String videoId() { return videoId; }
        public String title() { return title; }
        public String subtitle() { return subtitle; }

        @Override public String toString() { return title; }
    }
}
