package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FavoriteOrderTest {
    @Test public void savedOrderSurvivesRoundTripAndDeduplicates() {
        assertEquals(Arrays.asList("org.videolan.vlc", "ru.ivi.client"),
                FavoriteOrder.parse(FavoriteOrder.encode(Arrays.asList("org.videolan.vlc", "ru.ivi.client", "org.videolan.vlc"))));
    }
    @Test public void deliberatelyEmptyFavoritesStayEmpty() {
        assertTrue(FavoriteOrder.parse("").isEmpty());
        assertTrue(FavoriteOrder.parse(null).isEmpty());
    }
    @Test public void invalidEntriesCannotTurnIntoPackages() {
        assertEquals(Arrays.asList("com.example.tv"), FavoriteOrder.parse(" ../bad\ncom.example.tv\nnot a package\n"));
    }
    @Test public void movingAtEdgesOrMissingAppLeavesOrderUntouched() {
        List<String> original = Arrays.asList("ru.one.app", "ru.two.app");
        assertEquals(original, FavoriteOrder.move(original, "ru.one.app", -1));
        assertEquals(original, FavoriteOrder.move(original, "ru.two.app", 1));
        assertEquals(original, FavoriteOrder.move(original, "ru.missing.app", -1));
        assertEquals(Arrays.asList("ru.two.app", "ru.one.app"), FavoriteOrder.move(original, "ru.one.app", 1));
        assertEquals("ru.one.app", original.get(0));
    }
    @Test public void corruptOversizedSavedListIsBounded() {
        List<String> packages = new ArrayList<>();
        for (int i = 0; i < 100; i++) packages.add("com.app" + i);
        List<String> restored = FavoriteOrder.parse(FavoriteOrder.encode(packages));
        assertEquals(FavoriteOrder.LIMIT, restored.size());
        assertEquals("com.app0", restored.get(0));
        assertEquals("com.app23", restored.get(23));
    }
}
