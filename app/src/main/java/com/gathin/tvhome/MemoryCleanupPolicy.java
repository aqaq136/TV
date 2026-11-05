package com.gathin.tvhome;

/** Only expendable, cached user processes on Android versions which permit it. */
final class MemoryCleanupPolicy {
    static boolean eligible(int sdk, int importance, int uid, int ownUid, boolean system, boolean protectedPackage) {
        return sdk < 34 && importance >= 400 && uid >= 10_000 && uid != ownUid && !system && !protectedPackage;
    }
    static String change(long before, long after) {
        long delta = (after - before) / (1024 * 1024);
        return (delta >= 0 ? "+" : "−") + Math.abs(delta) + " МБ";
    }
}
