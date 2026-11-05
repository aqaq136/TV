package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies only portable owner choices. Firmware config, permissions and boot actions are excluded. */
final class BackupStore {
    private static final String RECOVERY = "before-restore.zip";
    private BackupStore() {}

    static BackupArchive.Data snapshot(Context context) throws IOException {
        Map<String, Map<String, Object>> preferences = new LinkedHashMap<>();
        for (String name : BackupArchive.KEYS.keySet()) {
            Map<String, ?> saved = context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
            Map<String, Object> portable = new LinkedHashMap<>();
            for (String key : BackupArchive.KEYS.get(name)) if (saved.containsKey(key)) portable.put(key, saved.get(key));
            preferences.put(name, portable);
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : BackupArchive.FILES) {
            AtomicFile file = new AtomicFile(new File(context.getFilesDir(), name));
            if (file.getBaseFile().isFile() || new File(file.getBaseFile() + ".bak").isFile()) {
                try (FileInputStream in = file.openRead()) {
                    files.put(name, BackupArchive.readBounded(in, name.endsWith(".jpg") ? BackupArchive.MAX_IMAGE : 65536));
                }
            }
        }
        BackupArchive.Data data = new BackupArchive.Data(preferences, files); BackupArchive.validate(data); return data;
    }

    static void validateImage(BackupArchive.Data data) throws IOException {
        byte[] image = data.files.get("premium-wallpaper.jpg");
        if (image == null) return;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4096 || bounds.outHeight > 4096
                || (long) bounds.outWidth * bounds.outHeight > 8_000_000) throw new IOException("Unsupported wallpaper");
    }

    static void restore(Context context, BackupArchive.Data data) throws IOException {
        BackupArchive.validate(data); validateImage(data);
        BackupArchive.Data previous = snapshot(context);
        ByteArrayOutputStream copy = new ByteArrayOutputStream(); BackupArchive.write(copy, previous);
        write(new AtomicFile(new File(context.getFilesDir(), RECOVERY)), copy.toByteArray());
        try { apply(context, data); }
        catch (IOException | RuntimeException e) {
            try { apply(context, previous); }
            catch (IOException | RuntimeException rollback) { e.addSuppressed(rollback); }
            throw new IOException("Restore failed; previous copy retained", e);
        }
    }

    static boolean hasRecovery(Context context) {
        return new File(context.getFilesDir(), RECOVERY).isFile() || new File(context.getFilesDir(), RECOVERY + ".bak").isFile();
    }

    static BackupArchive.Data recovery(Context context) throws IOException {
        try (FileInputStream in = new AtomicFile(new File(context.getFilesDir(), RECOVERY)).openRead()) {
            byte[] bytes = BackupArchive.readBounded(in, BackupArchive.MAX_ARCHIVE);
            return BackupArchive.read(new ByteArrayInputStream(bytes));
        }
    }

    private static void apply(Context context, BackupArchive.Data data) throws IOException {
        for (String name : BackupArchive.FILES) {
            AtomicFile file = new AtomicFile(new File(context.getFilesDir(), name));
            byte[] bytes = data.files.get(name);
            if (bytes != null) write(file, bytes);
            else {
                file.delete();
                if (file.getBaseFile().exists()) throw new IOException("Cannot remove previous file");
            }
        }
        for (String name : BackupArchive.KEYS.keySet()) {
            SharedPreferences.Editor editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            for (String key : BackupArchive.KEYS.get(name)) editor.remove(key);
            for (Map.Entry<String, Object> item : data.preferences.get(name).entrySet()) {
                Object v = item.getValue(); String key = item.getKey();
                if (v instanceof String) editor.putString(key, (String) v);
                else if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
                else if (v instanceof Integer) editor.putInt(key, (Integer) v);
            }
            // A cached weather summary belongs to the former city, not the restored one.
            if (name.equals(BackupArchive.PREMIUM)) editor.remove("weather_summary").remove("weather_time").remove("weather_source").remove("weather_attempt").remove("weather_error").remove("weather_hidden").remove("weather_next_refresh").remove("weather_forecast_time");
            if (!editor.commit()) throw new IOException("Cannot save settings");
        }
        PremiumPreferences.changed(context);
    }

    private static void write(AtomicFile file, byte[] bytes) throws IOException {
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (IOException | RuntimeException e) { if (out != null) file.failWrite(out); throw e; }
    }
}
