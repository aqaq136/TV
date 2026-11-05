package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Versioned, bounded data format. Archive names are never used as filesystem paths. */
final class BackupArchive {
    static final int MAX_ARCHIVE = 6 * 1024 * 1024;
    static final int MAX_IMAGE = 4 * 1024 * 1024;
    private static final int MAX_TEXT = 64 * 1024;
    static final String PREMIUM = "premium_home", LAUNCHER = "launcher_settings";
    static final Set<String> FILES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "order.txt", "library_apps.txt", "library_home_removed.txt", "premium-wallpaper.jpg")));
    static final Map<String, Set<String>> KEYS = new LinkedHashMap<>();
    static {
        KEYS.put(PREMIUM, new HashSet<>(Arrays.asList("favorites", "name", "theme", "custom_wallpaper", "large_text",
                "safe_area", "tile_shape", "sound_move", "sound_click", "sound_volume", "history_enabled", "weather_city", "latitude", "longitude")));
        KEYS.put(LAUNCHER, new HashSet<>(Arrays.asList("columns", "rows_below_dock", "animations", "advanced_effects",
                "dock_opacity", "clock_format", "home_idle_clock_minutes")));
        KEYS.get(PREMIUM).addAll(Arrays.asList("visual_palette", "visual_scene", "visual_quality", "visual_motion", "visual_particles",
                "visual_parallax", "visual_bloom", "visual_shimmer", "visual_widgets", "visual_tile_style", "visual_wallpaper_dim", "app_categories"));
    }

    static final class Data {
        final Map<String, Map<String, Object>> preferences;
        final Map<String, byte[]> files;
        Data(Map<String, Map<String, Object>> preferences, Map<String, byte[]> files) {
            this.preferences = preferences; this.files = files;
        }
    }

    private BackupArchive() {}

    static void write(OutputStream output, Data data) throws IOException {
        validate(data);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            JSONObject prefs = new JSONObject();
            try {
                for (String store : KEYS.keySet()) prefs.put(store, new JSONObject(data.preferences.get(store)));
                JSONObject metadata = new JSONObject().put("format", "metoxtv-backup").put("version", 1)
                        .put("created_at", System.currentTimeMillis()).put("preferences", prefs);
                entry(zip, "settings.json", metadata.toString(2).getBytes(StandardCharsets.UTF_8));
                for (Map.Entry<String, byte[]> file : data.files.entrySet()) entry(zip, file.getKey(), file.getValue());
            } catch (JSONException e) { throw new IOException("Invalid settings", e); }
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }

    static Data read(InputStream input) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !(name.equals("settings.json") || FILES.contains(name)) || entries.containsKey(name))
                    throw new IOException("Unexpected archive entry");
                byte[] bytes = readBounded(zip, name.equals("premium-wallpaper.jpg") ? MAX_IMAGE : MAX_TEXT);
                total += bytes.length;
                if (total > MAX_ARCHIVE) throw new IOException("Archive too large");
                entries.put(name, bytes); zip.closeEntry();
            }
        }
        byte[] metadata = entries.remove("settings.json");
        if (metadata == null) throw new IOException("Missing settings");
        try {
            JSONObject root = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
            if (!"metoxtv-backup".equals(root.get("format")) || !(root.get("version") instanceof Integer)
                    || root.getInt("version") != 1) throw new IOException("Unsupported backup version");
            JSONObject prefs = root.getJSONObject("preferences");
            if (prefs.length() != KEYS.size()) throw new IOException("Unexpected settings group");
            Map<String, Map<String, Object>> values = new LinkedHashMap<>();
            for (String store : KEYS.keySet()) {
                JSONObject object = prefs.getJSONObject(store);
                Map<String, Object> items = new LinkedHashMap<>();
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) { String key = keys.next(); items.put(key, object.get(key)); }
                values.put(store, items);
            }
            Data data = new Data(values, entries); validate(data); return data;
        } catch (JSONException | IllegalArgumentException e) { throw new IOException("Invalid backup", e); }
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (out.size() + n > limit) throw new IOException("File too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static void validate(Data data) throws IOException {
        if (data == null || !data.preferences.keySet().equals(KEYS.keySet())) throw new IOException("Invalid settings groups");
        for (Map.Entry<String, Map<String, Object>> store : data.preferences.entrySet()) {
            if (store.getValue() == null) throw new IOException("Missing settings group");
            for (Map.Entry<String, Object> item : store.getValue().entrySet()) {
                if (!KEYS.get(store.getKey()).contains(item.getKey()) || !valid(item.getKey(), item.getValue()))
                    throw new IOException("Invalid setting: " + item.getKey());
            }
        }
        int total = 0;
        for (Map.Entry<String, byte[]> file : data.files.entrySet()) {
            if (!FILES.contains(file.getKey()) || file.getValue() == null) throw new IOException("Invalid file");
            boolean image = file.getKey().equals("premium-wallpaper.jpg");
            if (file.getValue().length > (image ? MAX_IMAGE : MAX_TEXT)) throw new IOException("File too large");
            total += file.getValue().length;
            if (!image) {
                String text = new String(file.getValue(), StandardCharsets.UTF_8);
                try {
                    if (file.getKey().equals("order.txt")) {
                        Order order = Order.decode(text);
                        if (order == null || order.keys.size() > 512) throw new IOException("Invalid tile order");
                    } else LibraryApps.decode(text);
                } catch (IllegalArgumentException e) { throw new IOException("Invalid shortcuts", e); }
            }
        }
        if (total > MAX_ARCHIVE) throw new IOException("Archive too large");
        if (Boolean.TRUE.equals(data.preferences.get(PREMIUM).get("custom_wallpaper")) && !data.files.containsKey("premium-wallpaper.jpg"))
            throw new IOException("Wallpaper is missing");
    }

    private static boolean integer(Object value, int min, int max) {
        return value instanceof Integer && (Integer) value >= min && (Integer) value <= max;
    }
    private static boolean oneOf(Object value, String... choices) { return value instanceof String && Arrays.asList(choices).contains(value); }
    private static boolean string(Object value, int max) { return value instanceof String && ((String) value).length() <= max && ((String) value).indexOf('\0') < 0; }
    static boolean valid(String key, Object value) {
        switch (key) {
            case "visual_motion": case "visual_particles": case "visual_parallax": case "visual_bloom": case "visual_shimmer": case "visual_widgets": return value instanceof Boolean;
            case "visual_palette": return value instanceof String && Arrays.asList(VisualPalette.NAMES).contains(value);
            case "visual_scene": return oneOf(value, "aurora", "cosmos", "horizon");
            case "visual_quality": return integer(value, 0, 2);
            case "visual_tile_style": return oneOf(value, "glass", "neon", "solid");
            case "visual_wallpaper_dim": return integer(value, 0, 90);
            case "app_categories":
                if (!string(value, 32768)) return false;
                try { AppCategories.parse((String) value); return true; } catch (IllegalArgumentException e) { return false; }
            case "custom_wallpaper": case "large_text": case "sound_move": case "sound_click": case "history_enabled":
            case "animations": case "advanced_effects": return value instanceof Boolean;
            case "columns": return integer(value, 4, 7);
            case "rows_below_dock": return integer(value, 0, 3);
            case "dock_opacity": return integer(value, 30, 100);
            case "safe_area": return integer(value, 0, 24);
            case "sound_volume": return integer(value, 0, 100);
            case "home_idle_clock_minutes": return integer(value, 0, 1440);
            case "theme": return oneOf(value, "neon", "aurora", "calm");
            case "tile_shape": return oneOf(value, "wide", "square", "compact");
            case "clock_format": return oneOf(value, "system", "12", "24");
            case "name": return string(value, 32);
            case "weather_city": return string(value, 256);
            case "favorites": return string(value, 8192) && value.equals(FavoriteOrder.encode(FavoriteOrder.parse((String) value)));
            case "latitude": case "longitude":
                if (!string(value, 32)) return false;
                try { double n = Double.parseDouble((String) value); return Double.isFinite(n) && Math.abs(n) <= (key.equals("latitude") ? 90 : 180); }
                catch (NumberFormatException e) { return false; }
            default: return false;
        }
    }
}
