package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

final class VisualPreferences {
    private VisualPreferences() {}
    /** The requested visual upgrade starts in full mode once; subsequent owner choices are respected. */
    static void migrate(Context context) {
        SharedPreferences p = PremiumPreferences.get(context);
        if (p.contains("visual_version")) return;
        p.edit().putInt("visual_version", 1).putString("visual_palette", "cosmos").putString("visual_scene", "aurora")
                .putInt("visual_quality", 2).putBoolean("visual_motion", true).apply();
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("animations", true).putBoolean("advanced_effects", true).apply();
    }
}
