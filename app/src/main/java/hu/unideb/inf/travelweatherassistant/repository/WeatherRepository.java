package hu.unideb.inf.travelweatherassistant.repository;

import hu.unideb.inf.travelweatherassistant.network.GeocodingApiService;
import hu.unideb.inf.travelweatherassistant.network.GeocodingResponse;
import hu.unideb.inf.travelweatherassistant.network.WeatherApiService;
import hu.unideb.inf.travelweatherassistant.network.WeatherResponse;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/*
 * Repository layer for weather-related network operations.
 *
 * The Activity should not need to know how Retrofit is created or which base
 * URLs are used. Keeping this code here makes MainActivity cleaner and shows
 * a simple design-pattern idea from the course.
 */
public class WeatherRepository {
    private static final String WEATHER_BASE_URL = "https://api.open-meteo.com/v1/";
    private static final String GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/";

    // These are the exact current weather fields requested from Open-Meteo.
    private static final String CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m";

    private final WeatherApiService weatherApiService;
    private final GeocodingApiService geocodingApiService;

    public WeatherRepository() {
        // Retrofit creates a Java implementation of the API interface.
        weatherApiService = new Retrofit.Builder()
                .baseUrl(WEATHER_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApiService.class);

        geocodingApiService = new Retrofit.Builder()
                .baseUrl(GEOCODING_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeocodingApiService.class);
    }

    public Call<WeatherResponse> fetchWeather(double latitude, double longitude) {
        // timezone=auto makes the API return times in the local timezone of the coordinates.
        return weatherApiService.getCurrentWeather(latitude, longitude, CURRENT_FIELDS, "auto");
    }

    public Call<GeocodingResponse> searchCity(String cityName) {
        // The geocoding API returns possible matches; MainActivity uses the first result.
        return geocodingApiService.searchCity(cityName, 5, "en", "json");
    }
}
