package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;


/**
 * When an app was last opened from Home, for the cinematic shelf's "Last opened" line: the launcher notes the
 * time whenever it opens an app. No permission and nothing read from other apps, so an app started some other
 * way (a remote's own Netflix button) shows the last time Home opened it.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";

    private LastOpened() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        if (pkg == null || !PremiumPreferences.get(ctx).getBoolean("history_enabled", true)) return;
        prefs(ctx).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    static void clear(Context ctx) { prefs(ctx).edit().clear().apply(); }

    /** Milliseconds since the epoch, or 0 when Home never opened the app. */
    static long get(Context ctx, String pkg) {
        return pkg == null ? 0L : prefs(ctx).getLong(pkg, 0L);
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        return RussianTime.describe(when, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
