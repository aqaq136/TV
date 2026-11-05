package com.gathin.tvhome;

/** Explicit network smoke test; never runs as part of the offline unit suite. */
public final class LiveWeatherCheck {
    public static void main(String[] args) throws Exception {
        int cities = 0;
        for (WeatherData.City city : new WeatherData.City[]{new WeatherData.City("Москва", 55.7558, 37.6173), new WeatherData.City("Казань", 55.7961, 49.1064)}) {
            int available = 0;
            for (String provider : new String[]{"MET Norway", "Open-Meteo"}) {
                try (ToolNetwork net = new ToolNetwork()) {
                    String value = provider.equals("MET Norway") ? FreeWeather.met(net.json(FreeWeather.metUrl(city)), System.currentTimeMillis()).summary
                            : WeatherData.current(net.json(WeatherPolicy.openMeteoUrl(city)));
                    if (!value.contains("°C")) throw new IllegalStateException("No weather value");
                    System.out.println("LIVE WEATHER OK | " + city.title + " | " + provider + " | " + value); available++;
                } catch (Exception e) { System.out.println("LIVE WEATHER UNAVAILABLE | " + city.title + " | " + provider + " | " + e.getClass().getSimpleName()); }
            }
            if (available == 0) throw new IllegalStateException("Neither weather provider responded for " + city.title);
            cities++;
        }
        System.out.println("LIVE WEATHER PASSED | " + cities + " cities | no API keys");
    }
}
