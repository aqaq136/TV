package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherPolicyTest {
    @Test public void freshCacheDoesNotSpendApiQuota() { assertFalse(WeatherPolicy.due(2_000_000, 1_900_000, 1_900_000, false)); }
    @Test public void failedRequestWaitsFiveMinutesEvenWithEmptyCache() {
        assertFalse(WeatherPolicy.due(2_000_000, 0, 1_999_999, true));
        assertTrue(WeatherPolicy.due(2_300_000, 0, 2_000_000, true));
    }
    @Test public void updateDueAtThirtyMinutesAndAfterClockReset() {
        assertTrue(WeatherPolicy.due(2_800_000, 1_000_000, 1_000_000, false));
        assertTrue(WeatherPolicy.due(500, 1_000_000, 1_000_000, false));
    }
    @Test public void oldOrFutureDatedCacheIsMarkedStale() {
        assertTrue(WeatherPolicy.stale(8_200_000, 1_000_000)); assertTrue(WeatherPolicy.stale(100, 200));
        assertFalse(WeatherPolicy.stale(200, 100));
    }
    @Test public void keyCannotInjectHeadersOrQueryParameters() {
        assertTrue(WeatherPolicy.validKey("12345678-1234-1234-1234-123456789abc"));
        assertFalse(WeatherPolicy.validKey("short")); assertFalse(WeatherPolicy.validKey("abcdefghijklmnop\r\nBad: yes"));
        assertFalse(WeatherPolicy.validKey("abcdefghijklmnop?secret=bad"));
    }
    @Test public void credentialIsNotPartOfRequestUrlAndDecimalIsLocaleIndependent() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"));
            String url = WeatherPolicy.yandexUrl(new WeatherData.City("City", -55.75, 37.62), false);
            assertTrue(url.startsWith("https://api.weather.yandex.ru/v2/forecast?lat=-55.75&lon=37.62"));
            assertFalse(url.contains("key")); assertTrue(url.contains("hours=false"));
        } finally { java.util.Locale.setDefault(previous); }
    }
    @Test public void accessAndConnectivityFailuresGiveDifferentAdvice() {
        assertTrue(WeatherPolicy.failure(new ToolNetwork.HttpException(403), true).contains("ключ"));
        assertTrue(WeatherPolicy.failure(new ToolNetwork.HttpException(429), true).contains("частоту"));
        assertTrue(WeatherPolicy.failure(new java.net.UnknownHostException(), true).contains("DNS"));
        assertTrue(WeatherPolicy.failure(new java.net.SocketTimeoutException(), true).contains("вовремя"));
        assertTrue(WeatherPolicy.failure(new javax.net.ssl.SSLException("private token"), true).contains("дату"));
        assertFalse(WeatherPolicy.failure(new java.io.IOException("secret"), true).contains("secret"));
    }
    @Test public void cityPresetsHaveUniqueTitlesAndValidCoordinates() {
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (WeatherData.City c : WeatherData.presets()) { assertTrue(titles.add(c.title)); assertTrue(Math.abs(c.latitude) <= 90); }
        assertTrue(titles.contains("Москва, Россия")); assertTrue(titles.size() >= 20);
    }
}
