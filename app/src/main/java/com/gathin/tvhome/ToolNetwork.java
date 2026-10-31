package com.gathin.tvhome;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** Bounded HTTPS-only requests; TLS and hostname verification use Android defaults. */
final class ToolNetwork implements AutoCloseable {
    private volatile HttpsURLConnection active;
    private volatile boolean closed;
    private HttpsURLConnection connect(String address) throws Exception {
        if (closed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        HttpsURLConnection c = (HttpsURLConnection) new URL(address).openConnection();
        active = c;
        if (closed) { c.disconnect(); throw new java.io.InterruptedIOException(); }
        c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("User-Agent", "MetoxTV/3.4.1");
        return c;
    }
    String json(String address) throws Exception {
        HttpsURLConnection c = connect(address);
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = c.getInputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = stream.read(buffer)) >= 0) {
                    if (closed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (bytes.size() + n > 1024 * 1024) throw new java.io.IOException("Response too large");
                    bytes.write(buffer, 0, n);
                }
            }
            return bytes.toString("UTF-8");
        } finally { c.disconnect(); active = null; }
    }
    long[] download(int requested) throws Exception {
        HttpsURLConnection c = connect("https://speed.cloudflare.com/__down?bytes=" + requested);
        long start = System.nanoTime(); int bytes = 0;
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            long first = System.nanoTime();
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (closed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (bytes + n > requested) throw new java.io.IOException("Response too large");
                    bytes += n;
                    if (System.nanoTime() - start > 20_000_000_000L) throw new java.io.IOException("Test timed out");
                }
            }
            if (bytes != requested) throw new java.io.IOException("Incomplete download");
            return new long[]{bytes, Math.max(1, (System.nanoTime() - first) / 1_000_000), (first - start) / 1_000_000};
        } finally { c.disconnect(); active = null; }
    }
    @Override public void close() { closed = true; HttpsURLConnection c = active; if (c != null) c.disconnect(); }
}
