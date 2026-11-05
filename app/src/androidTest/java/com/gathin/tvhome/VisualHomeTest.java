package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual Android TV rendering and remote navigation; screenshots are test output, not mockups. */
@RunWith(AndroidJUnit4.class)
public class VisualHomeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    @Before public void resetVisualPreferences() {
        android.content.Context context = instrumentation.getTargetContext(); VisualPreferences.migrate(context);
        PremiumPreferences.get(context).edit().remove("weather_city").remove("latitude").remove("longitude").remove("weather_hidden").apply();
        WeatherStore.clearCache(context); WeatherStore.credentials(context).edit().clear().apply();
        PremiumPreferences.get(context).edit().putString("visual_palette", "cosmos").putString("visual_scene", "aurora")
                .putInt("visual_quality", 2).putBoolean("visual_motion", true).apply();
        context.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putBoolean("animations", true).apply();
    }
    private Activity start(Class<? extends Activity> type) {
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync(); return activity;
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        File folder = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "screenshots");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); }
        finally { bitmap.recycle(); }
        // UTP removes the tested application after the suite, including its external files directory.
        // Keep test captures in the emulator's shared Download directory before that cleanup.
        shell("mkdir -p /sdcard/Download/MetoxTV-preview");
        shell("cp " + new File(folder, name + ".png").getAbsolutePath() + " /sdcard/Download/MetoxTV-preview/" + name + ".png");
    }
    private void shell(String command) throws Exception {
        try (java.io.InputStream out = new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[1024]; while (out.read(buffer) != -1) { /* Drain to wait for completion. */ }
        }
    }
    @Test public void homeRendersAndRemoteCanReachQuickActions() throws Exception {
        Activity home = start(MainActivity.class);
        SystemClock.sleep(1800);
        screenshot("home-4.0");
        shell("mkdir -p /sdcard/Download/MetoxTV-preview");
        shell("screenrecord --size 1280x720 --bit-rate 1500000 --time-limit 6 /sdcard/Download/MetoxTV-preview/home-effects.mp4");
        instrumentation.runOnMainSync(() -> {
            AtmosphereView atmosphere = home.findViewById(R.id.visualAtmosphere);
            assertNotNull(atmosphere); assertTrue(atmosphere.isRendering());
            assertEquals("ru", home.getResources().getConfiguration().getLocales().get(0).getLanguage());
            assertEquals("Все приложения", home.getString(R.string.visual_all));
            assertEquals("Источники", home.getString(R.string.inputs));
            assertEquals("Отмена", home.getString(R.string.action_cancel));
            assertEquals("5 минут назад", LastOpened.describe(home, System.currentTimeMillis() - 300_000));
            assertEquals("Системная трассировка", AppLabels.knownTitle("com.android.traceur"));
            View hero = home.findViewById(R.id.visualHeroPanel), grid = home.findViewById(R.id.grid), bar = home.findViewById(R.id.visualQuickBar);
            assertTrue(hero.getHeight() > 0); assertTrue(grid.getHeight() >= 80 * home.getResources().getDisplayMetrics().density);
            int[] barLocation = new int[2], gridLocation = new int[2];
            bar.getLocationOnScreen(barLocation); grid.getLocationOnScreen(gridLocation);
            assertTrue(barLocation[1] + bar.getHeight() <= gridLocation[1]);
            RecyclerView recycler = (RecyclerView) grid;
            if (recycler.getChildCount() > 0) {
                View first = recycler.getChildAt(0).findViewById(R.id.card);
                if (first != null && first.isFocusable()) first.requestFocus();
            }
        });
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            View focus = home.getCurrentFocus(); assertNotNull(focus);
            assertEquals(R.id.visualQuickBar, ((View) focus.getParent()).getId());
        });
        screenshot("home-remote-focus");
        Activity studio = start(VisualSettingsActivity.class); SystemClock.sleep(600); screenshot("visual-studio");
        assertEquals("Визуальная студия", studio.getString(R.string.visual_studio));
        instrumentation.runOnMainSync(() -> {
            assertFalse(((AtmosphereView) home.findViewById(R.id.visualAtmosphere)).isRendering());
            studio.finish();
        });
        instrumentation.waitForIdleSync();
        Activity catalog = start(AppCenterActivity.class); SystemClock.sleep(600); screenshot("catalog-russian");
        assertEquals("Поиск и приложения", catalog.getString(R.string.center_title));
        instrumentation.runOnMainSync(catalog::finish); instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(home::finish); instrumentation.waitForIdleSync();
    }
    @Test public void studioChangesPaletteAndStaticModeStopsTheLoop() throws Exception {
        android.content.SharedPreferences prefs = PremiumPreferences.get(instrumentation.getTargetContext());
        prefs.edit().putString("visual_palette", "sunset").putString("visual_scene", "horizon").putInt("visual_quality", 0).apply();
        Activity home = start(MainActivity.class); SystemClock.sleep(700);
        instrumentation.runOnMainSync(() -> assertFalse(((AtmosphereView) home.findViewById(R.id.visualAtmosphere)).isRendering()));
        screenshot("sunset-horizon-static");
        prefs.edit().putString("visual_palette", "cosmos").putString("visual_scene", "aurora").putInt("visual_quality", 2).apply();
        instrumentation.runOnMainSync(home::finish); instrumentation.waitForIdleSync();
    }
    @Test public void weatherSetupAndCachedYandexSourceAreReadableWithRemote() throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        Activity weather = start(WeatherActivity.class);
        instrumentation.runOnMainSync(() -> {
            assertTrue(((android.widget.TextView)weather.findViewById(R.id.weatherSource)).getText().toString().contains("Яндекс API не подключён"));
            assertEquals("Выбрать город →", ((android.widget.TextView)weather.findViewById(R.id.weatherStatus)).getText().toString());
            assertTrue(weather.findViewById(R.id.weatherUpdate).hasFocus());
        });
        SystemClock.sleep(650); screenshot("weather-setup");
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> { assertEquals(R.id.weatherCity, weather.getCurrentFocus().getId()); weather.finish(); });
        instrumentation.waitForIdleSync();
        // Explicit cached test fixture; no real API access or fabricated live weather is claimed.
        WeatherStore.select(context, new WeatherData.City("Тестовый город", 55.75, 37.62));
        WeatherStore.credentials(context).edit().putString("yandex_key", "test-key-not-a-real-credential").putBoolean("yandex_enabled", true).apply();
        PremiumPreferences.get(context).edit().putString("weather_summary", "+20 °C · Пасмурно · ветер 2 м/с")
                .putString("weather_source", "Яндекс Погода").putLong("weather_time", System.currentTimeMillis()).apply();
        Activity cached = start(WeatherActivity.class);
        instrumentation.runOnMainSync(() -> {
            String text = ((android.widget.TextView)cached.findViewById(R.id.weatherStatus)).getText().toString();
            assertTrue(text.contains("Тестовый город"));
            assertTrue(((android.widget.TextView)cached.findViewById(R.id.weatherSource)).getText().toString().contains("Яндекс Погода"));
            assertTrue(text.contains("+20 °C"));
        });
        SystemClock.sleep(650); screenshot("weather-cached-fixture");
        instrumentation.runOnMainSync(cached::finish); instrumentation.waitForIdleSync();
        Activity home = start(MainActivity.class);
        instrumentation.runOnMainSync(() -> assertTrue(((android.widget.TextView)home.findViewById(R.id.premiumSubtitle)).getText().toString().contains("Яндекс Погода")));
        instrumentation.runOnMainSync(home::finish); instrumentation.waitForIdleSync();
        PremiumPreferences.get(context).edit().remove("latitude").remove("longitude").remove("weather_city").apply();
        WeatherStore.clearCache(context); WeatherStore.credentials(context).edit().clear().apply();
    }
}
