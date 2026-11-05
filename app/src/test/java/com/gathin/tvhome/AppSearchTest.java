package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppSearchTest {
    @Test public void matchesCaseAndAccents() {
        assertEquals(0, AppSearch.rank("Café TV", "org.example.tv", "CAFE TV"));
        assertEquals(0, AppSearch.rank("Ёлка", "org.example.tv", "елка"));
    }
    @Test public void findsRussianAppsWithLatinRemoteInput() {
        assertTrue(AppSearch.rank("Кинопоиск", "ru.kinopoisk", "kino") >= 0);
        assertEquals(0, AppSearch.rank("Кинопоиск", "ru.kinopoisk", "kinopoisk"));
        assertTrue(AppSearch.rank("Яндекс Музыка", "org.example.music", "yandeks muzyka") >= 0);
    }
    @Test public void everyWordMustMatchEvenAcrossFields() {
        assertTrue(AppSearch.rank("Music Player", "org.vendor.audio", "player vendor") >= 0);
        assertEquals(-1, AppSearch.rank("Music Player", "org.vendor.audio", "music missing"));
    }
    @Test public void exactThenPrefixThenSubstringThenPackage() {
        assertTrue(AppSearch.rank("TV", "org.a", "tv") < AppSearch.rank("TV Player", "org.b", "tv"));
        assertTrue(AppSearch.rank("TV Player", "org.b", "tv") < AppSearch.rank("My TV", "org.c", "tv"));
        assertTrue(AppSearch.rank("My TV", "org.c", "tv") < AppSearch.rank("Player", "org.tv.app", "tv"));
    }
    @Test public void handlesEmptyInputAndSeparators() {
        assertEquals(0, AppSearch.rank("Any", "org.a", "  "));
        assertEquals("org example app", AppSearch.normalize("ORG.example_App"));
        assertEquals("", AppSearch.normalize(null));
        assertEquals(-1, AppSearch.rank("", "", "a"));
    }
    @Test public void CyrillicTransliterationCoversTheAlphabet() {
        assertEquals("shchuka", AppSearch.transliterate(AppSearch.normalize("Щука")));
        assertEquals("podem", AppSearch.transliterate(AppSearch.normalize("Подъём")));
    }
}
