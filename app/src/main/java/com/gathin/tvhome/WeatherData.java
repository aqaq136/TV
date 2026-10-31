package com.gathin.tvhome;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WeatherData {
    static final class City {
        final String title;
        final double latitude, longitude;
        City(String title, double latitude, double longitude) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
                throw new IllegalArgumentException("Invalid coordinates");
            this.title = title; this.latitude = latitude; this.longitude = longitude;
        }
    }
    static List<City> cities(String json) throws Exception {
        JSONArray entries = new JSONObject(json).optJSONArray("results");
        List<City> result = new ArrayList<>();
        if (entries == null) return result;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject c = entries.getJSONObject(i);
            String title = c.getString("name");
            for (String key : new String[]{"admin1", "country"}) {
                String value = c.optString(key, "");
                if (!value.isEmpty() && !value.equals(c.getString("name"))) title += ", " + value;
            }
            result.add(new City(title, c.getDouble("latitude"), c.getDouble("longitude")));
        }
        return result;
    }
    static String current(String json) throws Exception {
        JSONObject c = new JSONObject(json).getJSONObject("current");
        double temperature = c.getDouble("temperature_2m"), wind = c.getDouble("wind_speed_10m");
        if (!Double.isFinite(temperature) || !Double.isFinite(wind)) throw new IllegalArgumentException("Invalid weather");
        return String.format(Locale.getDefault(), "%+.0f °C · %s · ветер %.0f км/ч", temperature, code(c.getInt("weather_code")), wind);
    }
    static String code(int code) {
        if (code == 0) return "Ясно";
        if (code <= 3 && code > 0) return "Облачно";
        if (code == 45 || code == 48) return "Туман";
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return "Дождь";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Снег";
        if (code >= 95 && code <= 99) return "Гроза";
        return "Без описания";
    }
}
