package hu.unideb.inf.travelweatherassistant.util;

import hu.unideb.inf.travelweatherassistant.network.WeatherResponse;

/*
 * OutfitAdvisor is the creative extension suggested by the professor.
 *
 * It behaves like a small local agent: it reads the weather facts and produces
 * a personalized clothing recommendation. This version is rule-based so the app
 * works without an API key, but the class could later be replaced by a real LLM
 * API call while keeping MainActivity almost unchanged.
 */
public class OutfitAdvisor {
    private OutfitAdvisor() {
    }

    public static String suggestOutfit(WeatherResponse.CurrentWeather current) {
        StringBuilder suggestion = new StringBuilder();
        double feelsLike = current.apparentTemperature;
        int code = current.weatherCode;

        suggestion.append(baseLayerAdvice(feelsLike));
        suggestion.append(" ");
        suggestion.append(weatherProtectionAdvice(code, current.rain));
        suggestion.append(" ");
        suggestion.append(windAdvice(current.windSpeed));
        suggestion.append(" ");
        suggestion.append(accessoryAdvice(current));

        return suggestion.toString().replaceAll("\\s+", " ").trim();
    }

    private static String baseLayerAdvice(double feelsLike) {
        if (feelsLike < 0) {
            return "Wear a winter coat, thermal layers, gloves and a warm hat.";
        }
        if (feelsLike < 8) {
            return "Choose a warm jacket or coat with a sweater underneath.";
        }
        if (feelsLike < 16) {
            return "A light jacket or hoodie should be comfortable.";
        }
        if (feelsLike < 24) {
            return "A T-shirt with an optional thin layer is enough.";
        }
        return "Wear light, breathable clothes such as a T-shirt and shorts.";
    }

    private static String weatherProtectionAdvice(int code, double rain) {
        if (code >= 95) {
            return "Because storms are possible, avoid metal umbrellas and stay near safe indoor places.";
        }
        if ((code >= 51 && code <= 82) || rain > 0) {
            return "Add a raincoat or umbrella and choose shoes that can handle wet streets.";
        }
        if (code >= 71 && code <= 77) {
            return "Use waterproof boots because snow can make sidewalks slippery.";
        }
        if (code == 45 || code == 48) {
            return "Fog can reduce visibility, so brighter outerwear is a smart choice.";
        }
        return "No special weather protection is needed right now.";
    }

    private static String windAdvice(double windSpeed) {
        if (windSpeed > 45) {
            return "The wind is strong, so avoid loose scarves and use a zipped jacket.";
        }
        if (windSpeed > 25) {
            return "It may feel breezy, so a windbreaker would help.";
        }
        return "Wind is not a major clothing concern.";
    }

    private static String accessoryAdvice(WeatherResponse.CurrentWeather current) {
        if (current.weatherCode == 0 && current.temperature > 22) {
            return "Sunglasses, sunscreen and a water bottle would make the trip nicer.";
        }
        if (current.apparentTemperature < 5) {
            return "A scarf can make the outfit much more comfortable.";
        }
        return "Keep the outfit simple and comfortable for walking.";
    }
}
