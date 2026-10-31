package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Applies sound choices to the native launcher settings screens too. */
abstract class ToolsActivityBase extends Activity {
    private LauncherSounds sounds;
    @Override protected void onResume() {
        super.onResume();
        sounds = new LauncherSounds(this);
        LauncherSounds.muteNative(getWindow().getDecorView());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (sounds != null) sounds.key(event);
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause() {
        if (sounds != null) { sounds.close(); sounds = null; }
        super.onPause();
    }
}
