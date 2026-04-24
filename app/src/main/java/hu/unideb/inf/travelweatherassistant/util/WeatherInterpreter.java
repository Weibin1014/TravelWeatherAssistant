package hu.unideb.inf.travelweatherassistant.util;

import hu.unideb.inf.travelweatherassistant.network.WeatherResponse;

/*
 * Utility class for converting raw weather API values into user-friendly output.
 *
 * Open-Meteo returns weather_code as a number. This class translates it into
 * readable text, an icon, and practical advice for travelers.
 */
public class WeatherInterpreter {
    private WeatherInterpreter() {
    }

    public static String describeCode(int code) {
        // Open-Meteo uses numeric weather codes; this converts them to readable text.
        if (code == 0) return "Clear sky";
        if (code == 1 || code == 2 || code == 3) return "Partly cloudy";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rainy";
        if (code >= 71 && code <= 77) return "Snowy";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Unknown weather";
    }

    public static String iconForCode(int code) {
        // The UI uses these icons as a lightweight multimedia-style weather display.
        if (code == 0) return "☀";
        if (code == 1 || code == 2 || code == 3) return "⛅";
        if (code == 45 || code == 48) return "🌫";
        if (code >= 51 && code <= 67) return "☔";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "🌧";
        if (code >= 95 && code <= 99) return "⛈";
        return "☁";
    }

    public static String travelAdvice(WeatherResponse.CurrentWeather current) {
        // Build practical travel advice from weather code, temperature and wind speed.
        StringBuilder advice = new StringBuilder();

        if (current.weatherCode >= 51 && current.weatherCode <= 82) {
            advice.append("Bring an umbrella and choose waterproof shoes. ");
        } else if (current.weatherCode >= 95) {
            advice.append("Thunderstorm risk: avoid open areas and check transport delays. ");
        } else if (current.weatherCode >= 71 && current.weatherCode <= 77) {
            advice.append("Snow is possible, so wear warm layers and allow extra travel time. ");
        } else if (current.weatherCode == 0 && current.temperature > 24) {
            advice.append("Sunny trip: carry water and use sunscreen. ");
        } else {
            advice.append("Weather looks travel-friendly. ");
        }

        if (current.apparentTemperature < 5) {
            advice.append("It feels cold, so take a coat. ");
        } else if (current.apparentTemperature > 30) {
            advice.append("It feels hot, so avoid long walks at noon. ");
        }

        if (current.windSpeed > 35) {
            advice.append("Strong wind: be careful with bikes and umbrellas.");
        }

        return advice.toString().trim();
    }
}
