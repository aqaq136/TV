package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

/** The premium home controls, independent of the app grid's lifecycle. */
final class PremiumHome {
    private final Activity activity;
    private final Runnable returnToGrid;
    private final float density;
    private final boolean animations;
    private String focusedPackage;
    private boolean restoreAfterLaunch;
    private final LinearLayout favorites;
    private final VisualDashboard dashboard;

    PremiumHome(Activity activity, Runnable returnToGrid) {
        this.activity = activity;
        this.returnToGrid = returnToGrid;
        VisualPreferences.migrate(activity);
        dashboard = new VisualDashboard(activity, returnToGrid);
        density = activity.getResources().getDisplayMetrics().density;
        animations = Config.load(activity).animations;
        favorites = activity.findViewById(R.id.erokhinFavorites);
        if (favorites == null) return; // Alternate locale layout without the premium controls.
        int[] ids = {R.id.erokhinStorage, R.id.erokhinMemory, R.id.erokhinStartup, R.id.erokhinSystem};
        for (int i = 0; i < ids.length; i++) {
            View v = activity.findViewById(ids[i]);
            TextView utility = (TextView) v;
            Drawable[] marks = utility.getCompoundDrawablesRelative();
            for (Drawable mark : marks) if (mark != null) mark.setBounds(0, 0, dp(18), dp(18));
            utility.setCompoundDrawablesRelative(marks[0], marks[1], marks[2], marks[3]);
            v.setBackground(new PrismSurfaceDrawable(VisualPalette.of(PremiumPreferences.get(activity).getString("visual_palette", "cosmos")).primary,
                    density, dp(10), "glass"));
            v.setNextFocusLeftId(ids[Math.max(0, i - 1)]);
            v.setNextFocusRightId(ids[Math.min(ids.length - 1, i + 1)]);
            v.setNextFocusDownId(ids[i]);
            decorate(v);
            v.setOnKeyListener((view, code, event) -> {
                if (code == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
                    focusFavorite(); return true;
                }
                return false;
            });
        }
        activity.findViewById(R.id.erokhinStorage).setOnClickListener(v -> open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        activity.findViewById(R.id.erokhinStartup).setOnClickListener(v -> open(Settings.ACTION_HOME_SETTINGS));
        activity.findViewById(R.id.erokhinMemory).setOnClickListener(v -> {
            ActivityManager.MemoryInfo info = memory();
            new LauncherDialogBuilder(activity).setTitle("Оперативная память")
                    .setMessage(String.format(Locale.getDefault(), "Доступно: %.1f ГБ\nВсего: %.1f ГБ\n\nAndroid управляет памятью автоматически.",
                            info.availMem / 1073741824.0, info.totalMem / 1073741824.0))
                    .setPositiveButton("Понятно", null).show();
        });
        activity.findViewById(R.id.erokhinSystem).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, LauncherToolsActivity.class)));
        refreshFavorites();
    }
    private int dp(float value) { return Math.round(value * density); }
    private String version() {
        try { return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }
    private void open(String action) {
        try { activity.startActivity(new Intent(action)); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException missing) { message("Этот экран недоступен в прошивке телевизора"); }
        }
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    private void decorate(View view) {
        view.setOnFocusChangeListener((v, focused) -> {
            boolean animate = animations;
            v.animate().cancel();
            v.animate().scaleX(focused && animate ? 1.04f : 1f).scaleY(focused && animate ? 1.04f : 1f)
                    .setDuration(animate ? 140 : 0).start();
        });
    }
    void resume() { dashboard.resume(); }
    void pause() { dashboard.pause(); }
    void close() { dashboard.close(); }
    void focused(String label, String pkg, Drawable icon, int accent, View tile) { dashboard.focused(label, pkg, icon, accent, tile); }
    void refreshFavorites() {
        if (favorites == null) return;
        View previous = activity.getCurrentFocus();
        boolean restore = previous != null && previous.getParent() == favorites;
        String previousTag = restore && previous.getTag() instanceof String ? (String) previous.getTag() : null;
        favorites.removeAllViews();
        PackageManager pm = activity.getPackageManager();
        for (String pkg : PremiumPreferences.favorites(activity)) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            try {
                TextView button = button(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg, 86);
                Drawable icon = pm.getApplicationIcon(pkg);
                icon.setBounds(0, 0, dp(24), dp(24));
                button.setCompoundDrawables(null, icon, null, null);
                button.setCompoundDrawablePadding(dp(1));
                Intent target = launch;
                button.setOnClickListener(v -> {
                    focusedPackage = pkg;
                    try { activity.startActivity(target); LastOpened.record(activity, pkg); restoreAfterLaunch = true; }
                    catch (ActivityNotFoundException | SecurityException e) { message("Не удалось открыть приложение. Проверьте, установлено ли оно."); refreshFavorites(); }
                });
                button.setOnLongClickListener(v -> {
                    new LauncherDialogBuilder(activity).setTitle(button.getContentDescription())
                            .setItems(new String[]{"Переместить влево", "Переместить вправо", "Убрать из избранного"}, (d, which) -> {
                                List<String> list = PremiumPreferences.favorites(activity);
                                if (which == 2) list.remove(pkg);
                                else list = FavoriteOrder.move(list, pkg, which == 0 ? -1 : 1);
                                PremiumPreferences.saveFavorites(activity, list);
                                refreshFavorites();
                                focusPackage(pkg);
                            }).show();
                    return true;
                });
            } catch (PackageManager.NameNotFoundException ignored) { /* App removed during refresh. */ }
        }
        TextView add = button("+", "__edit__", 64);
        add.setTextSize(30);
        add.setContentDescription("Настроить избранное");
        add.setOnClickListener(v -> {
            focusedPackage = "__edit__";
            restoreAfterLaunch = true;
            activity.startActivity(new Intent(activity, PremiumSettingsActivity.class).putExtra("edit_favorites", true));
        });
        for (int i = 0; i < favorites.getChildCount(); i++) {
            View child = favorites.getChildAt(i);
            child.setNextFocusLeftId(favorites.getChildAt(Math.max(0, i - 1)).getId());
            child.setNextFocusRightId(favorites.getChildAt(Math.min(favorites.getChildCount() - 1, i + 1)).getId());
        }
        if (restore) focusPackage(previousTag);
    }
    private TextView button(String label, String tag, int width) {
        TextView b = new TextView(activity);
        b.setId(View.generateViewId()); b.setTag(tag);
        b.setText(label); b.setContentDescription(label); b.setTextColor(Color.WHITE); b.setTextSize(11);
        b.setSingleLine(true); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(new PrismSurfaceDrawable(VisualPalette.of(PremiumPreferences.get(activity).getString("visual_palette", "cosmos")).primary,
                density, dp(10), "glass")); b.setFocusable(true); b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0); favorites.addView(b, lp);
        decorate(b);
        b.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP) { returnToGrid.run(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { activity.findViewById(R.id.erokhinMemory).requestFocus(); return true; }
            return false;
        });
        return b;
    }
    private void focusPackage(String pkg) {
        for (int i = 0; i < favorites.getChildCount(); i++) {
            View child = favorites.getChildAt(i);
            if (pkg != null && pkg.equals(child.getTag())) { child.requestFocus(); return; }
        }
        if (favorites.getChildCount() > 0) favorites.getChildAt(0).requestFocus();
    }
    private void focusFavorite() { focusPackage(focusedPackage); }
    void restoreFocusAfterLaunch() {
        if (!restoreAfterLaunch || favorites == null) return;
        restoreAfterLaunch = false;
        favorites.post(() -> focusPackage(focusedPackage));
    }
    private ActivityManager.MemoryInfo memory() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info;
    }
    void refreshStatus() {
        if (favorites == null) return;
        ((TextView) activity.findViewById(R.id.erokhinMemory)).setText(String.format(Locale.getDefault(), "ОЗУ · %.1f ГБ", memory().availMem / 1073741824.0));
        TextView subtitle = activity.findViewById(R.id.premiumSubtitle);
        if (subtitle != null) {
            android.content.SharedPreferences prefs = PremiumPreferences.get(activity);
            String weather = prefs.getString("weather_summary", "");
            long time = prefs.getLong("weather_time", 0);
            if (!weather.isEmpty()) {
                String city = prefs.getString("weather_city", "").split(",")[0];
                String saved = android.text.format.DateFormat.format("dd.MM HH:mm", time).toString();
                subtitle.setText(city + " · " + weather + " · " + saved);
                subtitle.setTextSize(10); subtitle.setMaxLines(2);
            } else { subtitle.setText(R.string.visual_weather_choose); subtitle.setTextSize(11); }
        }
        StatFs storage = new StatFs(activity.getFilesDir().getAbsolutePath());
        ((TextView) activity.findViewById(R.id.erokhinStorage)).setText(String.format(Locale.getDefault(), "Свободно · %.1f ГБ", storage.getAvailableBytes() / 1073741824.0));
        dashboard.refreshStatus();
    }
    void applyAppearance() {
        View content = activity.findViewById(R.id.premiumContent);
        if (content == null) return;
        android.content.SharedPreferences prefs = PremiumPreferences.get(activity);
        int safe = prefs.getInt("safe_area", 0);
        content.setPadding(dp(28 + safe), dp(14 + safe / 2f), dp(28 + safe), dp(14 + safe / 2f));
        ((TextView) activity.findViewById(R.id.greeting)).setTextSize(prefs.getBoolean("large_text", false) ? 19 : 16);
        View root = activity.findViewById(R.id.root);
        String theme = prefs.getString("theme", "neon");
        root.setBackgroundColor(VisualPalette.of(prefs.getString("visual_palette", "cosmos")).background);
        java.io.File wallpaper = new java.io.File(activity.getFilesDir(), "premium-wallpaper.jpg");
        if (prefs.getBoolean("custom_wallpaper", false) && wallpaper.isFile()) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(wallpaper.getAbsolutePath());
            if (bitmap != null) {
                Drawable picture = new PremiumWallpaperDrawable(bitmap);
                int dim = Math.max(0, Math.min(90, prefs.getInt("visual_wallpaper_dim", 40)));
                android.graphics.drawable.ColorDrawable shade = new android.graphics.drawable.ColorDrawable(Math.round(dim * 2.55f) << 24);
                root.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{picture, shade}));
            }
        }
        dashboard.style();
    }
}
