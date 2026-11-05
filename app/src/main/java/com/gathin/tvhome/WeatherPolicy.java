package com.gathin.tvhome;

import java.util.Locale;

/** Pure request/cache rules, shared by automatic and manual updates. */
final class WeatherPolicy {
    static final long REFRESH = 30 * 60_000L, RETRY = 5 * 60_000L, STALE = 2 * 60 * 60_000L;
    static boolean due(long now, long success, long attempt, boolean failed) {
        if (failed) return attempt <= 0 || now < attempt || now - attempt >= RETRY;
        return success <= 0 || now < success || now - success >= REFRESH;
    }
    static boolean stale(long now, long updated) { return updated <= 0 || now < updated || now - updated >= STALE; }
    static boolean validKey(String key) { return key != null && key.matches("[A-Za-z0-9_.-]{16,256}"); }
    static String yandexUrl(WeatherData.City city, boolean informer) {
        return "https://api.weather.yandex.ru/v2/" + (informer ? "informers" : "forecast")
                + "?lat=" + city.latitude + "&lon=" + city.longitude + "&lang=ru_RU"
                + (informer ? "" : "&limit=1&hours=false&extra=false");
    }
    static String openMeteoUrl(WeatherData.City city) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + city.latitude + "&longitude=" + city.longitude
                + "&current=temperature_2m,weather_code,wind_speed_10m&wind_speed_unit=kmh&timezone=auto";
    }
    static String yandexPage(WeatherData.City city) {
        return String.format(Locale.US, "https://yandex.ru/pogoda/?lat=%.6f&lon=%.6f", city.latitude, city.longitude);
    }
    static String failure(Exception e, boolean yandex) {
        if (e instanceof ToolNetwork.HttpException) {
            int code = ((ToolNetwork.HttpException)e).status;
            if (yandex && (code == 401 || code == 403)) return "Яндекс отклонил доступ. Проверьте ключ, срок действия, лимит и выбранный тариф API.";
            if (code == 429) return "Сервис ограничил частоту запросов. Следующая попытка через 5 минут.";
            if (code >= 500) return "Сервис погоды временно недоступен. Следующая попытка через 5 минут.";
            return "Сервис погоды вернул ошибку " + code + ". Проверьте настройки источника.";
        }
        if (e instanceof java.net.UnknownHostException) return "Не удалось найти сервер погоды. Проверьте интернет и DNS на телевизоре.";
        if (e instanceof java.net.SocketTimeoutException) return "Сервер погоды не ответил вовремя. Проверьте интернет; повтор через 5 минут.";
        if (e instanceof javax.net.ssl.SSLException) return "Не удалось установить защищённое соединение. Проверьте дату и время телевизора.";
        if (e instanceof org.json.JSONException || e instanceof IllegalArgumentException) return "Сервис прислал неполные или некорректные данные погоды. Сохранённая сводка не изменена.";
        return "Не удалось загрузить погоду. Проверьте интернет. Повтор через 5 минут; сохранённая сводка не изменена.";
    }
}
