package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class MemoryCleanupPolicyTest {
    @Test public void onlyCachedUserProcessesOnSupportedAndroidAreEligible() {
        assertTrue(MemoryCleanupPolicy.eligible(28, 400, 10123, 10456, false, false));
        assertFalse(MemoryCleanupPolicy.eligible(34, 400, 10123, 10456, false, false));
        assertFalse(MemoryCleanupPolicy.eligible(28, 100, 10123, 10456, false, false));
        assertFalse(MemoryCleanupPolicy.eligible(28, 125, 10123, 10456, false, false));
        assertFalse(MemoryCleanupPolicy.eligible(28, 300, 10123, 10456, false, false));
    }
    @Test public void systemProtectedAndOwnProcessesAreExcluded() {
        assertFalse(MemoryCleanupPolicy.eligible(28, 400, 10123, 10456, true, false));
        assertFalse(MemoryCleanupPolicy.eligible(28, 400, 10123, 10456, false, true));
        assertFalse(MemoryCleanupPolicy.eligible(28, 400, 10456, 10456, false, false));
        assertFalse(MemoryCleanupPolicy.eligible(28, 400, 1000, 10456, false, false));
    }
    @Test public void decreasingOrUnchangedMemoryIsReportedWithoutFakeSavings() {
        assertEquals("−5 МБ", MemoryCleanupPolicy.change(10*1048576L,5*1048576L));
        assertEquals("+0 МБ", MemoryCleanupPolicy.change(10,10));
        assertEquals("+5 МБ", MemoryCleanupPolicy.change(5*1048576L,10*1048576L));
    }
}
