package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TileShapeTest {
    @Test public void changingShapeChangesHeightWithoutChangingWidthAssumptions() {
        assertEquals(101, TileShape.height(180, "wide"));
        assertEquals(180, TileShape.height(180, "square"));
        assertEquals(72, TileShape.height(180, "compact"));
    }
    @Test public void unknownSavedShapeFallsBackToWide() {
        assertEquals(TileShape.height(123, "wide"), TileShape.height(123, "future-shape"));
        assertEquals(TileShape.height(123, "wide"), TileShape.height(123, null));
    }
    @Test public void shrinkingColumnsPreservesSquareAndCompactProportions() {
        assertEquals(80, TileShape.height(80, "square"));
        assertEquals(32, TileShape.height(80, "compact"));
        assertTrue(TileShape.height(0, "square") > 0);
    }
}
