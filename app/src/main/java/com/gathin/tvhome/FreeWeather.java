package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.Locale;

/** Two independent public APIs, no credentials. Values are never invented on failure. */
final class FreeWeather {
    static final class Reading {
        final String summary, source;
        final long forecastTime, expires;
        Reading(String summary, String source, long forecastTime, long expires) {
            this.summary = summary; this.source = source; this.forecastTime = forecastTime; this.expires = expires;
        }
    }
    static String metUrl(WeatherData.City city) {
        return String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", city.latitude, city.longitude);
    }
    static Reading load(ToolNetwork net, WeatherData.City city, long now) throws Exception {
        try {
            Reading r = met(net.json(metUrl(city)), now);
            return new Reading(r.summary, r.source, r.forecastTime, net.expires());
        } catch (Exception first) {
            if (net.isClosed() || first instanceof java.io.InterruptedIOException && !(first instanceof java.net.SocketTimeoutException)) throw first;
            // A cancelled request must not start a second connection.
            try {
                return new Reading(WeatherData.current(net.json(WeatherPolicy.openMeteoUrl(city))), "Open-Meteo", 0, 0);
            } catch (Exception second) { second.addSuppressed(first); throw second; }
        }
    }
    static Reading met(String json, long now) throws Exception {
        JSONObject properties = new JSONObject(json).getJSONObject("properties");
        JSONObject units = properties.getJSONObject("meta").getJSONObject("units");
        if (!"celsius".equals(units.getString("air_temperature")) || !"m/s".equals(units.getString("wind_speed")))
            throw new IllegalArgumentException("Unexpected units");
        JSONArray series = properties.getJSONArray("timeseries");
        JSONObject closest = null; long distance = Long.MAX_VALUE, forecastTime = 0;
        for (int i = 0; i < series.length(); i++) {
            JSONObject item = series.getJSONObject(i);
            long time = Instant.parse(item.getString("time")).toEpochMilli(), gap = Math.abs(now - time);
            if (gap < distance) { distance = gap; closest = item.getJSONObject("data"); forecastTime = time; }
        }
        if (closest == null || distance > 90 * 60_000L) throw new IllegalArgumentException("No current forecast");
        JSONObject details = closest.getJSONObject("instant").getJSONObject("details");
        double temp = details.getDouble("air_temperature"), wind = details.getDouble("wind_speed");
        if (!Double.isFinite(temp) || temp < -100 || temp > 80 || !Double.isFinite(wind) || wind < 0 || wind > 150)
            throw new IllegalArgumentException("Invalid forecast");
        String symbol = "";
        for (String period : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
            JSONObject next = closest.optJSONObject(period);
            if (next != null && next.optJSONObject("summary") != null) { symbol = next.getJSONObject("summary").optString("symbol_code", ""); break; }
        }
        return new Reading(String.format(Locale.forLanguageTag("ru-RU"), "%+.0f °C · %s · ветер %.0f м/с", temp, symbol(symbol), wind), "MET Norway", forecastTime, 0);
    }
    static String symbol(String value) {
        if (value.contains("thunder")) return "Гроза";
        if (value.contains("sleet")) return "Дождь со снегом";
        if (value.contains("snow")) return "Снег";
        if (value.contains("rain")) return "Дождь";
        if (value.startsWith("clearsky")) return "Ясно";
        if (value.startsWith("fair")) return "Малооблачно";
        if (value.startsWith("partlycloudy")) return "Облачно с прояснениями";
        if (value.startsWith("cloudy")) return "Пасмурно";
        if (value.startsWith("fog")) return "Туман";
        return "Без описания";
    }
}
