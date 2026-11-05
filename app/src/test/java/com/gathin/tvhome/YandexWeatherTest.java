package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class YandexWeatherTest {
    @Test public void parsesDocumentedFactInCelsiusAndMetresPerSecond() throws Exception {
        String text = WeatherData.yandex("{\"now\":1470220206,\"fact\":{\"temp\":20,\"feels_like\":21,\"condition\":\"overcast\",\"wind_speed\":2}}");
        assertEquals("+20 °C · Пасмурно · ветер 2 м/с", text);
    }
    @Test public void negativeAndZeroAreValidTemperatures() throws Exception {
        assertTrue(WeatherData.yandex("{\"fact\":{\"temp\":-15,\"condition\":\"snow\",\"wind_speed\":0}}").startsWith("-15 °C · Снег"));
        assertTrue(WeatherData.yandex("{\"fact\":{\"temp\":0,\"condition\":\"clear\",\"wind_speed\":0}}").startsWith("+0 °C"));
    }
    @Test(expected=Exception.class) public void apiErrorIsNeverShownAsZeroDegrees() throws Exception { WeatherData.yandex("{\"status\":\"error\",\"message\":\"forbidden\"}"); }
    @Test(expected=Exception.class) public void missingTemperatureDoesNotOverwriteCache() throws Exception { WeatherData.yandex("{\"fact\":{\"wind_speed\":2,\"condition\":\"clear\"}}"); }
    @Test(expected=Exception.class) public void nullTemperatureIsInvalid() throws Exception { WeatherData.yandex("{\"fact\":{\"temp\":null,\"wind_speed\":2,\"condition\":\"clear\"}}"); }
    @Test(expected=Exception.class) public void impossibleTemperatureIsInvalid() throws Exception { WeatherData.yandex("{\"fact\":{\"temp\":1000,\"wind_speed\":2,\"condition\":\"clear\"}}"); }
    @Test(expected=Exception.class) public void negativeWindIsInvalid() throws Exception { WeatherData.yandex("{\"fact\":{\"temp\":2,\"wind_speed\":-3,\"condition\":\"clear\"}}"); }
    @Test public void unknownConditionDoesNotShowRawServerText() { assertEquals("Без описания", WeatherData.condition("<unexpected>")); }
    @Test public void conditionVocabularyCoversRainSnowAndStorm() {
        assertEquals("Дождь со снегом", WeatherData.condition("wet-snow"));
        assertEquals("Дождь с грозой", WeatherData.condition("thunderstorm-with-rain"));
        assertEquals("Сильный дождь", WeatherData.condition("continuous-heavy-rain"));
    }
}
