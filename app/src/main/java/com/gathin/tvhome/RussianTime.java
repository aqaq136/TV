package com.gathin.tvhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Russian relative dates without firmware-owned English phrases. */
final class RussianTime {
    private RussianTime() {}
    static String form(long value, String one, String few, String many) {
        long hundred = Math.abs(value % 100), ten = Math.abs(value % 10);
        if (hundred >= 11 && hundred <= 14) return many;
        if (ten == 1) return one;
        return ten >= 2 && ten <= 4 ? few : many;
    }
    static String describe(long when, long now, ZoneId zone) {
        when = Math.min(when, now - 60_000L);
        long elapsed = now - when;
        if (elapsed < 3_600_000L) {
            long minutes = Math.max(1, elapsed / 60_000L);
            return minutes + " " + form(minutes, "минуту", "минуты", "минут") + " назад";
        }
        java.time.ZonedDateTime then = Instant.ofEpochMilli(when).atZone(zone), current = Instant.ofEpochMilli(now).atZone(zone);
        long days = ChronoUnit.DAYS.between(then.toLocalDate(), current.toLocalDate());
        String time = DateTimeFormatter.ofPattern("HH:mm", LauncherLocale.RUSSIAN).format(then);
        if (days == 0) return "сегодня, " + time;
        if (days == 1) return "вчера, " + time;
        if (days < 7) return days + " " + form(days, "день", "дня", "дней") + " назад, " + time;
        return DateTimeFormatter.ofPattern("d MMM, HH:mm", LauncherLocale.RUSSIAN).format(then);
    }
}
