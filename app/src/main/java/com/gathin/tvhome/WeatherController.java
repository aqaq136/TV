package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One foreground-only request, cached for 30 minutes; failures retry after five minutes. */
final class WeatherController {
    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final java.util.function.Supplier<ToolNetwork> connections;
    private ToolNetwork network;
    private boolean active, closed;
    private final Runnable poll = new Runnable() {
        public void run() { if (!active) return; refresh(false); changed.run(); main.postDelayed(this, 60_000); }
    };
    WeatherController(Context context, Runnable changed) { this(context, changed, ToolNetwork::new); }
    WeatherController(Context context, Runnable changed, java.util.function.Supplier<ToolNetwork> connections) {
        this.context = context.getApplicationContext(); this.changed = changed; this.connections = connections;
        WeatherStore.useFreeSources(this.context);
    }
    void resume() { if (closed) return; active = true; main.removeCallbacks(poll); main.post(poll); }
    void pause() {
        active = false; main.removeCallbacksAndMessages(null);
        if (network != null) network.close(); network = null;
    }
    void close() { closed = true; pause(); worker.shutdownNow(); }
    boolean loading() { return network != null; }
    void refresh(boolean force) {
        if (!active || closed || network != null) return;
        SharedPreferences p = PremiumPreferences.get(context);
        WeatherData.City city = WeatherStore.city(context);
        if (city == null || p.getBoolean("weather_hidden", false)) { changed.run(); return; }
        long now = System.currentTimeMillis();
        long received = p.getLong("weather_time", 0);
        if (received > 0 && now >= received && now < p.getLong("weather_next_refresh", 0) && !p.getString("weather_summary", "").isEmpty()) { changed.run(); return; }
        if (!force && !WeatherPolicy.due(now, p.getLong("weather_time", 0), p.getLong("weather_attempt", 0), !p.getString("weather_error", "").isEmpty())) return;
        String identity = WeatherStore.identity(context);
        ToolNetwork request = connections.get(); network = request;
        changed.run();
        worker.execute(() -> {
            FreeWeather.Reading reading = null; String error = null;
            try { reading = FreeWeather.load(request, city, System.currentTimeMillis()); }
            catch (Exception e) { error = "Не удалось получить погоду от основного и запасного источников. " + WeatherPolicy.failure(e, false); }
            finally { request.close(); }
            FreeWeather.Reading value = reading; String failure = error;
            main.post(() -> {
                if (network != request) return;
                network = null;
                if (!active || closed) return;
                if (identity.equals(WeatherStore.identity(context))) {
                    SharedPreferences.Editor edit = p.edit().putLong("weather_attempt", System.currentTimeMillis());
                    if (value != null) edit.putString("weather_summary", value.summary).putLong("weather_time", System.currentTimeMillis())
                            .putString("weather_source", value.source).putLong("weather_forecast_time", value.forecastTime)
                            .putLong("weather_next_refresh", value.expires).remove("weather_error");
                    else edit.putString("weather_error", failure);
                    edit.apply();
                }
                changed.run();
            });
        });
    }
}
