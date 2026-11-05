package com.gathin.tvhome;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A real, bounded cleanup; reports system measurements, never a fabricated reclaimed amount. */
public final class MemoryActivity extends ToolsActivityBase {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status, button;
    private boolean active, busy, started;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(dp(40), dp(28), dp(40), dp(28)); rows.setBackgroundColor(0xFF080F25); setContentView(rows);
        text(rows, "Очистка ОЗУ", 28);
        status = text(rows, "Свободно: " + mb(MemoryCleaner.available(this)), 23); status.setId(R.id.memoryStatus);
        text(rows, "Освобождает кэш изображений лаунчера. На Android 8–13 дополнительно запрашивает завершение доступных кэшированных процессов пользовательских приложений. Активные приложения и службы не выбираются.", 17);
        button = text(rows, "Очистить ОЗУ", 20); button.setId(R.id.memoryClean); button.setFocusable(true); button.setClickable(true); button.setPadding(dp(18), dp(16), dp(18), dp(16)); button.setBackgroundResource(R.drawable.erokhin_focus_tile); button.setOnClickListener(v -> clean()); button.requestFocus();
        text(rows, "Android сам управляет памятью; часть процессов может запуститься снова. Изменение свободной ОЗУ показывает системный замер и не гарантирует ускорения. На Android 14+ доступна очистка кэша самого лаунчера.", 16);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout rows, String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setPadding(0, dp(10), 0, dp(16)); rows.addView(v); return v;
    }
    private String mb(long bytes) { return (bytes / (1024 * 1024)) + " МБ"; }
    private void clean() {
        if (busy || !active) return;
        busy = true; button.setText("Очистка…");
        long before = MemoryCleaner.available(this); boolean released = MemoryCleaner.releaseHomeCache();
        worker.execute(() -> {
            int requested = 0;
            try { requested = MemoryCleaner.requestCachedProcesses(getApplicationContext()); }
            catch (RuntimeException ignored) { /* Restricted firmware still permits releasing our own cache. */ }
            Runtime.getRuntime().gc();
            int count = requested;
            main.postDelayed(() -> {
                if (!active || isDestroyed()) { busy = false; return; }
                long after = MemoryCleaner.available(this);
                status.setText((released ? "Кэш лаунчера освобождён." : "Кэш лаунчера уже свободен или недоступен.")
                        + "\nЗапросов завершения фоновых приложений: " + count
                        + "\nСвободно до: " + mb(before) + " · после: " + mb(after)
                        + "\nИзменение свободной ОЗУ: " + MemoryCleanupPolicy.change(before, after));
                button.setText("Очистить ещё раз"); busy = false;
            }, 1000);
        });
    }
    @Override protected void onResume() {
        super.onResume(); active = true;
        if (!started) { started = true; main.postDelayed(this::clean, 500); }
    }
    @Override protected void onPause() { active = false; super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
}
