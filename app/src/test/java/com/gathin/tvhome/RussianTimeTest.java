package com.gathin.tvhome;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

public class RussianTimeTest {
    private final long now = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    @Test public void minuteFormsAreRussian() {
        assertEquals("1 минуту назад",RussianTime.describe(now-60000,now,zone));
        assertEquals("2 минуты назад",RussianTime.describe(now-120000,now,zone));
        assertEquals("5 минут назад",RussianTime.describe(now-300000,now,zone));
        assertEquals("11 минут назад",RussianTime.describe(now-660000,now,zone));
        assertEquals("21 минуту назад",RussianTime.describe(now-1260000,now,zone));
    }
    @Test public void daysAndTimeUseRussianAnd24HourClock() {
        assertEquals("сегодня, 13:00",RussianTime.describe(now-7200000,now,zone));
        assertEquals("вчера, 15:00",RussianTime.describe(now-86400000,now,zone));
        assertEquals("2 дня назад, 15:00",RussianTime.describe(now-172800000,now,zone));
        assertEquals("5 дней назад, 15:00",RussianTime.describe(now-432000000,now,zone));
    }
    @Test public void futureClockValuesDoNotProduceEnglishOrNegativeDurations() {
        assertEquals("1 минуту назад",RussianTime.describe(now+60000,now,zone));
        assertEquals("дней",RussianTime.form(12,"день","дня","дней"));
        assertEquals("день",RussianTime.form(21,"день","дня","дней"));
    }
}
