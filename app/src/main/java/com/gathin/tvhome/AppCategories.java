package com.gathin.tvhome;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Owner categories override Android metadata; familiar media package hints help older TV apps. */
final class AppCategories {
    private AppCategories() {}
    static Map<String, Integer> parse(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return result;
        if (text.length() > 32768) throw new IllegalArgumentException("Categories too large");
        try {
            JSONObject json = new JSONObject(text);
            if (json.length() > 128) throw new IllegalArgumentException("Too many categories");
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String pkg = keys.next(); Object value = json.get(pkg);
                if (!LibraryApps.validPackage(pkg) || !(value instanceof Integer) || (Integer) value < 1 || (Integer) value > 4)
                    throw new IllegalArgumentException("Invalid category");
                result.put(pkg, (Integer) value);
            }
            return result;
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid categories", e); }
    }
    static int resolve(String pkg, int platform, Map<String, Integer> owner) {
        if (owner.containsKey(pkg)) return owner.get(pkg);
        if (platform == 0) return 3;
        if (platform == 1) return 2;
        if (platform == 2) return 1;
        String name = pkg.toLowerCase(Locale.ROOT);
        for (String word : new String[]{"youtube", "netflix", "kinopoisk", "ivi", "okko", "wink", "vlc", "kodi", "video"}) if (name.contains(word)) return 1;
        for (String word : new String[]{"music", "spotify", "audio", "radio"}) if (name.contains(word)) return 2;
        if (name.contains("game")) return 3;
        return 4;
    }
}
