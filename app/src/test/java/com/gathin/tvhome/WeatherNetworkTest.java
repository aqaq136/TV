package com.gathin.tvhome;

import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherNetworkTest {
    private static final String KEY = "test-key-not-a-real-credential";
    static final class Response extends HttpsURLConnection {
        int status = 200; byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8); boolean disconnected;
        Response(URL url) { super(url); }
        public void connect() {} public void disconnect() { disconnected = true; } public boolean usingProxy() { return false; }
        public int getResponseCode() { return status; }
        public java.io.InputStream getInputStream() { return new ByteArrayInputStream(body); }
        public String getCipherSuite() { return "test"; }
        public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        public java.security.cert.Certificate[] getServerCertificates() { return null; }
    }
    @Test public void keyTravelsOnlyInYandexHeaderAndRedirectsAreDisabled() throws Exception {
        Response[] seen = new Response[1];
        try (ToolNetwork net = new ToolNetwork(url -> seen[0] = new Response(url))) {
            assertEquals("{}", net.yandex(new WeatherData.City("City", 55, 37), KEY, false));
            assertEquals("api.weather.yandex.ru", seen[0].getURL().getHost());
            assertEquals(KEY, seen[0].getRequestProperty("X-Yandex-Weather-Key"));
            assertFalse(seen[0].getURL().toString().contains(KEY)); assertFalse(seen[0].getInstanceFollowRedirects());
            assertTrue(seen[0].disconnected);
            net.json("https://api.open-meteo.com/v1/forecast"); assertNull(seen[0].getRequestProperty("X-Yandex-Weather-Key"));
        }
    }
    @Test public void nonSuccessIsTypedWithoutIncludingResponseBodyOrKey() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> { Response r = new Response(url); r.status = 403; return r; })) {
            try { net.yandex(new WeatherData.City("City", 55, 37), KEY, false); fail(); }
            catch (ToolNetwork.HttpException e) { assertEquals(403, e.status); assertFalse(e.toString().contains(KEY)); }
        }
    }
    @Test public void redirectIsNotFollowedWithCredential() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> { Response r = new Response(url); r.status = 302; return r; })) {
            try { net.yandex(new WeatherData.City("City", 55, 37), KEY, true); fail(); }
            catch (ToolNetwork.HttpException e) { assertEquals(302, e.status); }
        }
    }
    @Test public void oversizedResponseIsRejected() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> { Response r = new Response(url); r.body = new byte[1024*1024+1]; return r; })) {
            try { net.json("https://api.open-meteo.com"); fail(); } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("large")); }
        }
    }
    @Test public void closedClientDoesNotOpenConnection() throws Exception {
        ToolNetwork net = new ToolNetwork(url -> { fail("Cancelled request must not connect"); return null; }); net.close();
        try { net.json("https://api.open-meteo.com"); fail(); } catch (java.io.InterruptedIOException expected) { }
    }
    @Test public void plainHttpIsRejectedBeforeConnection() throws Exception {
        try (ToolNetwork net = new ToolNetwork(url -> { fail(); return null; })) {
            try { net.json("http://api.open-meteo.com"); fail(); } catch (java.io.IOException expected) { assertEquals("HTTPS required", expected.getMessage()); }
        }
    }
}
