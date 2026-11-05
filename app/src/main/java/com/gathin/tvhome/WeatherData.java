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
        if (!Double.isFinite(temperature) || temperature < -100 || temperature > 80 || !Double.isFinite(wind) || wind < 0 || wind > 540)
            throw new IllegalArgumentException("Invalid weather");
        return String.format(Locale.getDefault(), "%+.0f °C · %s · ветер %.0f км/ч", temperature, code(c.getInt("weather_code")), wind);
    }
    static String yandex(String json) throws Exception {
        JSONObject fact = new JSONObject(json).getJSONObject("fact");
        double temperature = fact.getDouble("temp"), wind = fact.getDouble("wind_speed");
        if (!Double.isFinite(temperature) || temperature < -100 || temperature > 80
                || !Double.isFinite(wind) || wind < 0 || wind > 150) throw new IllegalArgumentException("Invalid weather");
        return String.format(Locale.forLanguageTag("ru-RU"), "%+.0f °C · %s · ветер %.0f м/с", temperature,
                condition(fact.getString("condition")), wind);
    }
    static String condition(String value) {
        switch (value) {
            case "clear": return "Ясно";
            case "partly-cloudy": return "Малооблачно";
            case "cloudy": return "Облачно с прояснениями";
            case "overcast": return "Пасмурно";
            case "drizzle": return "Морось";
            case "light-rain": return "Небольшой дождь";
            case "rain": return "Дождь";
            case "moderate-rain": return "Умеренный дождь";
            case "heavy-rain": case "continuous-heavy-rain": return "Сильный дождь";
            case "showers": return "Ливень";
            case "wet-snow": return "Дождь со снегом";
            case "light-snow": return "Небольшой снег";
            case "snow": return "Снег";
            case "snow-showers": return "Снегопад";
            case "hail": return "Град";
            case "thunderstorm": return "Гроза";
            case "thunderstorm-with-rain": return "Дождь с грозой";
            case "thunderstorm-with-hail": return "Гроза с градом";
            default: return "Без описания";
        }
    }
    static List<City> presets() {
        List<City> cities = new ArrayList<>();
        cities.add(new City("Москва, Россия", 55.7558, 37.6173));
        cities.add(new City("Санкт-Петербург, Россия", 59.9386, 30.3141));
        cities.add(new City("Новосибирск, Россия", 55.0302, 82.9204));
        cities.add(new City("Екатеринбург, Россия", 56.8389, 60.6057));
        cities.add(new City("Казань, Россия", 55.7961, 49.1064));
        cities.add(new City("Нижний Новгород, Россия", 56.3269, 44.0059));
        cities.add(new City("Краснодар, Россия", 45.0355, 38.9753));
        cities.add(new City("Сочи, Россия", 43.5855, 39.7231));
        cities.add(new City("Ростов-на-Дону, Россия", 47.2357, 39.7015));
        cities.add(new City("Самара, Россия", 53.1959, 50.1002));
        cities.add(new City("Уфа, Россия", 54.7388, 55.9721));
        cities.add(new City("Красноярск, Россия", 56.0153, 92.8932));
        cities.add(new City("Воронеж, Россия", 51.6608, 39.2003));
        cities.add(new City("Пермь, Россия", 58.0105, 56.2502));
        cities.add(new City("Волгоград, Россия", 48.708, 44.5133));
        cities.add(new City("Омск, Россия", 54.9885, 73.3242));
        cities.add(new City("Челябинск, Россия", 55.1644, 61.4368));
        cities.add(new City("Калининград, Россия", 54.7104, 20.4522));
        cities.add(new City("Владивосток, Россия", 43.1155, 131.8855));
        cities.add(new City("Минск, Беларусь", 53.9006, 27.559));
        cities.add(new City("Алматы, Казахстан", 43.2389, 76.8897));
        return cities;
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
