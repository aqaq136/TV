package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppLabelsTest {
    @Test public void systemUtilitiesHaveRussianNames() {
        assertEquals("Системная трассировка", AppLabels.knownTitle("com.android.traceur"));
        assertEquals("Настройки телевизора", AppLabels.knownTitle("com.android.tv.settings"));
        assertEquals("Файлы", AppLabels.knownTitle("com.android.documentsui"));
        assertEquals("Видео Google", AppLabels.knownTitle("com.lego.android.tvleanback"));
    }
    @Test public void unrelatedApplicationBrandsKeepTheirOwnLabels() {
        assertNull(AppLabels.knownTitle("com.google.android.youtube.tv"));
        assertNull(AppLabels.knownTitle("org.example.app"));
        assertNull(AppLabels.knownTitle(null));
    }
    @Test public void launcherLocaleIsRussian() {
        assertEquals("ru", LauncherLocale.RUSSIAN.getLanguage());
        assertEquals("RU", LauncherLocale.RUSSIAN.getCountry());
    }
}
