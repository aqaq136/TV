package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The visible 4.0 dashboard around the existing, fully featured launcher grid. */
final class VisualDashboard {
    static final SessionTimer SESSION = new SessionTimer();
    private final Activity activity;
    private final Runnable returnToGrid;
    private final AtmosphereView atmosphere;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<TextView> actions = new ArrayList<>();
    private final int[] location = new int[2];
    private TextView timer, title, caption, continueButton;
    private ImageView heroIcon;
    private String currentPackage, recentPackage;
    private Intent recentIntent;
    private boolean active, closed, animate;
    private int generation;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!active) return;
            refreshTimer();
            if (SESSION.consumeFinished(SystemClock.elapsedRealtime())) Toast.makeText(activity, R.string.visual_timer_finished, Toast.LENGTH_LONG).show();
            main.postDelayed(this, 1000);
        }
    };

    VisualDashboard(Activity activity, Runnable returnToGrid) {
        this.activity = activity; this.returnToGrid = returnToGrid;
        atmosphere = activity.findViewById(R.id.visualAtmosphere);
        LinearLayout bar = activity.findViewById(R.id.visualQuickBar);
        if (bar == null) return;
        title = activity.findViewById(R.id.visualSpotlightTitle); caption = activity.findViewById(R.id.visualSpotlightCaption);
        heroIcon = activity.findViewById(R.id.visualHeroIcon); continueButton = activity.findViewById(R.id.visualContinue);
        continueButton.setOnClickListener(v -> openRecent());
        action(bar, R.string.visual_all, () -> apps(0));
        action(bar, R.string.center_favorites, () -> apps(1));
        action(bar, R.string.center_recent, () -> apps(2));
        action(bar, R.string.visual_studio, () -> activity.startActivity(new Intent(activity, VisualSettingsActivity.class)));
        timer = action(bar, R.string.visual_timer, this::chooseTimer);
        View weather = activity.findViewById(R.id.visualWeather);
        weather.setOnClickListener(v -> activity.startActivity(new Intent(activity, LauncherToolsActivity.class).putExtra("action", "weather")));
        View stats = activity.findViewById(R.id.visualDevice);
        stats.setOnClickListener(v -> activity.startActivity(new Intent(activity, LauncherToolsActivity.class)));
        for (View v : new View[]{continueButton, weather, stats}) {
            v.setOnKeyListener((view, code, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { actions.get(0).requestFocus(); return true; }
                return false;
            });
            v.setOnFocusChangeListener((view, focused) -> animateControl(view, focused));
        }
        style();
    }
    private int dp(float x) { return Math.round(x * activity.getResources().getDisplayMetrics().density); }
    private void animateControl(View view, boolean focused) {
        view.animate().cancel();
        view.animate().scaleX(focused && animate ? 1.025f : 1).scaleY(focused && animate ? 1.025f : 1).setDuration(animate ? 160 : 0).start();
    }
    private TextView action(LinearLayout bar, int label, Runnable action) {
        TextView b = new TextView(activity); b.setId(View.generateViewId()); b.setText(label); b.setTextSize(12); b.setTextColor(0xFFD3E3F7);
        b.setGravity(Gravity.CENTER); b.setSingleLine(); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setPadding(dp(8), 0, dp(8), 0); b.setFocusable(true); b.setClickable(true);
        b.setBackgroundResource(R.drawable.visual_control);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1); lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        bar.addView(b, lp); actions.add(b);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focused) -> animateControl(v, focused));
        b.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { returnToGrid.run(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_UP) { continueButton.requestFocus(); return true; }
            return code == KeyEvent.KEYCODE_DPAD_LEFT && b == actions.get(0)
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT && b == actions.get(actions.size() - 1);
        }); return b;
    }
    private void apps(int mode) { activity.startActivity(new Intent(activity, AppCenterActivity.class).putExtra("mode", mode)); }
    private void openRecent() {
        if (recentIntent == null) { apps(0); return; }
        try { activity.startActivity(recentIntent); LastOpened.record(activity, recentPackage); }
        catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(activity, R.string.center_launch_failed, Toast.LENGTH_LONG).show(); refreshRecent(); }
    }
    void style() {
        if (atmosphere == null) return;
        android.content.SharedPreferences p = PremiumPreferences.get(activity);
        animate = Config.load(activity).animations;
        atmosphere.configure();
        VisualPalette palette = VisualPalette.of(p.getString("visual_palette", "cosmos"));
        for (int id : new int[]{R.id.visualHeroPanel, R.id.visualWeather, R.id.visualDevice}) {
            activity.findViewById(id).setBackground(new PrismSurfaceDrawable(id == R.id.visualHeroPanel ? palette.secondary : palette.primary,
                    activity.getResources().getDisplayMetrics().density, dp(18), "glass"));
        }
        View widgets = activity.findViewById(R.id.visualWidgets);
        widgets.setVisibility(p.getBoolean("visual_widgets", true) ? View.VISIBLE : View.GONE);
        continueButton.setBackground(new PrismSurfaceDrawable(palette.primary, activity.getResources().getDisplayMetrics().density, dp(10), "neon"));
        continueButton.setTextColor(Color.WHITE);
        View appPanel = activity.findViewById(R.id.premiumAppsPanel);
        if (appPanel != null) {
            PrismSurfaceDrawable panel = new PrismSurfaceDrawable(palette.primary, activity.getResources().getDisplayMetrics().density, dp(20), "panel");
            panel.setAlpha(Math.round(Config.load(activity).dockOpacity * 2.55f)); appPanel.setBackground(panel);
        }
    }
    void resume() {
        active = true; if (atmosphere != null) { atmosphere.setActive(true); refreshRecent(); }
        tileEffects(activity.getWindow().getDecorView(), true); main.removeCallbacks(tick); main.post(tick);
    }
    void pause() {
        active = false; if (atmosphere != null) atmosphere.setActive(false);
        tileEffects(activity.getWindow().getDecorView(), false); main.removeCallbacks(tick);
        if (title != null) { title.animate().cancel(); caption.animate().cancel(); heroIcon.animate().cancel(); }
    }
    void close() { closed = true; pause(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); }
    private void tileEffects(View v, boolean enabled) {
        if (v instanceof PrismTileView) ((PrismTileView) v).setEffectsActive(enabled);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) tileEffects(g.getChildAt(i), enabled); }
    }
    void focused(String label, String pkg, Drawable icon, int accent, View tile) {
        if (title == null || pkg == null || pkg.equals(activity.getPackageName())) return;
        tile.getLocationOnScreen(location);
        atmosphere.follow((location[0] + tile.getWidth() / 2f) / Math.max(1, atmosphere.getWidth()) * 2 - 1,
                (location[1] + tile.getHeight() / 2f) / Math.max(1, atmosphere.getHeight()) * 2 - 1);
        if (pkg.equals(currentPackage)) return; currentPackage = pkg;
        title.animate().cancel(); caption.animate().cancel(); heroIcon.animate().cancel();
        title.setText(label); heroIcon.setImageDrawable(icon);
        long time = LastOpened.get(activity, pkg);
        caption.setText(time > 0 ? activity.getString(R.string.visual_last_opened, LastOpened.describe(activity, time)) : activity.getString(R.string.visual_open_hint));
        if (animate) {
            title.setAlpha(.3f); title.setTranslationY(dp(6)); title.animate().alpha(1).translationY(0).setDuration(220).start();
            heroIcon.setAlpha(.25f); heroIcon.setRotation(-8); heroIcon.setScaleX(.9f); heroIcon.setScaleY(.9f);
            heroIcon.animate().alpha(.65f).rotation(0).scaleX(1).scaleY(1).setDuration(320).start();
        } else { title.setAlpha(1); title.setTranslationY(0); heroIcon.setAlpha(.65f); }
    }
    void refreshStatus() {
        if (atmosphere == null) return;
        TextView ram = activity.findViewById(R.id.erokhinMemory), storage = activity.findViewById(R.id.erokhinStorage);
        ((TextView) activity.findViewById(R.id.visualDeviceText)).setText(activity.getString(R.string.visual_device_info, ram.getText(), storage.getText()));
        refreshTimer();
    }
    private void refreshRecent() {
        int request = ++generation;
        worker.execute(() -> {
            Map<String, ?> history = activity.getSharedPreferences("last_opened", Activity.MODE_PRIVATE).getAll();
            List<Map.Entry<String, ?>> entries = new ArrayList<>(history.entrySet());
            entries.removeIf(e -> !(e.getValue() instanceof Long));
            entries.sort((a,b) -> Long.compare((Long)b.getValue(), (Long)a.getValue()));
            String pkg = null, label = null; Intent target = null;
            for (Map.Entry<String, ?> entry : entries) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    target = activity.getPackageManager().getLeanbackLaunchIntentForPackage(entry.getKey());
                    if (target == null) target = activity.getPackageManager().getLaunchIntentForPackage(entry.getKey());
                    if (target != null && !entry.getKey().equals(activity.getPackageName())) {
                        pkg = entry.getKey(); label = AppLabels.application(activity, activity.getPackageManager().getApplicationInfo(pkg, 0)); break;
                    }
                } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) { target = null; }
            }
            String resultPackage = pkg, resultLabel = label; Intent resultIntent = target;
            main.post(() -> {
                if (closed || request != generation) return;
                recentPackage = resultPackage; recentIntent = resultIntent;
                continueButton.setText(resultLabel == null ? activity.getString(R.string.visual_browse) : activity.getString(R.string.visual_continue, resultLabel));
            });
        });
    }
    private void refreshTimer() {
        if (timer == null) return;
        long left = SESSION.remaining(SystemClock.elapsedRealtime());
        timer.setText(left > 0 ? activity.getString(R.string.visual_timer_left, SessionTimer.format(left)) : activity.getString(R.string.visual_timer));
    }
    private void chooseTimer() {
        int[] durations = {15,25,45,60}; String[] labels = new String[5];
        for (int i = 0; i < durations.length; i++) labels[i] = activity.getString(R.string.idle_minutes, durations[i]);
        labels[4] = activity.getString(R.string.visual_timer_stop);
        new LauncherDialogBuilder(activity).setTitle(R.string.visual_timer).setItems(labels, (d, which) -> {
            if (which == 4) SESSION.stop(); else SESSION.start(SystemClock.elapsedRealtime(), durations[which]); refreshTimer();
            Toast.makeText(activity, R.string.visual_timer_note, Toast.LENGTH_LONG).show();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
