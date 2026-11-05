package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Credentials are local, separate from the exported preferences allowlist. */
final class WeatherStore {
    static void useFreeSources(Context c) {
        SharedPreferences p = PremiumPreferences.get(c);
        if (!p.getBoolean("weather_free_sources_v1", false)) {
            credentials(c).edit().clear().apply(); clearCache(c);
            p.edit().putBoolean("weather_free_sources_v1", true).apply();
        }
    }
    static SharedPreferences credentials(Context c) { return c.getSharedPreferences("weather_credentials", Context.MODE_PRIVATE); }
    static String key(Context c) { return credentials(c).getString("yandex_key", ""); }
    static boolean yandex(Context c) { return credentials(c).getBoolean("yandex_enabled", !key(c).isEmpty()) && !key(c).isEmpty(); }
    static WeatherData.City city(Context c) {
        SharedPreferences p = PremiumPreferences.get(c);
        try { return new WeatherData.City(p.getString("weather_city", "Город"), Double.parseDouble(p.getString("latitude", "")), Double.parseDouble(p.getString("longitude", ""))); }
        catch (RuntimeException e) { return null; }
    }
    static String identity(Context c) {
        SharedPreferences p = PremiumPreferences.get(c);
        return p.getString("latitude", "") + ":" + p.getString("longitude", "") + ":" + p.getBoolean("weather_hidden", false);
    }
    static void clearCache(Context c) {
        PremiumPreferences.get(c).edit().remove("weather_summary").remove("weather_time").remove("weather_source")
                .remove("weather_attempt").remove("weather_error").remove("weather_next_refresh").remove("weather_forecast_time").apply();
    }
    static void select(Context c, WeatherData.City city) {
        PremiumPreferences.get(c).edit().putString("weather_city", city.title).putString("latitude", Double.toString(city.latitude))
                .putString("longitude", Double.toString(city.longitude)).putBoolean("weather_hidden", false).apply();
        clearCache(c);
    }
    static String summary(Context c, boolean detail) {
        SharedPreferences p = PremiumPreferences.get(c);
        if (p.getBoolean("weather_hidden", false)) return "Погода скрыта · открыть настройки →";
        if (city(c) == null) return "Выбрать город →";
        String city = p.getString("weather_city", "Город").split(",")[0];
        String weather = p.getString("weather_summary", ""), error = p.getString("weather_error", "");
        if (weather.isEmpty()) return city + "\n" + (error.isEmpty() ? "Ожидание загрузки…" : detail ? error : "Не удалось обновить · подробнее →");
        long time = p.getLong("weather_time", 0);
        String source = p.getString("weather_source", "Open-Meteo");
        String age = WeatherPolicy.stale(System.currentTimeMillis(), time) ? "Устарело · " : "";
        String saved = android.text.format.DateFormat.format("dd.MM HH:mm", time).toString();
        if (!detail) return city + " · " + weather.split(" · ")[0] + "\n" + source + " · " + age + saved + (error.isEmpty() ? "" : " · сбой");
        long forecast = p.getLong("weather_forecast_time", 0);
        return city + " · " + weather + "\nПолучено: " + age + saved
                + (forecast == 0 ? "" : " · прогноз на " + android.text.format.DateFormat.format("HH:mm", forecast))
                + (error.isEmpty() ? "" : detail ? "\n" + error : " · ошибка обновления");
    }
}
