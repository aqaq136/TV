package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionTimerTest {
    @Test public void usesElapsedTimeAndFinishesOnlyOnce() {
        SessionTimer timer = new SessionTimer(); timer.start(1000, 15);
        assertEquals(900000, timer.remaining(1000)); assertTrue(timer.running(2000));
        assertFalse(timer.consumeFinished(900999)); assertTrue(timer.consumeFinished(901000));
        assertFalse(timer.consumeFinished(901001)); assertFalse(timer.running(901001));
    }
    @Test public void cancelRemovesTheReminder() {
        SessionTimer timer = new SessionTimer(); timer.start(100, 1); timer.stop();
        assertFalse(timer.running(101)); assertFalse(timer.consumeFinished(600100));
    }
    @Test public void restartReplacesThePreviousDeadline() {
        SessionTimer timer = new SessionTimer(); timer.start(100, 1); timer.start(1000, 2);
        assertEquals(60000, timer.remaining(61000)); assertTrue(timer.consumeFinished(121000));
        timer.start(200000, 1); assertTrue(timer.consumeFinished(260000));
    }
    @Test public void formatRoundsUpAndDoesNotGoNegative() {
        assertEquals("00:01", SessionTimer.format(1)); assertEquals("25:00", SessionTimer.format(1500000)); assertEquals("00:00", SessionTimer.format(-1000));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsZeroDuration() { new SessionTimer().start(0,0); }
    @Test(expected = IllegalArgumentException.class) public void rejectsHugeDuration() { new SessionTimer().start(0,181); }
}
