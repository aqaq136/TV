package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;

public class FreeWeatherTest {
    private static final long NOW = java.time.Instant.parse("2026-11-05T12:20:00Z").toEpochMilli();
    private static final String CURRENT = "{\"current\":{\"temperature_2m\":-2,\"weather_code\":73,\"wind_speed_10m\":12}}";
    static String met(String time, String temp) {
        return "{\"properties\":{\"meta\":{\"units\":{\"air_temperature\":\"celsius\",\"wind_speed\":\"m/s\"}},\"timeseries\":[{\"time\":\"" + time + "\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":" + temp + ",\"wind_speed\":3}},\"next_1_hours\":{\"summary\":{\"symbol_code\":\"snowshowers_day\"}}}}]}}";
    }
    static class Reply extends HttpsURLConnection {
        final String body; final int code;
        Reply(URL url, String body, int code) { super(url); this.body = body; this.code = code; }
        public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
        public int getResponseCode() { return code; }
        public java.io.InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        public String getCipherSuite() { return "fixture"; }
        public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        public java.security.cert.Certificate[] getServerCertificates() { return null; }
    }
    @Test public void parsesNearbyForecastAndKeepsItsTime() throws Exception {
        FreeWeather.Reading r = FreeWeather.met(met("2026-11-05T12:00:00Z", "-2"), NOW);
        assertEquals("MET Norway", r.source); assertTrue(r.summary.contains("-2 °C · Снег")); assertTrue(r.summary.contains("3 м/с"));
        assertEquals(java.time.Instant.parse("2026-11-05T12:00:00Z").toEpochMilli(), r.forecastTime);
    }
    @Test(expected=Exception.class) public void staleForecastIsRejected() throws Exception { FreeWeather.met(met("2026-11-01T12:00:00Z", "-2"), NOW); }
    @Test(expected=Exception.class) public void wrongUnitsAreNotShownAsCelsius() throws Exception { FreeWeather.met(met("2026-11-05T12:00:00Z", "-2").replace("celsius", "kelvin"), NOW); }
    @Test(expected=Exception.class) public void nullTemperatureIsNotZeroDegrees() throws Exception { FreeWeather.met(met("2026-11-05T12:00:00Z", "null"), NOW); }
    @Test public void failedPrimaryAutomaticallyUsesIndependentSource() throws Exception {
        java.util.List<String> hosts = new java.util.ArrayList<>();
        try (ToolNetwork net = new ToolNetwork(url -> { hosts.add(url.getHost()); return new Reply(url, CURRENT, url.getHost().equals("api.met.no") ? 503 : 200); })) {
            FreeWeather.Reading r = FreeWeather.load(net, new WeatherData.City("Город", 55, 37), NOW);
            assertEquals("Open-Meteo", r.source); assertTrue(r.summary.contains("-2"));
            assertEquals(java.util.Arrays.asList("api.met.no", "api.open-meteo.com"), hosts);
        }
    }
    @Test public void malformedPrimaryFallsBackInsteadOfShowingPartialWeather() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> new Reply(url, url.getHost().equals("api.met.no") ? "{}" : CURRENT, 200))) {
            assertEquals("Open-Meteo", FreeWeather.load(net, new WeatherData.City("Город", 55, 37), NOW).source);
        }
    }
    @Test public void successfulPrimaryDoesNotSpendAnotherRequest() throws Exception {
        int[] calls = {0};
        try (ToolNetwork net = new ToolNetwork(url -> { calls[0]++; return new Reply(url, met("2026-11-05T12:00:00Z", "5"), 200); })) {
            assertEquals("MET Norway", FreeWeather.load(net, new WeatherData.City("Город", 55, 37), NOW).source); assertEquals(1, calls[0]);
        }
    }
    @Test public void cancellationNeverStartsBackupRequest() throws Exception {
        int[] calls = {0}; ToolNetwork[] holder = new ToolNetwork[1];
        holder[0] = new ToolNetwork(url -> { calls[0]++; holder[0].close(); throw new java.io.InterruptedIOException(); });
        try { FreeWeather.load(holder[0], new WeatherData.City("Город", 55, 37), NOW); fail(); } catch (java.io.InterruptedIOException expected) { assertEquals(1, calls[0]); }
    }
    @Test public void bothFailedSourcesNeverProduceWeather() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> new Reply(url, "{}", 503))) {
            try { FreeWeather.load(net, new WeatherData.City("Город", 55, 37), NOW); fail(); }
            catch (ToolNetwork.HttpException expected) { assertEquals(503, expected.status); assertEquals(1, expected.getSuppressed().length); }
        }
    }
    @Test public void metRequestRoundsCoordinatesAndIdentifiesApplication() throws Exception {
        String url = FreeWeather.metUrl(new WeatherData.City("Город", 55.12345678, -37.12345678));
        assertTrue(url.endsWith("lat=55.1235&lon=-37.1235"));
        try (ToolNetwork net = new ToolNetwork(u -> { Reply r = new Reply(u, "{}", 200); return r; })) { net.json(url); }
    }
    @Test public void snowAndThunderSymbolsUseRussianDescriptions() {
        assertEquals("Гроза", FreeWeather.symbol("heavyrainandthunder_day")); assertEquals("Туман", FreeWeather.symbol("fog"));
    }
}
