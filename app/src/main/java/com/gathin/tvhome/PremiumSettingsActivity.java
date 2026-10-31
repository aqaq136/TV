package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native, remote-accessible personalization. No network or storage permissions required. */
public final class PremiumSettingsActivity extends ToolsActivityBase {
    private static final int WALLPAPER = 71;
    private LinearLayout rows;
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = PremiumPreferences.get(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xFF080F25);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(48), dp(24), dp(48), dp(24));
        scroll.addView(rows); setContentView(scroll);
        title("ErokhinTV · Персонализация", 28);
        title("Изменения сохраняются автоматически. Вернитесь кнопкой «Назад».", 14);
        row("Инструменты: APK, погода, сеть, звуки", () -> startActivity(new Intent(this, LauncherToolsActivity.class)));
        row("Избранное — выбрать приложения", this::editFavorites);
        row("Имя в приветствии", this::editName);
        row("Цветовая тема", () -> new LauncherDialogBuilder(this).setTitle("Цветовая тема")
                .setSingleChoiceItems(new String[]{"Неон · голубой и фиолетовый", "Аврора · бирюзовый", "Спокойная · приглушённые волны"},
                        java.util.Arrays.asList("neon", "aurora", "calm").indexOf(prefs.getString("theme", "neon")), (d, which) -> {
                            prefs.edit().putString("theme", new String[]{"neon", "aurora", "calm"}[which]).putBoolean("custom_wallpaper", false).apply();
                            changed(); d.dismiss();
                        }).setNegativeButton("Отмена", null).show());
        row("Свои обои — выбрать изображение", () -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(pick, WALLPAPER); }
            catch (ActivityNotFoundException e) { message("В прошивке нет выбора файлов. Используйте встроенные темы."); }
        });
        row("Убрать свои обои", () -> { prefs.edit().putBoolean("custom_wallpaper", false).apply(); changed(); message("Выбрана встроенная тема"); });
        row("Отступы от краёв экрана", () -> {
            int selected = prefs.getInt("safe_area", 0) / 12;
            new LauncherDialogBuilder(this).setTitle("Если телевизор обрезает края")
                    .setSingleChoiceItems(new String[]{"Обычные", "Увеличенные", "Максимальные"}, Math.max(0, Math.min(2, selected)), (d, which) -> {
                        prefs.edit().putInt("safe_area", which * 12).apply(); changed(); d.dismiss();
                    }).setNegativeButton("Отмена", null).show();
        });
        TextView large = row("", () -> {});
        updateLargeLabel(large);
        large.setOnClickListener(v -> { prefs.edit().putBoolean("large_text", !prefs.getBoolean("large_text", false)).apply(); changed(); updateLargeLabel(large); });
        row("Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        row("Bluetooth", () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        row("Системные настройки", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        row("Скачать обновление с GitHub", () -> open(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/aqaq136/TV/tree/erokhintv-premium/downloads"))));
        title("Плитки: удерживайте OK, чтобы переместить или добавить в избранное.\nИзбранное: удерживайте OK, чтобы изменить порядок или убрать ярлык.\nРазмер плиток, часы и анимации — в предыдущем экране настроек.", 14);
        row("Сбросить персонализацию", () -> new LauncherDialogBuilder(this).setTitle("Сбросить персонализацию?")
                .setMessage("Будут сброшены избранное, имя, тема и отступы. Установленные приложения и порядок основных плиток сохранятся.")
                .setPositiveButton("Сбросить", (d, w) -> { prefs.edit().clear().apply(); changed(); updateLargeLabel(large); message("Персонализация сброшена"); })
                .setNegativeButton("Отмена", null).show());
        if (state == null && getIntent().getBooleanExtra("edit_favorites", false)) scroll.post(this::editFavorites);
    }
    private void updateLargeLabel(TextView view) { view.setText("Крупные подписи: " + (prefs.getBoolean("large_text", false) ? "включены" : "выключены")); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void title(String label, int size) {
        TextView t = new TextView(this); t.setText(label); t.setTextSize(size); t.setTextColor(0xFFD3E5FF);
        t.setPadding(0, dp(8), 0, dp(12)); rows.addView(t);
    }
    private TextView row(String label, Runnable action) {
        TextView t = new TextView(this); t.setText(label); t.setTextColor(Color.WHITE); t.setTextSize(18);
        t.setGravity(android.view.Gravity.CENTER_VERTICAL); t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setMinHeight(dp(52)); t.setFocusable(true); t.setClickable(true);
        t.setBackgroundResource(R.drawable.erokhin_focus_tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8); rows.addView(t, lp);
        t.setOnClickListener(v -> action.run()); return t;
    }
    private void changed() { PremiumPreferences.changed(this); }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) { message("Этот экран недоступен. Откройте системные настройки или GitHub на компьютере."); }
    }
    private void editName() {
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(prefs.getString("name", ""));
        field.setHint("Например, Александр"); field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        new LauncherDialogBuilder(this).setTitle("Имя в приветствии").setView(field)
                .setPositiveButton("Сохранить", (d, w) -> { prefs.edit().putString("name", field.getText().toString().trim()).apply(); changed(); })
                .setNeutralButton("Без имени", (d, w) -> { prefs.edit().remove("name").apply(); changed(); })
                .setNegativeButton("Отмена", null).show();
    }
    private void editFavorites() {
        Map<String, String> installed = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(getPackageName())) installed.put(pkg, info.loadLabel(getPackageManager()).toString());
            }
        }
        List<String> packages = new ArrayList<>(installed.keySet());
        java.text.Collator collator = java.text.Collator.getInstance();
        packages.sort((a, b) -> collator.compare(installed.get(a), installed.get(b)));
        if (packages.isEmpty()) { message("Нет доступных приложений для добавления"); return; }
        List<String> selected = new ArrayList<>(PremiumPreferences.favorites(this));
        selected.retainAll(packages);
        String[] labels = new String[packages.size()]; boolean[] checked = new boolean[packages.size()];
        for (int i = 0; i < packages.size(); i++) { labels[i] = installed.get(packages.get(i)); checked[i] = selected.contains(packages.get(i)); }
        new LauncherDialogBuilder(this).setTitle("Избранное · до " + FavoriteOrder.LIMIT + " приложений")
                .setMultiChoiceItems(labels, checked, (d, which, on) -> {
                    String pkg = packages.get(which);
                    if (on && !selected.contains(pkg)) selected.add(pkg); else if (!on) selected.remove(pkg);
                }).setPositiveButton("Сохранить", (d, w) -> {
                    if (selected.size() > FavoriteOrder.LIMIT) { message("Выберите не больше " + FavoriteOrder.LIMIT + " приложений. Изменения не сохранены."); return; }
                    PremiumPreferences.saveFavorites(this, selected); message("Избранное сохранено");
                }).setNegativeButton("Отмена", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != WALLPAPER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        File temporary = new File(getFilesDir(), "premium-wallpaper.tmp");
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, options); }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new java.io.IOException("Unsupported image");
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1920) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream stream = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
            if (bitmap == null) throw new java.io.IOException("Cannot decode");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw new java.io.IOException("Cannot save");
            } finally { bitmap.recycle(); }
            if (!temporary.renameTo(new File(getFilesDir(), "premium-wallpaper.jpg"))) throw new java.io.IOException("Cannot replace");
            prefs.edit().putBoolean("custom_wallpaper", true).apply(); changed(); message("Обои сохранены");
        } catch (Exception | OutOfMemoryError e) { temporary.delete(); message("Не удалось сохранить изображение. Попробуйте обычный JPG или PNG меньшего размера."); }
    }
}
