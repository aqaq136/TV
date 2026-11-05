package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit export, preview, confirmation and a local undo copy. Nothing leaves the TV automatically. */
public final class BackupActivity extends ToolsActivityBase {
    private static final int EXPORT = 201, IMPORT = 202;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF080F25);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(20), dp(40), dp(24)); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText(R.string.backup_title); title.setTextColor(Color.WHITE); title.setTextSize(27); root.addView(title);
        status = new TextView(this); status.setText(R.string.backup_description); status.setTextSize(17); status.setTextColor(0xFFC2DAFF);
        status.setPadding(0, dp(14), 0, dp(18)); root.addView(status);
        row(root, R.string.backup_export, () -> pick(true));
        row(root, R.string.backup_import, () -> pick(false));
        row(root, R.string.backup_undo, () -> {
            if (busy) return;
            if (!BackupStore.hasRecovery(this)) { status.setText(R.string.backup_no_recovery); return; }
            work(R.string.backup_reading, () -> {
                BackupArchive.Data data = BackupStore.recovery(getApplicationContext());
                BackupStore.validateImage(data); show(() -> preview(data));
            });
        });
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private void row(LinearLayout parent, int label, Runnable action) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(18); view.setTextColor(Color.WHITE);
        view.setMinHeight(dp(56)); view.setPadding(dp(18), dp(12), dp(18), dp(12));
        view.setFocusable(true); view.setClickable(true); view.setBackgroundResource(R.drawable.erokhin_focus_tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); parent.addView(view, lp);
        view.setOnClickListener(v -> { if (!busy) action.run(); });
    }
    private void pick(boolean export) {
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(export ? "application/zip" : "*/*");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, "MetoxTV-backup-" + java.time.LocalDate.now() + ".zip");
        try { startActivityForResult(intent, export ? EXPORT : IMPORT); }
        catch (ActivityNotFoundException | SecurityException e) { status.setText(R.string.backup_picker_missing); }
    }
    private interface Work { void run() throws Exception; }
    private void work(int label, Work operation) {
        busy = true; status.setText(label);
        worker.execute(() -> {
            try { operation.run(); }
            catch (Exception e) { show(() -> status.setText(R.string.backup_failed)); }
            finally { show(() -> busy = false); }
        });
    }
    private void show(Runnable action) { runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) action.run(); }); }
    private void preview(BackupArchive.Data data) {
        int count = 0;
        for (java.util.Map<String, Object> group : data.preferences.values()) count += group.size();
        new LauncherDialogBuilder(this).setTitle(R.string.backup_confirm_title)
                .setMessage(getString(R.string.backup_confirm, count, data.files.size()))
                .setPositiveButton(R.string.backup_restore, (d, w) -> work(R.string.backup_restoring, () -> {
                    BackupStore.restore(getApplicationContext(), data);
                    show(() -> status.setText(R.string.backup_restored));
                })).setNegativeButton(R.string.action_cancel, (d, w) -> status.setText(R.string.backup_cancelled)).show();
        status.setText(R.string.backup_valid);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == EXPORT) work(R.string.backup_saving, () -> {
            BackupArchive.Data snapshot = BackupStore.snapshot(getApplicationContext());
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Cannot open destination"); BackupArchive.write(out, snapshot);
            }
            show(() -> status.setText(R.string.backup_saved));
        });
        if (request == IMPORT) work(R.string.backup_reading, () -> {
            BackupArchive.Data snapshot;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open source");
                byte[] bytes = BackupArchive.readBounded(in, BackupArchive.MAX_ARCHIVE);
                snapshot = BackupArchive.read(new ByteArrayInputStream(bytes));
            }
            BackupStore.validateImage(snapshot); show(() -> preview(snapshot));
        });
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
