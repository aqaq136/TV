package com.gathin.tvhome;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class BackupArchiveTest {
    private BackupArchive.Data empty() {
        Map<String, Map<String, Object>> prefs = new LinkedHashMap<>();
        for (String key : BackupArchive.KEYS.keySet()) prefs.put(key, new LinkedHashMap<>());
        return new BackupArchive.Data(prefs, new LinkedHashMap<>());
    }
    private byte[] encode(BackupArchive.Data data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BackupArchive.write(out, data); return out.toByteArray();
    }
    private byte[] archive(String name, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
        }
        return out.toByteArray();
    }
    private byte[] metadata(String prefs, String version) throws IOException {
        String json = "{\"format\":\"metoxtv-backup\",\"version\":" + version + ",\"preferences\":" + prefs + "}";
        return archive("settings.json", json.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void roundTripPreservesTypesAndAnIntentionallyEmptyFavoritesList() throws Exception {
        BackupArchive.Data source = empty();
        source.preferences.get(BackupArchive.PREMIUM).put("favorites", "");
        source.preferences.get(BackupArchive.PREMIUM).put("large_text", true);
        source.preferences.get(BackupArchive.LAUNCHER).put("columns", 6);
        source.files.put("library_apps.txt", "org.example.tv\n".getBytes(StandardCharsets.UTF_8));
        source.files.put("order.txt", "tvhome-order 1\ndock \norg.example.tv/.Main\n".getBytes(StandardCharsets.UTF_8));
        BackupArchive.Data restored = BackupArchive.read(new ByteArrayInputStream(encode(source)));
        assertEquals(source.preferences, restored.preferences);
        assertArrayEquals(source.files.get("order.txt"), restored.files.get("order.txt"));
        assertEquals(2, restored.files.size());
    }
    @Test(expected = IOException.class) public void rejectsPathTraversal() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(archive("../shared_prefs/launcher_settings.xml", new byte[0])));
    }
    @Test(expected = IOException.class) public void rejectsUnknownFiles() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(archive("config.json", "{}".getBytes(StandardCharsets.UTF_8))));
    }
    @Test(expected = IOException.class) public void rejectsZipBombBeforeExpandingItAll() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(archive("premium-wallpaper.jpg", new byte[BackupArchive.MAX_IMAGE + 1])));
    }
    @Test(expected = IOException.class) public void rejectsFutureVersion() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(metadata("{\"premium_home\":{},\"launcher_settings\":{}}", "2")));
    }
    @Test(expected = IOException.class) public void rejectsStringVersion() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(metadata("{\"premium_home\":{},\"launcher_settings\":{}}", "\"1\"")));
    }
    @Test(expected = IOException.class) public void rejectsWrongSettingsTypesInsteadOfCoercingThem() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(metadata("{\"premium_home\":{},\"launcher_settings\":{\"columns\":\"6\"}}", "1")));
    }
    @Test(expected = IOException.class) public void rejectsPrivilegedOrUnknownSettings() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.LAUNCHER).put("stock_home_redirect", "org.stock.home"); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsInvalidNumericRange() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.LAUNCHER).put("columns", 100); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsInvalidEnum() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.PREMIUM).put("theme", "unexpected"); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsMissingWallpaper() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.PREMIUM).put("custom_wallpaper", true); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsDuplicateShortcutPackages() throws Exception {
        BackupArchive.Data source = empty(); source.files.put("library_apps.txt", "org.a\norg.a\n".getBytes(StandardCharsets.UTF_8)); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsBrokenTileOrder() throws Exception {
        BackupArchive.Data source = empty(); source.files.put("order.txt", "broken".getBytes(StandardCharsets.UTF_8)); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsTruncatedJson() throws Exception {
        BackupArchive.read(new ByteArrayInputStream(archive("settings.json", "{\"format\":".getBytes(StandardCharsets.UTF_8))));
    }
    @Test(expected = IOException.class) public void rejectsNonZipInput() throws Exception {
        BackupArchive.read(new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8)));
    }
    @Test(expected = IOException.class) public void rejectsInvalidCoordinates() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.PREMIUM).put("latitude", "NaN"); encode(source);
    }
    @Test public void onlyPortableKeysAreIncluded() {
        assertFalse(BackupArchive.KEYS.get(BackupArchive.LAUNCHER).contains("open_on_start"));
        assertFalse(BackupArchive.KEYS.get(BackupArchive.LAUNCHER).contains("settings_revision"));
        assertFalse(BackupArchive.KEYS.get(BackupArchive.PREMIUM).contains("weather_summary"));
        assertFalse(BackupArchive.FILES.contains("config.json"));
    }
    @Test public void visualStudioAndCategoriesRoundTrip() throws Exception {
        BackupArchive.Data source = empty(); Map<String,Object> p = source.preferences.get(BackupArchive.PREMIUM);
        p.put("visual_palette", "sunset"); p.put("visual_scene", "horizon"); p.put("visual_quality", 2);
        p.put("visual_particles", true); p.put("visual_tile_style", "neon"); p.put("app_categories", "{\"org.example\":2}");
        assertEquals(source.preferences,BackupArchive.read(new ByteArrayInputStream(encode(source))).preferences);
    }
    @Test(expected = IOException.class) public void rejectsUnknownVisualPalette() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.PREMIUM).put("visual_palette", "broken"); encode(source);
    }
    @Test(expected = IOException.class) public void rejectsInvalidManualCategories() throws Exception {
        BackupArchive.Data source = empty(); source.preferences.get(BackupArchive.PREMIUM).put("app_categories", "{\"org.example\":99}"); encode(source);
    }
}
