package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;

/** Dialogs honour the same independent sound switches as the launcher. */
final class LauncherDialogBuilder extends AlertDialog.Builder {
    private final Activity activity;
    LauncherDialogBuilder(Activity activity) { super(activity); this.activity = activity; }
    @Override public AlertDialog create() {
        AlertDialog dialog = super.create();
        final LauncherSounds[] sound = {null};
        dialog.setOnShowListener(d -> {
            sound[0] = new LauncherSounds(activity);
            LauncherSounds.muteNative(dialog.getWindow().getDecorView());
        });
        dialog.setOnKeyListener((d, code, event) -> { if (sound[0] != null) sound[0].key(event); return false; });
        dialog.setOnDismissListener(d -> { if (sound[0] != null) sound[0].close(); });
        return dialog;
    }
}
