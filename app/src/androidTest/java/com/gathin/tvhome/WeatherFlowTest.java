package com.gathin.tvhome;

import android.content.Context;
import android.app.Instrumentation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.URL;
import java.io.ByteArrayInputStream;
import javax.net.ssl.HttpsURLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Deterministic HTTPS fixtures verify request handling, not a live provider claim. */
@RunWith(AndroidJUnit4.class)
public class WeatherFlowTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private final Context context = ins.getTargetContext();
    private WeatherController controller;
    private static final String JSON = "{\"fact\":{\"temp\":20,\"condition\":\"overcast\",\"wind_speed\":2}}";
    @Before public void setup() {
        PremiumPreferences.get(context).edit().clear().apply();
        WeatherStore.credentials(context).edit().clear().putString("yandex_key", "test-key-not-a-real-credential").putBoolean("yandex_enabled", true).apply();
        WeatherStore.select(context, new WeatherData.City("Первый город", 55, 37));
    }
    @After public void cleanup() {
        if (controller != null) ins.runOnMainSync(controller::close);
        PremiumPreferences.get(context).edit().remove("latitude").remove("longitude").remove("weather_city").apply();
        WeatherStore.clearCache(context); WeatherStore.credentials(context).edit().clear().apply();
    }
    static class Response extends HttpsURLConnection {
        final int status; final CountDownLatch opened, release;
        Response(URL url, int status, CountDownLatch opened, CountDownLatch release) { super(url); this.status = status; this.opened = opened; this.release = release; }
        public void connect() {} public boolean usingProxy() { return false; }
        public void disconnect() { if (release != null) release.countDown(); }
        public int getResponseCode() { return status; }
        public java.io.InputStream getInputStream() throws java.io.IOException {
            if (opened != null) opened.countDown();
            if (release != null) try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Fixture timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
            return new ByteArrayInputStream(JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        public String getCipherSuite() { return "test"; }
        public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        public java.security.cert.Certificate[] getServerCertificates() { return null; }
    }
    private CountDownLatch start(int code, CountDownLatch opened, CountDownLatch release, java.util.concurrent.atomic.AtomicInteger calls) {
        CountDownLatch done = new CountDownLatch(1);
        ins.runOnMainSync(() -> {
            controller = new WeatherController(context, () -> { if (!controller.loading()) done.countDown(); },
                    () -> new ToolNetwork(url -> { calls.incrementAndGet(); return new Response(url, code, opened, release); }));
            controller.resume();
        });
        return done;
    }
    @Test public void successfulYandexResponseIsCachedAnd403PreservesIt() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch done = start(200, null, null, calls); assertTrue(done.await(5, TimeUnit.SECONDS)); ins.waitForIdleSync();
        assertEquals("Яндекс Погода", PremiumPreferences.get(context).getString("weather_source", ""));
        String previous = PremiumPreferences.get(context).getString("weather_summary", ""); assertTrue(previous.contains("+20 °C"));
        long time = PremiumPreferences.get(context).getLong("weather_time", 0); assertTrue(time > 0);
        ins.runOnMainSync(controller::close);
        // Make the cache due, then reject access at the provider.
        PremiumPreferences.get(context).edit().putLong("weather_time", time - WeatherPolicy.REFRESH).apply();
        done = start(403, null, null, calls); assertTrue(done.await(5, TimeUnit.SECONDS)); ins.waitForIdleSync();
        assertEquals(previous, PremiumPreferences.get(context).getString("weather_summary", ""));
        assertEquals(time - WeatherPolicy.REFRESH, PremiumPreferences.get(context).getLong("weather_time", 0));
        assertTrue(PremiumPreferences.get(context).getString("weather_error", "").contains("ключ"));
        ins.runOnMainSync(() -> controller.refresh(false)); ins.waitForIdleSync(); assertEquals(2, calls.get());
    }
    @Test public void changingCityDiscardsAnOlderInFlightResponse() throws Exception {
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch done = start(200, opened, release, new java.util.concurrent.atomic.AtomicInteger());
        assertTrue(opened.await(5, TimeUnit.SECONDS));
        WeatherStore.select(context, new WeatherData.City("Второй город", 60, 30)); release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS)); ins.waitForIdleSync();
        assertEquals("Второй город", PremiumPreferences.get(context).getString("weather_city", ""));
        assertFalse(PremiumPreferences.get(context).contains("weather_summary"));
    }
    @Test public void leavingHomeCancelsAndCannotSaveAPartialResponse() throws Exception {
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        start(200, opened, release, calls); assertTrue(opened.await(5, TimeUnit.SECONDS));
        ins.runOnMainSync(() -> { controller.refresh(true); controller.pause(); controller.refresh(true); assertFalse(controller.loading()); });
        assertTrue(release.await(2, TimeUnit.SECONDS)); android.os.SystemClock.sleep(200); ins.waitForIdleSync();
        assertEquals(1, calls.get()); assertFalse(PremiumPreferences.get(context).contains("weather_summary"));
        assertFalse(BackupArchive.KEYS.containsKey("weather_credentials"));
        assertFalse(BackupArchive.KEYS.get(BackupArchive.PREMIUM).contains("yandex_key"));
    }
}
