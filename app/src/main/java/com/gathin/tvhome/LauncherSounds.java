package com.gathin.tvhome;

import android.app.Activity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/** Independent move/click volumes; never intercepts an application's own audio. */
final class LauncherSounds implements AutoCloseable {
    private final Activity activity;
    private final ToneGenerator tone;
    private long previous;
    LauncherSounds(Activity activity) {
        this.activity = activity;
        int volume = PremiumPreferences.get(activity).getInt("sound_volume", 25);
        ToneGenerator candidate = null;
        try { candidate = new ToneGenerator(AudioManager.STREAM_SYSTEM, volume); } catch (RuntimeException ignored) {}
        tone = candidate;
    }
    static void muteNative(View v) {
        v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                public void onChildViewAdded(View parent, View child) { muteNative(child); }
                public void onChildViewRemoved(View parent, View child) {}
            });
            for (int i = 0; i < group.getChildCount(); i++) muteNative(group.getChildAt(i));
        }
    }
    void key(KeyEvent event) {
        if (tone == null || event.getAction() != KeyEvent.ACTION_DOWN) return;
        int code = event.getKeyCode();
        boolean move = code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean click = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        android.content.SharedPreferences prefs = PremiumPreferences.get(activity);
        if (!(move && prefs.getBoolean("sound_move", true) || click && prefs.getBoolean("sound_click", true))) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - previous < 90) return;
        previous = now;
        try { tone.startTone(move ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_ACK, move ? 20 : 35); }
        catch (RuntimeException ignored) { /* Audio service may be unavailable during TV startup. */ }
    }
    @Override public void close() { if (tone != null) tone.release(); }
}
