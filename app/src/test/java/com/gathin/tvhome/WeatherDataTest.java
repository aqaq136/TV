package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherDataTest {
    @Test public void distinguishesCitiesWithSameName() throws Exception {
        String json = "{\"results\":[{\"name\":\"Москва\",\"admin1\":\"Москва\",\"country\":\"Россия\",\"latitude\":55.75,\"longitude\":37.62}]}";
        assertEquals("Москва, Россия", WeatherData.cities(json).get(0).title);
        assertEquals(55.75, WeatherData.cities(json).get(0).latitude, .001);
    }
    @Test public void emptySearchDoesNotInventCity() throws Exception { assertTrue(WeatherData.cities("{}").isEmpty()); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidCoordinates() { new WeatherData.City("bad", 100, 20); }
    @Test public void parsesRealisticCurrentWeatherFields() throws Exception {
        String result = WeatherData.current("{\"current\":{\"temperature_2m\":-6,\"weather_code\":73,\"wind_speed_10m\":12}}");
        assertTrue(result.contains("-6")); assertTrue(result.contains("Снег")); assertTrue(result.contains("12"));
    }
    @Test(expected=Exception.class) public void refusesPartialWeatherInsteadOfDisplayingZeroDegrees() throws Exception {
        WeatherData.current("{\"current\":{\"weather_code\":0}}");
    }
    @Test public void unknownWeatherCodesAreExplicit() { assertEquals("Без описания", WeatherData.code(123)); assertEquals("Гроза", WeatherData.code(95)); }
}
