package com.gathin.tvhome;

/** Monotonic, process-local session timer. Never sets an alarm over another application. */
final class SessionTimer {
    private long deadline;
    private boolean notified;
    synchronized void start(long now, int minutes) {
        if (minutes < 1 || minutes > 180 || now < 0) throw new IllegalArgumentException("Invalid duration");
        deadline = now + minutes * 60_000L; notified = false;
    }
    synchronized void stop() { deadline = 0; notified = false; }
    synchronized long remaining(long now) { return deadline == 0 ? 0 : Math.max(0, deadline - now); }
    synchronized boolean running(long now) { return remaining(now) > 0; }
    synchronized boolean consumeFinished(long now) {
        if (deadline == 0 || now < deadline || notified) return false;
        notified = true; return true;
    }
    static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
