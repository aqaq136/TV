package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collections;

public class AppCategoriesTest {
    @Test public void ownerChoiceWinsOverAndroidMetadata() { assertEquals(2,AppCategories.resolve("org.video",2,AppCategories.parse("{\"org.video\":2}"))); }
    @Test public void mapsPlatformCategories() {
        assertEquals(3,AppCategories.resolve("org.a",0,Collections.emptyMap()));
        assertEquals(2,AppCategories.resolve("org.a",1,Collections.emptyMap()));
        assertEquals(1,AppCategories.resolve("org.a",2,Collections.emptyMap()));
        assertEquals(4,AppCategories.resolve("org.a",-1,Collections.emptyMap()));
    }
    @Test public void supportsOlderMediaPackages() { assertEquals(1,AppCategories.resolve("ru.kinopoisk",-1,Collections.emptyMap())); assertEquals(2,AppCategories.resolve("org.radio",-1,Collections.emptyMap())); }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidPackage() { AppCategories.parse("{\"../bad\":1}"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsStringCategory() { AppCategories.parse("{\"org.a\":\"1\"}"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnknownCategory() { AppCategories.parse("{\"org.a\":99}"); }
}
