// File: src/main/java/app/YouTubeParsers.java
package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeParsers {
    private YouTubeParsers(){}

    public static Map<String,Object> extractJSONDict(String html, String anchor) {
        if (html == null) return null;
        int pos = html.indexOf(anchor);
        if (pos < 0) return null;
        int start = html.indexOf('{', pos);
        if (start < 0) return null;
        int level = 0, end = -1;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') level++;
            else if (c == '}') { level--; if (level == 0) { end = i; break; } }
        }
        if (end < 0) return null;
        String jsonText = html.substring(start, end+1);
        try {
            ObjectMapper om = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String,Object> obj = om.readValue(jsonText, Map.class);
            return obj;
        } catch (Exception e) { return null; }
    }

    public static Integer findWatchingNowCount(Object any) { return findWatchingNowCount(any, null); }

    public static Integer findWatchingNowCount(Object any, String targetVideoId) {
        if (any instanceof Map) {
            Map<?,?> m = (Map<?,?>) any;
            if (!matchesVideo(targetVideoId, m)) return null;

            Integer direct = parseConcurrent(m.get("concurrentViewCount"));
            if (direct != null) return direct;

            Object vvr = m.get("videoViewCountRenderer");
            if (vvr instanceof Map) {
                Integer n = extractCountFromViewCount((Map<?, ?>) vvr);
                if (n != null) return n;
            }

            Object primary = m.get("videoPrimaryInfoRenderer");
            if (primary != null) {
                Integer n = findWatchingNowCount(primary, targetVideoId);
                if (n != null) return n;
            }

            Object liveDetails = m.get("liveBroadcastDetails");
            if (liveDetails != null) {
                Integer n = findWatchingNowCount(liveDetails, targetVideoId);
                if (n != null) return n;
            }

            for (Object v : m.values()) {
                Integer n = findWatchingNowCount(v, targetVideoId);
                if (n != null) return n;
            }
        } else if (any instanceof List) {
            for (Object v : (List<?>) any) {
                Integer n = findWatchingNowCount(v, targetVideoId);
                if (n != null) return n;
            }
        } else if (any instanceof JsonNode) {
            JsonNode node = (JsonNode) any;
            if (!matchesVideo(targetVideoId, node)) return null;

            Integer direct = parseConcurrent(node.get("concurrentViewCount"));
            if (direct != null) return direct;

            JsonNode vvr = node.get("videoViewCountRenderer");
            if (vvr != null) {
                Integer n = extractCountFromViewCount(vvr);
                if (n != null) return n;
            }

            JsonNode primary = node.get("videoPrimaryInfoRenderer");
            if (primary != null) {
                Integer n = findWatchingNowCount(primary, targetVideoId);
                if (n != null) return n;
            }

            JsonNode liveDetails = node.get("liveBroadcastDetails");
            if (liveDetails != null && !liveDetails.isMissingNode()) {
                Integer n = findWatchingNowCount(liveDetails, targetVideoId);
                if (n != null) return n;
            }

            if (node.isArray()) for (JsonNode ch : node) {
                Integer n = findWatchingNowCount(ch, targetVideoId);
                if (n != null) return n;
            }

            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Integer n = findWatchingNowCount(it.next().getValue(), targetVideoId);
                if (n != null) return n;
            }
        }
        return null;
    }

    private static Integer extractCountFromViewCount(Map<?,?> vvr) {
        Object v = vvr.get("viewCount");
        Integer n = extractFromViewCountValue(v);
        if (n != null) return n;
        v = vvr.get("shortViewCount");
        n = extractFromViewCountValue(v);
        if (n != null) return n;
        v = vvr.get("accessibility");
        if (v instanceof Map) {
            Object ad = ((Map<?, ?>) v).get("accessibilityData");
            if (ad instanceof Map) {
                Object label = ((Map<?, ?>) ad).get("label");
                if (label instanceof String) {
                    Integer mm = parseWatchingText((String) label);
                    if (mm != null) return mm;
                }
            }
        }
        return null;
    }
    private static Integer extractCountFromViewCount(JsonNode v) {
        if (v == null || v.isMissingNode()) return null;
        JsonNode simple = v.get("simpleText");
        if (simple != null && simple.isTextual()) {
            Integer mm = parseWatchingText(simple.asText());
            if (mm != null) return mm;
        }
        JsonNode runs = v.get("runs");
        if (runs != null && runs.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : runs) {
                JsonNode t = r.get("text");
                if (t != null && t.isTextual()) sb.append(t.asText());
            }
            Integer mm = parseWatchingText(sb.toString());
            if (mm != null) return mm;
        }
        return null;
    }
    private static Integer extractFromViewCountValue(Object v) {
        if (v instanceof Map) {
            Map<?,?> mv = (Map<?, ?>) v;
            Object s = mv.get("simpleText");
            if (s instanceof String) {
                Integer mm = parseWatchingText((String) s); if (mm != null) return mm;
            }
            Object runs = mv.get("runs");
            if (runs instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object o : (List<?>) runs) {
                    if (o instanceof Map) {
                        Object t = ((Map<?, ?>) o).get("text");
                        if (t instanceof String) sb.append((String) t);
                    }
                }
                Integer mm = parseWatchingText(sb.toString()); if (mm != null) return mm;
            }
        } else if (v instanceof JsonNode) {
            return extractCountFromViewCount((JsonNode) v);
        }
        return null;
    }

    private static final Pattern NUM_SUF = Pattern.compile("([0-9][0-9\\.,]*)\\s*(억|만|천|[KMB])?");
    private static Integer parseWatchingText(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.replace('\u00A0',' ').toLowerCase(Locale.ROOT);
        if (!(s.contains("watching") || s.contains("viewing") || s.contains("시청"))) return null;
        Matcher m = NUM_SUF.matcher(raw);
        if (m.find()) {
            Long v = normalizeNumber(m.group(1), m.group(2));
            return v == null ? null : v.intValue();
        }
        return null;
    }

    private static Integer parseConcurrent(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) {
            long val = ((Number) raw).longValue();
            if (val <= 0) return null;
            return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
        }
        if (raw instanceof String) {
            String txt = ((String) raw).trim();
            if (txt.isEmpty()) return null;
            String digits = txt.replaceAll("[^0-9.]", "");
            if (digits.isEmpty()) return null;
            try {
                double val = Double.parseDouble(digits);
                if (val <= 0d) return null;
                long rounded = Math.round(val);
                return rounded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static boolean matchesVideo(String targetVideoId, Map<?,?> node) {
        if (targetVideoId == null || targetVideoId.isEmpty() || node == null) return true;
        Object vid = node.get("videoId");
        if (vid instanceof String && !targetVideoId.equals(vid)) return false;
        Object endpoint = node.get("watchEndpoint");
        if (endpoint instanceof Map) {
            Object id2 = ((Map<?, ?>) endpoint).get("videoId");
            if (id2 instanceof String && !targetVideoId.equals(id2)) return false;
        }
        Object browse = node.get("navigationEndpoint");
        if (browse instanceof Map) {
            Object watch = ((Map<?, ?>) browse).get("watchEndpoint");
            if (watch instanceof Map) {
                Object id3 = ((Map<?, ?>) watch).get("videoId");
                if (id3 instanceof String && !targetVideoId.equals(id3)) return false;
            }
        }
        return true;
    }

    private static boolean matchesVideo(String targetVideoId, JsonNode node) {
        if (targetVideoId == null || targetVideoId.isEmpty() || node == null || node.isMissingNode()) return true;
        JsonNode vid = node.get("videoId");
        if (vid != null && vid.isTextual() && !targetVideoId.equals(vid.asText())) return false;
        JsonNode endpoint = node.get("watchEndpoint");
        if (endpoint != null) {
            JsonNode id2 = endpoint.get("videoId");
            if (id2 != null && id2.isTextual() && !targetVideoId.equals(id2.asText())) return false;
        }
        JsonNode nav = node.get("navigationEndpoint");
        if (nav != null) {
            JsonNode watch = nav.get("watchEndpoint");
            if (watch != null) {
                JsonNode id3 = watch.get("videoId");
                if (id3 != null && id3.isTextual() && !targetVideoId.equals(id3.asText())) return false;
            }
        }
        return true;
    }

    private static Long normalizeNumber(String num, String suffix) {
        if (num == null) return null;
        String t = num.replace(",", "").trim();
        int i=0; while (i<t.length() && (Character.isDigit(t.charAt(i)) || t.charAt(i)=='.')) i++;
        String baseStr = i>0 ? t.substring(0,i) : t;
        double base;
        try { base = Double.parseDouble(baseStr); } catch (Exception e) { return null; }
        if (suffix == null || suffix.isEmpty()) return Math.round(base);
        switch (suffix.toUpperCase(Locale.ROOT)) {
            case "천": return Math.round(base * 1_000d);
            case "만": return Math.round(base * 10_000d);
            case "억": return Math.round(base * 100_000_000d);
            case "K":  return Math.round(base * 1_000d);
            case "M":  return Math.round(base * 1_000_000d);
            case "B":  return Math.round(base * 1_000_000_000d);
            default:   return Math.round(base);
        }
    }

    public static String findSubscriberText(Object any) {
        if (any instanceof Map) {
            Map<?,?> m = (Map<?,?>) any;
            String got = extractSubscriberTextValue(m.get("subscriberCountText"));
            if (got != null) return got;
            Object header = m.get("header");
            if (header != null) {
                String s = findSubscriberText(header);
                if (s != null) return s;
            }
            for (Object v : m.values()) {
                String s = findSubscriberText(v);
                if (s != null) return s;
            }
        } else if (any instanceof List) {
            for (Object v : (List<?>) any) {
                String s = findSubscriberText(v);
                if (s != null) return s;
            }
        } else if (any instanceof JsonNode) {
            JsonNode n = (JsonNode) any;
            String got = extractSubscriberTextValue(n.get("subscriberCountText"));
            if (got != null) return got;
            JsonNode header = n.get("header");
            if (header != null) {
                String s = findSubscriberText(header);
                if (s != null) return s;
            }
            Iterator<JsonNode> it = n.elements();
            while (it.hasNext()) {
                String s = findSubscriberText(it.next());
                if (s != null) return s;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
            while (fields.hasNext()) {
                String s = findSubscriberText(fields.next().getValue());
                if (s != null) return s;
            }
        }
        return null;
    }
    private static String extractSubscriberTextValue(Object v) {
        if (v instanceof Map) {
            Object s = ((Map<?, ?>) v).get("simpleText");
            if (s instanceof String) return (String) s;
            Object runs = ((Map<?, ?>) v).get("runs");
            if (runs instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object o : (List<?>) runs) {
                    if (o instanceof Map) {
                        Object t = ((Map<?, ?>) o).get("text");
                        if (t instanceof String) sb.append((String) t);
                    }
                }
                if (sb.length()>0) return sb.toString();
            }
        } else if (v instanceof JsonNode) {
            JsonNode n = (JsonNode) v;
            JsonNode s = n.get("simpleText");
            if (s!=null && s.isTextual()) return s.asText();
            JsonNode runs = n.get("runs");
            if (runs!=null && runs.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode r : runs) {
                    JsonNode t = r.get("text");
                    if (t!=null && t.isTextual()) sb.append(t.asText());
                }
                if (sb.length()>0) return sb.toString();
            }
        }
        return null;
    }

    public static String formatSubscribers(String raw, NumberFormat nf) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.replace('\u00A0',' ')
                .replace("subscribers","")
                .replace("Subscriber","")
                .replace("구독자","")
                .replace("명","")
                .trim();
        Matcher m = NUM_SUF.matcher(s);
        if (m.find()) {
            Long n = normalizeNumber(m.group(1), m.group(2));
            if (n != null) return nf.format(n);
        }
        return null;
    }

    public static String extractOgImage(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    public static String extractChannelIdFromHTML(String html) {
        if (html == null) return null;
        String[] patterns = {
                "\"externalId\"\\s*:\\s*\"(UC[0-9A-Za-z_\\-]{22})\"",
                "\"browseId\"\\s*:\\s*\"(UC[0-9A-Za-z_\\-]{22})\"",
                "href=\"https://www\\.youtube\\.com/channel/(UC[0-9A-Za-z_\\-]{22})\""
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(html);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public static String extractSubscriberTextFromHTML(String html) {
        if (html == null) return null;
        Matcher m1 = Pattern.compile("\"subscriberCountText\"\\s*:\\s*\\{\\s*\"simpleText\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(html);
        if (m1.find()) return m1.group(1);
        Matcher m2 = Pattern.compile("\"subscriberCountText\"\\s*:\\s*\\{\\s*\"runs\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(html);
        if (m2.find()) {
            String runs = m2.group(1);
            Matcher t = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(runs);
            StringBuilder sb = new StringBuilder();
            while (t.find()) sb.append(t.group(1));
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    public static final class YtCfg { public final String apiKey, clientVersion; public YtCfg(String k,String v){apiKey=k;clientVersion=v;} }
    public static YtCfg extractYtCfgKeys(String html) {
        if (html == null) return null;
        String key = first(html, "\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"");
        if (key == null) key = first(html, "innertubeApiKey\\s*:\\s*\"([^\"]+)\"");
        String ver = first(html, "\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"");
        if (ver == null) ver = first(html, "innertubeContextClientVersion\\s*:\\s*\"([^\"]+)\"");
        return (key != null && ver != null) ? new YtCfg(key, ver) : null;
    }
    private static String first(String text, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
