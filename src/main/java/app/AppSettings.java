// File: src/main/java/app/AppSettings.java
package app;

import java.util.prefs.Preferences;

/** WHY: OS 설정(레지스트리/Preferences)에 간단히 저장, JDK만으로 동작 */
public final class AppSettings {
    private static final String NODE = "ytmultichat_javafx";
    private static final String K_API = "apiKey";
    private static final String K_CH1 = "channel1";
    private static final String K_CH2 = "channel2";
    private static final String K_V1  = "video1";
    private static final String K_V2  = "video2";

    private final Preferences prefs;
    public String apiKey = "";
    public String channel1 = "";
    public String channel2 = "";
    public String video1 = "";
    public String video2 = "";

    private AppSettings(Preferences p) { this.prefs = p; }

    public static AppSettings load() {
        Preferences p = Preferences.userRoot().node(NODE);
        AppSettings s = new AppSettings(p);
        s.apiKey = p.get(K_API, "");
        s.channel1 = p.get(K_CH1, "");
        s.channel2 = p.get(K_CH2, "");
        s.video1 = p.get(K_V1, "");
        s.video2 = p.get(K_V2, "");
        return s;
    }

    public void save() {
        prefs.put(K_API, safe(apiKey));
        prefs.put(K_CH1, safe(channel1));
        prefs.put(K_CH2, safe(channel2));
        prefs.put(K_V1,  safe(video1));
        prefs.put(K_V2,  safe(video2));
        try { prefs.flush(); } catch (Exception ignored) {}
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
