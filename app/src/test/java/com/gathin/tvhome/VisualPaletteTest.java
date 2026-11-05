package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisualPaletteTest {
    @Test public void everyPaletteIsOpaqueAndHasDistinctAccents() {
        for (String name : VisualPalette.NAMES) {
            VisualPalette p = VisualPalette.of(name);
            assertEquals(255, p.background >>> 24); assertEquals(255, p.primary >>> 24); assertNotEquals(p.primary,p.secondary);
        }
    }
    @Test public void unknownPaletteHasSafeFallback() { assertEquals(VisualPalette.of("cosmos").primary,VisualPalette.of(null).primary); }
    @Test public void blendingIsBounded() {
        assertEquals(0xFF000000,VisualPalette.blend(0xFF000000,0xFFFFFFFF,-1));
        assertEquals(0xFFFFFFFF,VisualPalette.blend(0xFF000000,0xFFFFFFFF,2));
        assertEquals(0xFF808080,VisualPalette.blend(0xFF000000,0xFFFFFFFF,.5f));
    }
    @Test public void alphaKeepsRgbAndClamps() { assertEquals(0x0063DFFF,VisualPalette.alpha(0xFF63DFFF,-1)); assertEquals(0xFF63DFFF,VisualPalette.alpha(0x0063DFFF,999)); }
}
