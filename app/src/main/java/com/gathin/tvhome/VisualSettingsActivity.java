package com.gathin.tvhome;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Live preview uses exactly the same renderer as Home. Every setting is available from a remote. */
public final class VisualSettingsActivity extends ToolsActivityBase {
    private SharedPreferences prefs;
    private AtmosphereView preview;
    private LinearLayout rows;
    private final java.util.List<Runnable> toggleRefreshes = new java.util.ArrayList<>();
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); prefs = PremiumPreferences.get(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20)); root.setBackgroundColor(0xFF070B1B); setContentView(root);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 1));
        TextView title = label(getString(R.string.visual_studio), 26); left.addView(title);
        preview = new AtmosphereView(this); left.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        left.addView(label(getString(R.string.visual_preview_note), 14));
        ScrollView scroll = new ScrollView(this); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1); sp.leftMargin = dp(22); root.addView(scroll, sp);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        row(R.string.visual_presets, () -> choices(R.string.visual_presets, R.array.visual_presets, -1, which -> {
            boolean full = which == 0, animated = which < 2;
            prefs.edit().putInt("visual_quality", full ? 2 : animated ? 1 : 0).putBoolean("visual_motion", animated)
                    .putBoolean("visual_particles", full).putBoolean("visual_shimmer", animated).putBoolean("visual_parallax", animated).putBoolean("visual_bloom", true).apply();
            getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit().putBoolean("animations", animated).putBoolean("advanced_effects", full).apply(); changed();
        }));
        row(R.string.visual_palette, () -> choices(R.string.visual_palette, R.array.visual_palettes,
                java.util.Arrays.asList(VisualPalette.NAMES).indexOf(prefs.getString("visual_palette", "cosmos")), which -> {
                    prefs.edit().putString("visual_palette", VisualPalette.NAMES[which]).apply(); changed();
                }));
        String[] scenes = {"aurora", "cosmos", "horizon"};
        row(R.string.visual_scene, () -> choices(R.string.visual_scene, R.array.visual_scenes,
                java.util.Arrays.asList(scenes).indexOf(prefs.getString("visual_scene", "aurora")), which -> { prefs.edit().putString("visual_scene", scenes[which]).apply(); changed(); }));
        String[] styles = {"glass", "neon", "solid"};
        row(R.string.visual_tiles, () -> choices(R.string.visual_tiles, R.array.visual_tile_styles,
                java.util.Arrays.asList(styles).indexOf(prefs.getString("visual_tile_style", "glass")), which -> { prefs.edit().putString("visual_tile_style", styles[which]).apply(); changed(); }));
        toggle(R.string.visual_motion, "visual_motion", true);
        toggle(R.string.visual_particles, "visual_particles", true);
        toggle(R.string.visual_bloom, "visual_bloom", true);
        toggle(R.string.visual_shimmer, "visual_shimmer", true);
        toggle(R.string.visual_parallax, "visual_parallax", true);
        toggle(R.string.visual_widgets, "visual_widgets", true);
        row(R.string.visual_wallpaper_dim, () -> choices(R.string.visual_wallpaper_dim, R.array.visual_dim_choices,
                Math.max(0, Math.min(3, prefs.getInt("visual_wallpaper_dim", 40) / 20 - 1)), which -> { prefs.edit().putInt("visual_wallpaper_dim", (which + 1) * 20).apply(); changed(); }));
        row(R.string.visual_wallpaper, () -> startActivity(new android.content.Intent(this, PremiumSettingsActivity.class)));
        rows.getChildAt(0).requestFocus();
    }
    private interface Choice { void select(int which); }
    private void choices(int title, int labels, int selected, Choice choice) {
        new LauncherDialogBuilder(this).setTitle(title).setSingleChoiceItems(getResources().getStringArray(labels), selected, (d,w) -> { choice.select(w); d.dismiss(); })
                .setNegativeButton(R.string.action_cancel, null).show();
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(0xFFDCE9FF); view.setPadding(dp(8), dp(8), dp(8), dp(10)); return view;
    }
    private TextView row(int text, Runnable action) {
        TextView view = label(getString(text), 16); view.setFocusable(true); view.setClickable(true);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setMinHeight(dp(48)); view.setBackgroundResource(R.drawable.visual_control);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(7); rows.addView(view, lp);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void toggle(int label, String key, boolean fallback) {
        TextView view = row(label, () -> {});
        Runnable refresh = () -> view.setText(getString(R.string.visual_toggle, getString(label), getString(prefs.getBoolean(key, fallback) ? R.string.on : R.string.off)));
        toggleRefreshes.add(refresh);
        view.setOnClickListener(v -> {
            boolean enabled = !prefs.getBoolean(key, fallback);
            SharedPreferences.Editor edit = prefs.edit().putBoolean(key, enabled);
            if (key.equals("visual_motion") && enabled) {
                edit.putInt("visual_quality", Math.max(1, prefs.getInt("visual_quality", 2)));
                getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit().putBoolean("animations", true).apply();
            }
            edit.apply(); changed(); refresh.run();
        }); refresh.run();
    }
    private void changed() { PremiumPreferences.changed(this); preview.configure(); for (Runnable refresh : toggleRefreshes) refresh.run(); }
    @Override protected void onResume() { super.onResume(); if (preview != null) { preview.configure(); preview.setActive(true); } }
    @Override protected void onPause() { if (preview != null) preview.setActive(false); super.onPause(); }
}
